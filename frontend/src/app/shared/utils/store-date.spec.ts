import { storeDayEndIso, storeDayStartIso, storeMonthStart, storeToday } from './store-date';

describe('store-date', () => {
  it('hoy es el día de Bolivia, no el del dispositivo', () => {
    // 02:30 UTC del 30-10 son las 22:30 del 29-10 en Bolivia
    expect(storeToday(new Date('2026-10-30T02:30:00Z'))).toBe('2026-10-29');
    expect(storeToday(new Date('2026-10-30T04:00:00Z'))).toBe('2026-10-30');
    expect(storeMonthStart(new Date('2026-11-01T03:59:00Z'))).toBe('2026-10-01');
  });

  it('el día de la tienda va de 04:00 UTC a 03:59:59.999 UTC del día siguiente', () => {
    expect(storeDayStartIso('2026-10-29')).toBe('2026-10-29T04:00:00.000Z');
    expect(storeDayEndIso('2026-10-29')).toBe('2026-10-30T03:59:59.999Z');
    expect(storeDayStartIso('29/10/2026')).toBeUndefined();
  });
});
