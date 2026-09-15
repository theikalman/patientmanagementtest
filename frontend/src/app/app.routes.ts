import { Routes } from '@angular/router';

/**
 * Routes are lazy loaded with `loadComponent`, so the initial bundle only
 * contains the grid. The edit form, the datepicker and the dialog are fetched
 * when the user first opens them.
 */
export const routes: Routes = [
  {
    path: 'patients',
    title: 'Patients',
    loadComponent: () =>
      import('./features/patients/patient-list/patient-list').then((m) => m.PatientList),
  },
  {
    path: 'patients/new',
    title: 'New patient',
    loadComponent: () =>
      import('./features/patients/patient-form/patient-form').then((m) => m.PatientForm),
  },
  {
    path: 'patients/:id/edit',
    title: 'Edit patient',
    loadComponent: () =>
      import('./features/patients/patient-form/patient-form').then((m) => m.PatientForm),
  },
  { path: '', pathMatch: 'full', redirectTo: 'patients' },
  { path: '**', redirectTo: 'patients' },
];
