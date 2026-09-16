import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { PatientList } from './patient-list';
import { PatientApi } from '../../../core/api/patient-api';
import { Page, Patient, PatientQuery } from '../../../core/models/patient.model';

function patient(id: number, pid: string, first: string, last: string): Patient {
  return {
    id,
    pid,
    firstName: first,
    lastName: last,
    fullName: `${first} ${last}`,
    dateOfBirth: '1985-04-12',
    age: 41,
    gender: 'FEMALE',
    phoneNo: '+61412345678',
    phoneNoLocal: '0412 345 678',
    address: {
      street: '12 Wallaby Way',
      suburb: 'Sydney',
      state: 'NSW',
      stateName: 'New South Wales',
      postcode: '2000',
      formatted: '12 Wallaby Way, Sydney NSW 2000',
    },
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function pageOf(content: Patient[], overrides: Partial<Page<Patient>> = {}): Page<Patient> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  };
}

describe('PatientList', () => {
  let fixture: ComponentFixture<PatientList>;
  let listSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  /** Reaches the protected members the template drives, without weakening them to public. */
  const component = () =>
    fixture.componentInstance as unknown as {
      onPageChange(event: { pageIndex: number; pageSize: number }): void;
      onSortChange(sort: { active: string; direction: 'asc' | 'desc' | '' }): void;
      confirmDelete(p: Patient): void;
      loadError(): string | null;
    };

  const lastQuery = (): PatientQuery => listSpy.mock.calls.at(-1)![0];

  beforeEach(async () => {
    listSpy = vi.fn().mockReturnValue(of(pageOf([patient(1, 'PAT-000001', 'Jane', 'Citizen')])));
    deleteSpy = vi.fn().mockReturnValue(of(undefined));
    dialogOpen = vi.fn().mockReturnValue({ afterClosed: () => of(true) });

    await TestBed.configureTestingModule({
      imports: [PatientList],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: PatientApi, useValue: { list: listSpy, delete: deleteSpy } },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientList);
    await fixture.whenStable();
  });

  it('loads the first page on init using the default sort', () => {
    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(lastQuery()).toEqual({
      page: 0,
      size: 10,
      sort: 'lastName',
      direction: 'asc',
      search: '',
    });
  });

  it('renders a row per patient returned by the server', async () => {
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');

    expect(rows).toHaveLength(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('PAT-000001');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Jane Citizen');
  });

  it('shows the total from the server, not the number of rows on screen', async () => {
    listSpy.mockReturnValue(
      of(pageOf([patient(1, 'PAT-000001', 'Jane', 'Citizen')], { totalElements: 4212 })),
    );
    component().onPageChange({ pageIndex: 0, pageSize: 10 });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('4212 records');
  });

  it('asks the server for the new page rather than slicing locally', async () => {
    component().onPageChange({ pageIndex: 3, pageSize: 25 });
    await fixture.whenStable();

    expect(listSpy).toHaveBeenCalledTimes(2);
    expect(lastQuery().page).toBe(3);
    expect(lastQuery().size).toBe(25);
  });

  it('sends the sort to the server and returns to the first page', async () => {
    component().onPageChange({ pageIndex: 4, pageSize: 10 });
    await fixture.whenStable();

    component().onSortChange({ active: 'dateOfBirth', direction: 'desc' });
    await fixture.whenStable();

    expect(lastQuery().sort).toBe('dateOfBirth');
    expect(lastQuery().direction).toBe('desc');
    expect(lastQuery().page).toBe(0);
  });

  it('falls back to the default sort when the user clears it', async () => {
    component().onSortChange({ active: 'dateOfBirth', direction: '' });
    await fixture.whenStable();

    expect(lastQuery().sort).toBe('lastName');
    expect(lastQuery().direction).toBe('asc');
  });

  it('debounces typing so one request is sent per pause, not per keystroke', async () => {
    vi.useFakeTimers();
    try {
      const search = (fixture.nativeElement as HTMLElement).querySelector(
        'input[type="search"]',
      ) as HTMLInputElement;

      for (const value of ['j', 'ja', 'jan', 'jane']) {
        search.value = value;
        search.dispatchEvent(new Event('input'));
      }

      expect(listSpy).toHaveBeenCalledTimes(1); // still only the initial load
      vi.advanceTimersByTime(300);
    } finally {
      vi.useRealTimers();
    }

    await fixture.whenStable();

    expect(listSpy).toHaveBeenCalledTimes(2);
    expect(lastQuery().search).toBe('jane');
    expect(lastQuery().page).toBe(0);
  });

  it('confirms before deleting, and reloads the page afterwards', async () => {
    component().confirmDelete(patient(1, 'PAT-000001', 'Jane', 'Citizen'));
    await fixture.whenStable();

    expect(dialogOpen).toHaveBeenCalledTimes(1);
    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(listSpy).toHaveBeenCalledTimes(2);
  });

  it('does not delete when the confirmation is dismissed', async () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(false) });

    component().confirmDelete(patient(1, 'PAT-000001', 'Jane', 'Citizen'));
    await fixture.whenStable();

    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('shows an empty state instead of a blank grid when nothing matches', async () => {
    listSpy.mockReturnValue(of(pageOf([])));
    component().onPageChange({ pageIndex: 0, pageSize: 10 });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'There are no patients yet',
    );
  });
});
