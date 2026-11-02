import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners, provideEnvironmentInitializer } from '@angular/core';
import { DATE_PIPE_DEFAULT_OPTIONS } from '@angular/common';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { STORE_LOCALE, STORE_TIMEZONE } from './shared/utils/money';
import { installNumberInputComma } from './shared/utils/number-input-comma';
import { installNumberInputGuard } from './shared/utils/number-input-guard';

// Amounts read "Bs 1.234,50" (the locale is registered in money.ts) and dates are store days (America/La_Paz), like
// the server

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideEnvironmentInitializer(installNumberInputComma),
    provideEnvironmentInitializer(installNumberInputGuard),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: STORE_LOCALE },
    { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { timezone: STORE_TIMEZONE } }
  ]
};
