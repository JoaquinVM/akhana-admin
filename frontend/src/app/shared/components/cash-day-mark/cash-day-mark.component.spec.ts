import { TestBed } from '@angular/core/testing';
import { CashDayMarkComponent } from './cash-day-mark.component';
import { storeToday } from '../../utils/store-date';

describe('CashDayMarkComponent', () => {
  function textOf(operationDate: string | null, openedAt: string | null): string {
    const fixture = TestBed.createComponent(CashDayMarkComponent);
    fixture.componentRef.setInput('operationDate', operationDate);
    fixture.componentRef.setInput('openedAt', openedAt);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
  }

  it('CAJ-12: una caja de un día anterior lleva la marca', () => {
    expect(textOf('2026-10-28', '2026-10-30T13:00:00Z')).toBe('Día 28/10 · abierta el 30/10');
  });

  it('CAJ-12: la que sigue abierta en un día posterior al suyo también, aunque se abriera ese mismo día', () => {
    expect(textOf('2026-10-02', '2026-10-02T13:00:00Z')).toBe('Día 02/10 · abierta el 02/10');
  });

  it('CAJ-12: la de hoy no lleva marca', () => {
    expect(textOf(storeToday(), new Date().toISOString())).toBe('');
  });

  it('sin datos no muestra nada', () => {
    expect(textOf(null, null)).toBe('');
  });
});
