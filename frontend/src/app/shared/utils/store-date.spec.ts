import { operationDayLabel, sessionDayLabel, storeMonthStart, storeToday } from './store-date';

describe('store-date', () => {
  it('hoy es el día de Bolivia, no el del dispositivo', () => {
    // 02:30 UTC del 30-10 son las 22:30 del 29-10 en Bolivia
    expect(storeToday(new Date('2026-10-30T02:30:00Z'))).toBe('2026-10-29');
    expect(storeToday(new Date('2026-10-30T04:00:00Z'))).toBe('2026-10-30');
    expect(storeMonthStart(new Date('2026-11-01T03:59:00Z'))).toBe('2026-10-01');
  });

  it('CAJ-11: marca una venta o movimiento cargado otro día que su día de operación', () => {
    // 00:20 del 01-11 en Bolivia
    expect(operationDayLabel('2026-10-31', '2026-11-01T04:20:00Z')).toBe('Día 31/10 · cargada el 01/11 00:20');
    // 23:59 del 31-10 en Bolivia es el mismo día: sin marca
    expect(operationDayLabel('2026-10-31', '2026-11-01T03:59:00Z')).toBeNull();
    expect(operationDayLabel(null, '2026-11-01T04:20:00Z')).toBeNull();
  });

  it('CAJ-11: marca una caja cuyo día no es el de su apertura', () => {
    expect(sessionDayLabel('2026-10-28', '2026-10-29T13:10:00Z')).toBe('Día 28/10 · abierta el 29/10');
    expect(sessionDayLabel('2026-10-31', '2026-10-31T13:10:00Z')).toBeNull();
    expect(sessionDayLabel(undefined, '2026-10-31T13:10:00Z')).toBeNull();
  });
});
