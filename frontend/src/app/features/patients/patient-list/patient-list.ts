import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { PatientApi } from '../../../core/api/patient-api';
import { ApiError } from '../../../core/models/api-error.model';
import { Page, Patient, PatientQuery } from '../../../core/models/patient.model';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';

/**
 * The patient grid.
 *
 * Everything that narrows the result set - the page, the page size, the sort and
 * the search term - is sent to the server and applied there. The component never
 * holds more than one page of rows, so the grid behaves identically with 40
 * patients and with 4,000,000.
 *
 * State lives in signals and the table renders from a plain array rather than a
 * `MatTableDataSource`: the data source exists to do client side paging and
 * filtering, which is exactly what we do not want here.
 */
@Component({
  selector: 'app-patient-list',
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './patient-list.html',
  styleUrl: './patient-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientList {
  private readonly api = inject(PatientApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly paginator = viewChild(MatPaginator);
  private readonly sort = viewChild(MatSort);

  protected readonly displayedColumns = [
    'pid',
    'fullName',
    'dateOfBirth',
    'gender',
    'phoneNo',
    'address',
    'actions',
  ] as const;

  /** Page sizes offered to the user; the server caps anything above 100. */
  protected readonly pageSizeOptions = [5, 10, 25, 50, 100];

  protected readonly searchControl = new FormControl('', { nonNullable: true });

  protected readonly page = signal<Page<Patient> | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);

  private readonly query = signal<PatientQuery>({
    page: 0,
    size: 10,
    sort: 'lastName',
    direction: 'asc',
    search: '',
  });

  constructor() {
    // Typing in the search box must not fire a request per keystroke. Debouncing
    // and de-duplicating here keeps the server load proportional to intent
    // rather than to typing speed.
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((search) => {
        // A new search invalidates the current page number: page 4 of the old
        // result set is meaningless in the new one.
        this.query.update((q) => ({ ...q, search, page: 0 }));
        this.paginator()?.firstPage();
      });

    // One place where a query change turns into a request. Every interaction
    // (search, page, sort, reload) funnels through the query signal, so they
    // cannot get out of step with each other.
    effect(() => this.fetch(this.query()));
  }

  private fetch(query: PatientQuery): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.api
      .list(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.loadError.set(error.message);
          this.page.set(null);
          this.loading.set(false);
        },
      });
  }

  protected onPageChange(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  protected onSortChange(sort: Sort): void {
    // Clearing the sort in Material yields an empty direction; fall back to the
    // server's default ordering rather than sending an invalid parameter.
    this.query.update((q) => ({
      ...q,
      sort: sort.direction ? sort.active : 'lastName',
      direction: sort.direction || 'asc',
      page: 0,
    }));
  }

  protected reload(): void {
    // Replacing the object re-runs the effect even though the values are equal.
    this.query.update((q) => ({ ...q }));
  }

  protected clearSearch(): void {
    this.searchControl.setValue('');
  }

  protected edit(patient: Patient): void {
    this.router.navigate(['/patients', patient.id, 'edit']);
  }

  protected confirmDelete(patient: Patient): void {
    const data: ConfirmDialogData = {
      title: 'Delete patient',
      message: `Delete ${patient.fullName} (${patient.pid})? This cannot be undone.`,
      confirmLabel: 'Delete',
      destructive: true,
    };

    this.dialog
      .open(ConfirmDialog, { data, width: '420px', autoFocus: 'dialog' })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.delete(patient);
        }
      });
  }

  private delete(patient: Patient): void {
    this.api
      .delete(patient.id)
      .pipe(
        // Reload straight after the delete so the grid shows the true server
        // state, including a row promoted from the next page into this one.
        switchMap(() => this.api.list(this.query())),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.snackBar.open(`${patient.fullName} was deleted`, 'Dismiss', { duration: 4000 });
          this.correctPageOverflowIfNeeded(page);
        },
        error: (error: ApiError) => {
          this.snackBar.open(error.message, 'Dismiss', { duration: 6000 });
          this.reload();
        },
      });
  }

  /** Deleting the last row of the last page would otherwise leave an empty grid. */
  private correctPageOverflowIfNeeded(page: Page<Patient>): void {
    if (page.content.length === 0 && page.page > 0) {
      this.query.update((q) => ({ ...q, page: q.page - 1 }));
    }
  }

  protected trackById(_index: number, patient: Patient): number {
    return patient.id;
  }
}
