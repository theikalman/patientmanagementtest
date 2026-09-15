/**
 * RFC 9457 problem document, as produced by the server's GlobalExceptionHandler.
 */
export interface FieldViolation {
  /** Dotted path of the rejected field, for example "address.postcode". */
  readonly field: string;
  readonly message: string;
  readonly rejectedValue?: unknown;
}

export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly timestamp?: string;
  /** Present on validation failures only. */
  readonly errors?: readonly FieldViolation[];
  /** Present on 409 concurrent modification only. */
  readonly expectedVersion?: number;
  readonly actualVersion?: number;
}

/**
 * The single error shape the rest of the application deals with.
 *
 * The HTTP interceptor converts every failure - a problem document, an
 * unexpected HTML error page, or the network being down - into one of these, so
 * components never have to inspect an HttpErrorResponse.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    override readonly message: string,
    readonly fieldErrors: readonly FieldViolation[] = [],
    readonly problem?: ProblemDetail,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** True when the record was changed by somebody else while it was being edited. */
  get isConflict(): boolean {
    return this.status === 409;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isValidation(): boolean {
    return this.status === 400 && this.fieldErrors.length > 0;
  }
}
