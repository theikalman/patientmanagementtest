import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { DateAdapter, provideNativeDateAdapter } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { MAT_DATE_LOCALE } from '@angular/material/core';

import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuDateAdapter } from './core/util/au-date-adapter';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // Zoneless change detection: the application drives updates through signals
    // rather than zone.js monkey patching, which is the Angular 21 default and
    // means fewer, more predictable change detection passes.
    provideZonelessChangeDetection(),

    provideRouter(
      routes,
      // Route params and query params arrive as component @Input()s, so the list
      // component can keep its state in the URL without touching ActivatedRoute.
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),

    // One interceptor turns every HTTP failure into an ApiError.
    provideHttpClient(withInterceptors([errorInterceptor])),

    // No animations provider: Angular Material 21 animates with CSS, so
    // @angular/animations is not a dependency of this application at all.

    // The datepicker needs a date adapter. en-AU makes it *display* dd/mm/yyyy,
    // but the native adapter still *parses* typed input as US m/d/y, so
    // AuDateAdapter overrides parsing to be day-first. See au-date-adapter.ts.
    provideNativeDateAdapter(),
    { provide: DateAdapter, useClass: AuDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-AU' },

    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
  ],
};
