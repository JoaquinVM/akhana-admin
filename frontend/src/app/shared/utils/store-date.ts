import { formatDate } from '@angular/common';
import { STORE_TIMEZONE } from './money';

// Store days (America/La_Paz, UTC-4 all year), whatever the time zone of the device: the server checks dates and
// day ranges in the store's zone, so the screens do too

/** Today in the store, yyyy-MM-dd. */
export function storeToday(now: Date = new Date()): string {
  return formatDate(now, 'yyyy-MM-dd', 'en-US', STORE_TIMEZONE);
}

/** First day of the store's current month, yyyy-MM-01. */
export function storeMonthStart(now: Date = new Date()): string {
  return storeToday(now).slice(0, 8) + '01';
}

/** dd/MM of a store day sent as yyyy-MM-dd. */
function dayMonth(day: string): string {
  return `${day.slice(8, 10)}/${day.slice(5, 7)}`;
}

/**
 * CAJ-11: «Día 31/10 · cargada el 01/11 00:20» for a sale, movement or payment whose day of operation differs from
 * the store day it was recorded; null when they match (or there is no day).
 */
export function operationDayLabel(operationDate: string | null | undefined, recordedAt: string | null | undefined): string | null {
  if (!operationDate || !recordedAt || storeToday(new Date(recordedAt)) === operationDate) {
    return null;
  }
  return `Día ${dayMonth(operationDate)} · cargada el ${formatDate(recordedAt, 'dd/MM HH:mm', 'en-US', STORE_TIMEZONE)}`;
}

/** CAJ-11: «Día 28/10 · abierta el 29/10» for a cash session whose day differs from the day it was opened; else null. */
export function sessionDayLabel(operationDate: string | null | undefined, openedAt: string | null | undefined): string | null {
  if (!operationDate || !openedAt) {
    return null;
  }
  const openedDay = storeToday(new Date(openedAt));
  return openedDay === operationDate ? null : `Día ${dayMonth(operationDate)} · abierta el ${dayMonth(openedDay)}`;
}
