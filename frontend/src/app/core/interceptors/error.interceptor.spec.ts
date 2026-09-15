import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { errorInterceptor } from './error.interceptor';
import { ApiError } from '../models/api-error.model';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  /** Runs a request that is guaranteed to fail and hands back the ApiError. */
  function failWith(body: object | string, status: number, statusText = 'Error'): Promise<ApiError> {
    return new Promise((resolve) => {
      http.get('/api/v1/patients/1').subscribe({ error: (e: ApiError) => resolve(e) });
      backend.expectOne('/api/v1/patients/1').flush(body, { status, statusText });
    });
  }

  it('lifts the detail out of a problem document', async () => {
    const error = await failWith(
      { title: 'Patient not found', detail: 'No patient exists with id 1', status: 404 },
      404,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.message).toBe('No patient exists with id 1');
    expect(error.isNotFound).toBe(true);
  });

  it('exposes per field violations from a validation failure', async () => {
    const error = await failWith(
      {
        title: 'Validation failed',
        detail: 'The request contains 1 invalid field(s).',
        status: 400,
        errors: [
          { field: 'address.postcode', message: 'postcode 2000 is not allocated to VIC', rejectedValue: '2000' },
        ],
      },
      400,
    );

    expect(error.isValidation).toBe(true);
    expect(error.fieldErrors).toHaveLength(1);
    expect(error.fieldErrors[0].field).toBe('address.postcode');
  });

  it('keeps the version numbers from a conflict so the form can explain itself', async () => {
    const error = await failWith(
      { title: 'Concurrent modification', detail: 'Changed by someone else', status: 409, expectedVersion: 0, actualVersion: 2 },
      409,
    );

    expect(error.isConflict).toBe(true);
    expect(error.problem?.expectedVersion).toBe(0);
    expect(error.problem?.actualVersion).toBe(2);
  });

  it('falls back to a readable message when the body is not a problem document', async () => {
    const error = await failWith('<html>502 Bad Gateway</html>', 502);

    expect(error.message).toContain('502');
    expect(error.fieldErrors).toHaveLength(0);
  });

  it('turns an unreachable server into an actionable message rather than "status 0"', async () => {
    const error = await new Promise<ApiError>((resolve) => {
      http.get('/api/v1/patients').subscribe({ error: (e: ApiError) => resolve(e) });
      backend.expectOne('/api/v1/patients').error(new ProgressEvent('error'));
    });

    expect(error.status).toBe(0);
    expect(error.message).toContain('Cannot reach the server');
  });

  it('never rewrites a successful response', async () => {
    const body = await new Promise((resolve) => {
      http.get('/api/v1/patients/1').subscribe(resolve);
      backend.expectOne('/api/v1/patients/1').flush({ id: 1 });
    });

    expect(body).toEqual({ id: 1 });
  });
});
