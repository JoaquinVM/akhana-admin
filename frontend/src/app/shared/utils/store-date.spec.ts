import { openSessionDayLabel, operationDayLabel, sessionDayLabel, shortDayLabel, storeMonthStart, storeToday, weekdayDayLabel } from './store-date';

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

  it('CAJ-12: marca la caja abierta de un día anterior; la de hoy no lleva marca', () => {
    const now = new Date('2026-10-30T15:00:00Z');
    expect(openSessionDayLabel('2026-10-28', '2026-10-30T13:00:00Z', now)).toBe('Día 28/10 · abierta el 30/10');
    expect(openSessionDayLabel('2026-10-30', '2026-10-30T13:00:00Z', now)).toBeNull();
    expect(openSessionDayLabel(null, '2026-10-30T13:00:00Z', now)).toBeNull();
    expect(openSessionDayLabel('2026-10-28', null, now)).toBeNull();
  });

  it('CAJ-12: también si se abrió en su día y sigue abierta o se reabrió otro día', () => {
    const now = new Date('2026-11-03T15:00:00Z');
    expect(openSessionDayLabel('2026-11-02', '2026-11-02T13:00:00Z', now)).toBe('Día 02/11 · abierta el 02/11');
  });

  it('CAJ-12: el cambio de día de la marca sigue la hora de Bolivia, no la del dispositivo', () => {
    // 23:30 del 02-11 en Bolivia: sigue siendo hoy
    expect(openSessionDayLabel('2026-11-02', '2026-11-02T13:00:00Z', new Date('2026-11-03T03:30:00Z'))).toBeNull();
    // 00:30 del 03-11 en Bolivia: ya es de ayer
    expect(openSessionDayLabel('2026-11-02', '2026-11-02T13:00:00Z', new Date('2026-11-03T04:30:00Z'))).toBe('Día 02/11 · abierta el 02/11');
  });

  it('CAJ-13: nombra el día de la semana sin depender del dispositivo', () => {
    expect(weekdayDayLabel('2026-10-31')).toBe('sábado 31/10');
    expect(weekdayDayLabel('2026-11-02')).toBe('lunes 2/11');
    expect(weekdayDayLabel('2026-11-01')).toBe('domingo 1/11');
  });

  it('CAJ-12: el día de los mensajes va como en la spec', () => {
    expect(shortDayLabel('2026-11-02')).toBe('2/11');
    expect(shortDayLabel('2026-10-30')).toBe('30/10');
  });

  it('CAJ-11: marca una caja cuyo día no es el de su apertura', () => {
    expect(sessionDayLabel('2026-10-28', '2026-10-29T13:10:00Z')).toBe('Día 28/10 · abierta el 29/10');
    expect(sessionDayLabel('2026-10-31', '2026-10-31T13:10:00Z')).toBeNull();
    expect(sessionDayLabel(undefined, '2026-10-31T13:10:00Z')).toBeNull();
  });
});
