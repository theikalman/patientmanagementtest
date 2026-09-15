import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { PatientApi, ReferenceDataApi } from '../../../core/api/patient-api';
import { ApiError } from '../../../core/models/api-error.model';
import {
  AustralianState,
  CreatePatientPayload,
  Gender,
  Patient,
  ReferenceOption,
  UpdatePatientPayload,
} from '../../../core/models/patient.model';
import { parseIsoDate, toIsoDate } from '../../../core/util/dates';

/**
 * Create and edit form.
 *
 * One component serves both routes: they render the same fields and differ only
 * in whether an existing patient is loaded first and whether the save is a POST
 * or a PUT. Splitting them would duplicate the entire form for no gain.
 *
 * Client side validation mirrors the server's rules so that obvious mistakes are
 * caught without a round trip, but it is treated as a convenience, not as the
 * guarantee: whatever the server rejects is mapped back onto the offending
 * control, so a rule that only exists on the server (the postcode/state ranges,
 * for instance) still shows up on the right field.
 */
@Component({
  selector: 'app-patient-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './patient-form.html',
  styleUrl: './patient-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientForm {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PatientApi);
  private readonly referenceData = inject(ReferenceDataApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  /** Bound from the `:id` route parameter by withComponentInputBinding(). */
  readonly id = input<string | undefined>();

  protected readonly patient = signal<Patient | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);

  protected readonly genders = signal<ReferenceOption[]>([]);
  protected readonly states = signal<ReferenceOption[]>([]);

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly heading = computed(() =>
    this.isEdit() ? `Edit ${this.patient()?.fullName ?? 'patient'}` : 'New patient',
  );

  /** Nobody alive was born before this, so it bounds the datepicker sensibly. */
  protected readonly minDate = new Date(new Date().getFullYear() - 130, 0, 1);
  protected readonly maxDate = new Date();

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    dateOfBirth: this.fb.control<Date | null>(null, Validators.required),
    gender: this.fb.control<Gender | null>(null, Validators.required),
    // The server owns the canonical phone format; here we only require something
    // that is plausibly an Australian number, and let the server have the final say.
    phoneNo: ['', [Validators.required, Validators.pattern(/^[\d\s()+\-.]{8,20}$/)]],
    address: this.fb.group({
      street: ['', [Validators.required, Validators.maxLength(200)]],
      suburb: ['', [Validators.required, Validators.maxLength(100)]],
      state: this.fb.control<AustralianState | null>(null, Validators.required),
      postcode: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    }),
  });

  /** Field paths currently carrying an error that came from the server. */
  private readonly serverRejectedPaths = new Set<string>();

  constructor() {
    this.loadReferenceData();

    // A server side rejection describes one specific payload. As soon as the user
    // changes anything, that verdict is stale and must be cleared - otherwise
    // fixing the *state* leaves the rejection sitting on the *postcode*, which
    // reads as if the fix did not work.
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.clearServerErrors());

    // The route parameter arrives as an input, so read it once the component is
    // constructed rather than subscribing to ActivatedRoute.
    queueMicrotask(() => {
      const id = this.id();
      if (id !== undefined) {
        this.loadPatient(Number(id));
      }
    });
  }

  private loadReferenceData(): void {
    forkJoin({
      genders: this.referenceData.genders(),
      states: this.referenceData.states(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ genders, states }) => {
          this.genders.set(genders);
          this.states.set(states);
        },
        error: (error: ApiError) => this.loadError.set(error.message),
      });
  }

  private loadPatient(id: number): void {
    if (Number.isNaN(id)) {
      this.loadError.set('That patient id is not valid.');
      return;
    }

    this.loading.set(true);
    this.api
      .getById(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (patient) => {
          this.patient.set(patient);
          this.form.patchValue({
            firstName: patient.firstName,
            lastName: patient.lastName,
            dateOfBirth: parseIsoDate(patient.dateOfBirth),
            gender: patient.gender,
            // Show the readable local format; the server re-normalises on save.
            phoneNo: patient.phoneNoLocal,
            address: {
              street: patient.address.street,
              suburb: patient.address.suburb,
              state: patient.address.state,
              postcode: patient.address.postcode,
            },
          });
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.loadError.set(error.message);
          this.loading.set(false);
        },
      });
  }

  protected save(): void {
    this.formError.set(null);

    if (this.form.invalid) {
      // Touch everything so that the fields the user never visited also show
      // their error, instead of the form silently refusing to submit.
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.toPayload();
    this.saving.set(true);

    const existing = this.patient();
    const request = existing
      ? this.api.update(existing.id, { ...payload, version: existing.version } as UpdatePatientPayload)
      : this.api.create(payload);

    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.snackBar.open(
          existing ? `${saved.fullName} was updated` : `${saved.fullName} was created as ${saved.pid}`,
          'Dismiss',
          { duration: 4000 },
        );
        this.router.navigate(['/patients']);
      },
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleSaveError(error);
      },
    });
  }

  private handleSaveError(error: ApiError): void {
    if (error.isValidation) {
      this.applyServerFieldErrors(error);
      this.formError.set('Some fields were rejected by the server. See the messages below.');
      return;
    }

    // The server's 409 detail already names both versions and what to do next,
    // so repeating it here would only make the banner longer, not clearer.
    if (error.isConflict) {
      this.formError.set(error.message);
      return;
    }

    this.formError.set(error.message);
  }

  /**
   * Projects the server's per field violations onto the matching form controls.
   *
   * This is what makes a server-only rule (such as the postcode/state ranges)
   * behave like any other field error in the UI, rather than a banner the user
   * has to translate into "which box do I fix?".
   */
  private applyServerFieldErrors(error: ApiError): void {
    for (const violation of error.fieldErrors) {
      const control: AbstractControl | null = this.form.get(violation.field);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), server: violation.message });
        control.markAsTouched();
        this.serverRejectedPaths.add(violation.field);
      }
    }
  }

  /**
   * Strips the `server` key from every control that has one, leaving any client
   * side errors on that control intact.
   */
  private clearServerErrors(): void {
    if (this.serverRejectedPaths.size === 0) {
      return;
    }

    for (const path of this.serverRejectedPaths) {
      const control = this.form.get(path);
      if (!control?.errors?.['server']) {
        continue;
      }
      const { server: _removed, ...remaining } = control.errors;
      // setErrors(null) is what re-runs the control's own validators.
      control.setErrors(Object.keys(remaining).length > 0 ? remaining : null);
    }

    this.serverRejectedPaths.clear();
    this.formError.set(null);
  }

  private toPayload(): CreatePatientPayload {
    const value = this.form.getRawValue();
    return {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      dateOfBirth: toIsoDate(value.dateOfBirth),
      gender: value.gender,
      phoneNo: value.phoneNo.trim(),
      address: {
        street: (value.address.street ?? '').trim(),
        suburb: (value.address.suburb ?? '').trim(),
        state: value.address.state ?? null,
        postcode: (value.address.postcode ?? '').trim(),
      },
    };
  }

  protected cancel(): void {
    this.router.navigate(['/patients']);
  }

  /**
   * Single source of the message shown under a field, so the template does not
   * grow a ladder of @if branches per control.
   */
  protected errorFor(path: string, label: string): string | null {
    const control = this.form.get(path);
    if (!control || !control.touched || !control.errors) {
      return null;
    }
    const errors = control.errors;
    if (errors['server']) {
      return String(errors['server']);
    }
    if (errors['required']) {
      return `${label} is required`;
    }
    if (errors['maxlength']) {
      return `${label} must be at most ${errors['maxlength'].requiredLength} characters`;
    }
    if (errors['pattern']) {
      return path === 'address.postcode'
        ? 'Postcode must be exactly 4 digits'
        : `${label} is not in a valid format`;
    }
    if (errors['matDatepickerMax']) {
      return 'Date of birth must be in the past';
    }
    if (errors['matDatepickerMin']) {
      return 'Date of birth is unrealistically far in the past';
    }
    if (errors['matDatepickerParse']) {
      return 'Enter the date as dd/mm/yyyy';
    }
    return `${label} is not valid`;
  }
}
