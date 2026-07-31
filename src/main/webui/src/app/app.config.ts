import { ApplicationConfig, ErrorHandler, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { httpErrorInterceptor } from './interceptors/http-error.interceptor';
import { ClientDiagnosticsService, DiagnosticsErrorHandler } from './services/client-diagnostics.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor, httpErrorInterceptor])),
    { provide: ErrorHandler, useClass: DiagnosticsErrorHandler },
    // Installed before the app renders, so an error during startup is captured too.
    provideAppInitializer(() => inject(ClientDiagnosticsService).install()),
  ]
};
