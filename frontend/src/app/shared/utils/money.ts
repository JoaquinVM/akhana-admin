import { formatNumber, registerLocaleData } from '@angular/common';
import localeEsBo from '@angular/common/locales/es-BO';

// The store's number format (es-BO): "1.234,50". Templates get it from the number pipe through LOCALE_ID; code
// that builds a message uses these helpers so both read the same.
export const STORE_LOCALE = 'es-BO';

// Bolivia has no daylight saving time, so the store day (America/La_Paz) is always UTC-4
export const STORE_TIMEZONE = '-0400';

// Registered here too, so formatBs works wherever it is imported (also in unit tests)
registerLocaleData(localeEsBo, STORE_LOCALE);

// POS-04 (097): the smallest coin; cash is charged in multiples of it
export const SMALLEST_COIN = 0.1;

// To the nearest Bs 0.10 (15.04 -> 15.00, 15.05 -> 15.10), like the server; a positive amount never becomes 0.
// Works in cents so 15.05 is not read as 15.0499...
export function roundToCoin(amount: number): number {
  const cents = Math.round((amount || 0) * 100);
  const rounded = Math.round(cents / 10) * 10;
  return rounded === 0 && cents > 0 ? SMALLEST_COIN : rounded / 100;
}

// True when the amount is a whole number of Bs 0.10 coins
export function isWholeCoins(amount: number): boolean {
  return Math.round((amount || 0) * 100) % 10 === 0;
}

export function formatBs(amount: number): string {
  return `Bs ${formatNumber(amount ?? 0, STORE_LOCALE, '1.2-2')}`;
}
