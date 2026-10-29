import { formatNumber, registerLocaleData } from '@angular/common';
import localeEsBo from '@angular/common/locales/es-BO';

// The store's number format (es-BO): "1.234,50". Templates get it from the number pipe through LOCALE_ID; code
// that builds a message uses these helpers so both read the same.
export const STORE_LOCALE = 'es-BO';

// Bolivia has no daylight saving time, so the store day (America/La_Paz) is always UTC-4
export const STORE_TIMEZONE = '-0400';

// Registered here too, so formatBs works wherever it is imported (also in unit tests)
registerLocaleData(localeEsBo, STORE_LOCALE);

export function formatBs(amount: number): string {
  return `Bs ${formatNumber(amount ?? 0, STORE_LOCALE, '1.2-2')}`;
}
