import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { AuthStateService } from '../services/auth-state.service';

/**
 * Detects when the local API rejects us for lack of a valid session cookie (HTTP 401) and surfaces the
 * auth gate. The session cookie is HttpOnly and same-origin, so the browser attaches it automatically —
 * there is nothing to add to outgoing requests here; we only react to the rejection.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authState = inject(AuthStateService);
  return next(req).pipe(
    tap({
      error: (err) => {
        if (err instanceof HttpErrorResponse && err.status === 401) {
          authState.markUnauthenticated();
        }
      },
    }),
  );
};
