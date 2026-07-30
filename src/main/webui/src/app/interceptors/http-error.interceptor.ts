import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { ClientDiagnosticsService } from '../services/client-diagnostics.service';

/**
 * Records every rejected API call as the browser saw it, for attaching to a bug report. Paired with
 * the server's access log this is what separates a request the backend refused from one that never
 * arrived in the form the UI intended.
 *
 * Only the request line and the response are kept. The request body is deliberately left out: a
 * settings save carries credentials, and a bug report is a public document.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const diagnostics = inject(ClientDiagnosticsService);
  return next(req).pipe(
    tap({
      error: (err) => {
        if (err instanceof HttpErrorResponse) {
          diagnostics.recordFailure({
            timestamp: Date.now(),
            method: req.method,
            url: req.urlWithParams,
            status: err.status,
            statusText: err.statusText,
            responseSnippet: describeBody(err.error),
          });
        }
      },
    }),
  );
};

function describeBody(body: unknown): string | undefined {
  if (body == null) return undefined;
  if (typeof body === 'string') return body;
  try {
    return JSON.stringify(body);
  } catch {
    return String(body);
  }
}
