import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PatientApi, ReferenceDataApi } from './patient-api';
import { CreatePatientPayload, Page, Patient, PatientQuery } from '../models/patient.model';

/**
 * These tests pin the HTTP contract: method, URL and query parameters. They are
 * the client side counterpart of the backend's controller tests, and they are
 * what would fail loudly if someone "tidied up" a parameter name.
 */
describe('PatientApi', () => {
  let api: PatientApi;
  let http: HttpTestingController;

  const query: PatientQuery = { page: 0, size: 10, sort: 'lastName', direction: 'asc', search: '' };

  const emptyPage: Page<Patient> = {
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PatientApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests a page with paging and sorting parameters', () => {
    api.list(query).subscribe();

    const req = http.expectOne(
      (r) => r.url === '/api/v1/patients' && r.params.get('page') === '0',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('lastName,asc');
    req.flush(emptyPage);
  });

  it('omits the search parameter entirely when no term is supplied', () => {
    api.list(query).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/patients');
    expect(req.request.params.has('search')).toBe(false);
    req.flush(emptyPage);
  });

  it('omits the search parameter when the term is only whitespace', () => {
    api.list({ ...query, search: '   ' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/patients');
    expect(req.request.params.has('search')).toBe(false);
    req.flush(emptyPage);
  });

  it('sends a trimmed search term when one is supplied', () => {
    api.list({ ...query, search: '  jane  ' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/patients');
    expect(req.request.params.get('search')).toBe('jane');
    req.flush(emptyPage);
  });

  it('sends the requested sort direction', () => {
    api.list({ ...query, sort: 'dateOfBirth', direction: 'desc' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/patients');
    expect(req.request.params.get('sort')).toBe('dateOfBirth,desc');
    req.flush(emptyPage);
  });

  it('fetches a single patient by id', () => {
    api.getById(7).subscribe();

    const req = http.expectOne('/api/v1/patients/7');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('POSTs a create payload without a pid, because the server allocates it', () => {
    const payload: CreatePatientPayload = {
      firstName: 'Jane',
      lastName: 'Citizen',
      dateOfBirth: '1985-04-12',
      gender: 'FEMALE',
      phoneNo: '0412 345 678',
      address: { street: '12 Wallaby Way', suburb: 'Sydney', state: 'NSW', postcode: '2000' },
    };

    api.create(payload).subscribe();

    const req = http.expectOne('/api/v1/patients');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    expect(req.request.body).not.toHaveProperty('pid');
    req.flush({});
  });

  it('PUTs an update payload including the version being edited', () => {
    api
      .update(7, {
        firstName: 'Jane',
        lastName: 'Doe',
        dateOfBirth: '1985-04-12',
        gender: 'FEMALE',
        phoneNo: '0412 345 678',
        address: { street: '1 Collins St', suburb: 'Melbourne', state: 'VIC', postcode: '3000' },
        version: 3,
      })
      .subscribe();

    const req = http.expectOne('/api/v1/patients/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(3);
    req.flush({});
  });

  it('DELETEs by id', () => {
    api.delete(7).subscribe();

    const req = http.expectOne('/api/v1/patients/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});

describe('ReferenceDataApi', () => {
  let api: ReferenceDataApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReferenceDataApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches the state list from the server rather than hard coding it', () => {
    api.states().subscribe();
    http.expectOne('/api/v1/reference-data/states').flush([]);
  });

  it('fetches the gender list from the server', () => {
    api.genders().subscribe();
    http.expectOne('/api/v1/reference-data/genders').flush([]);
  });
});
