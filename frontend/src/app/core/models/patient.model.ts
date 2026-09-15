/**
 * Wire types for the Patient API.
 *
 * These mirror the server's DTOs one for one and are the only place the HTTP
 * contract is described. Components depend on these types rather than on `any`,
 * so a server side rename becomes a compile error here instead of an undefined
 * at runtime in a template.
 */

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';

export type AustralianState = 'ACT' | 'NSW' | 'NT' | 'QLD' | 'SA' | 'TAS' | 'VIC' | 'WA';

export interface AddressResponse {
  readonly street: string;
  readonly suburb: string;
  readonly state: AustralianState;
  readonly stateName: string;
  readonly postcode: string;
  /** The whole address on one line, formatted by the server. */
  readonly formatted: string;
}

export interface Patient {
  readonly id: number;
  readonly pid: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly fullName: string;
  /** ISO-8601 date, for example "1985-04-12". */
  readonly dateOfBirth: string;
  readonly age: number;
  readonly gender: Gender;
  /** Canonical E.164 form, for example "+61412345678". */
  readonly phoneNo: string;
  /** Local display form, for example "0412 345 678". */
  readonly phoneNoLocal: string;
  readonly address: AddressResponse;
  /**
   * Optimistic locking token. Must be echoed back on update; if the record has
   * changed in the meantime the server answers 409 instead of overwriting.
   */
  readonly version: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AddressPayload {
  street: string;
  suburb: string;
  state: AustralianState | null;
  postcode: string;
}

/** POST body. There is no `pid`: the server allocates it. */
export interface CreatePatientPayload {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: Gender | null;
  phoneNo: string;
  address: AddressPayload;
}

/** PUT body: the create fields plus the version being edited. */
export interface UpdatePatientPayload extends CreatePatientPayload {
  version: number;
}

/** Server side pagination envelope. */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

/** One entry of a reference data dropdown, served by the API so the two sides cannot drift. */
export interface ReferenceOption {
  readonly value: string;
  readonly label: string;
}

/** Query parameters for the patient grid. */
export interface PatientQuery {
  readonly page: number;
  readonly size: number;
  readonly sort: string;
  readonly direction: 'asc' | 'desc';
  readonly search: string;
}
