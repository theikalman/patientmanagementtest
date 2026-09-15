import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatePatientPayload,
  Page,
  Patient,
  PatientQuery,
  ReferenceOption,
  UpdatePatientPayload,
} from '../models/patient.model';

/**
 * The only place in the application that knows the API's URLs.
 *
 * Requests go to the relative path `/api/v1/...`. In development the Angular dev
 * server proxies that to http://localhost:8080 (see proxy.conf.json), and in
 * production both applications are served from the same origin. Neither case
 * needs a hard coded host, which is what keeps the built bundle
 * environment agnostic.
 */
@Injectable({ providedIn: 'root' })
export class PatientApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/patients';

  /**
   * One page of patients.
   *
   * Paging, sorting and filtering are all query parameters handled by the
   * server: the client never downloads the full table and slices it locally.
   */
  list(query: PatientQuery): Observable<Page<Patient>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sort},${query.direction}`);

    const search = query.search.trim();
    if (search.length > 0) {
      params = params.set('search', search);
    }

    return this.http.get<Page<Patient>>(this.baseUrl, { params });
  }

  getById(id: number): Observable<Patient> {
    return this.http.get<Patient>(`${this.baseUrl}/${id}`);
  }

  create(payload: CreatePatientPayload): Observable<Patient> {
    return this.http.post<Patient>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdatePatientPayload): Observable<Patient> {
    return this.http.put<Patient>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}

/**
 * Dropdown values, fetched from the server rather than duplicated in the client.
 *
 * If a state or a gender is ever added, the UI picks it up without a redeploy.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceDataApi {
  private readonly http = inject(HttpClient);

  states(): Observable<ReferenceOption[]> {
    return this.http.get<ReferenceOption[]>('/api/v1/reference-data/states');
  }

  genders(): Observable<ReferenceOption[]> {
    return this.http.get<ReferenceOption[]>('/api/v1/reference-data/genders');
  }
}
