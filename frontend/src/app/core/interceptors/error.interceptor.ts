import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError, ProblemDetail } from '../models/api-error.model';

/**
 * Normalises every HTTP failure into an {@link ApiError}.
 *
 * Doing this once, here, is what lets components write
 * `error: (e: ApiError) => ...` and get a message that is always safe to show a
 * user - whether the server sent a problem document, an unexpected HTML error
 * page, or nothing at all because the network is down.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((response: HttpErrorResponse) => throwError(() => toApiError(response))),
  );

function toApiError(response: HttpErrorResponse): ApiError {
  // status 0 means the request never reached the server.
  if (response.status === 0) {
    return new ApiError(
      0,
      'Cannot reach the server. Check that the API is running on http://localhost:8080.',
    );
  }

  const problem = asProblemDetail(response.error);
  if (problem) {
    return new ApiError(
      response.status,
      problem.detail ?? problem.title ?? defaultMessage(response.status),
      problem.errors ?? [],
      problem,
    );
  }

  return new ApiError(response.status, defaultMessage(response.status));
}

function asProblemDetail(body: unknown): ProblemDetail | null {
  if (body && typeof body === 'object' && ('detail' in body || 'title' in body)) {
    return body as ProblemDetail;
  }
  return null;
}

function defaultMessage(status: number): string {
  switch (status) {
    case 400:
      return 'The request was rejected. Please check the values you entered.';
    case 404:
      return 'The record could not be found. It may have been deleted by someone else.';
    case 409:
      return 'This record was changed by someone else. Reload and try again.';
    case 500:
      return 'The server ran into a problem. Please try again shortly.';
    default:
      return `Request failed with status ${status}.`;
  }
}
