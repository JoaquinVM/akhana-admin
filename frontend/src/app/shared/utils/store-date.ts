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

/** The instant a store day starts (00:00 in Bolivia), as an ISO string; undefined for an invalid day. */
export function storeDayStartIso(day: string): string | undefined {
  return toIso(`${day}T00:00:00.000-04:00`);
}

/** The last millisecond of a store day (23:59:59.999 in Bolivia), as an ISO string. */
export function storeDayEndIso(day: string): string | undefined {
  return toIso(`${day}T23:59:59.999-04:00`);
}

function toIso(value: string): string | undefined {
  if (!/^\d{4}-\d{2}-\d{2}T/.test(value)) {
    return undefined;
  }
  const date = new Date(value);
  return isNaN(date.getTime()) ? undefined : date.toISOString();
}
