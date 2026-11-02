import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CashDayMarkComponent } from './cash-day-mark.component';
import { storeToday } from '../../utils/store-date';

describe('CashDayMarkComponent', () => {
  afterEach(() => vi.useRealTimers());

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

  describe('CAJ-12: al cambiar de día con la pestaña abierta', () => {
    // 23:59:30 del 02-11 en La Paz (UTC-4)
    const BEFORE_LA_PAZ_MIDNIGHT = new Date('2026-11-03T03:59:30Z');

    function openTodaysSession() {
      const fixture = TestBed.createComponent(CashDayMarkComponent);
      fixture.componentRef.setInput('operationDate', '2026-11-02');
      fixture.componentRef.setInput('openedAt', '2026-11-02T13:00:00Z');
      // No detectChanges by hand below: the mark has to show on its own
      fixture.autoDetectChanges();
      return fixture;
    }

    it('la marca aparece sola pasada la medianoche de La Paz, sin recrear el componente', async () => {
      vi.useFakeTimers({ now: BEFORE_LA_PAZ_MIDNIGHT });
      const fixture = openTodaysSession();
      const text = () => (fixture.nativeElement as HTMLElement).textContent?.trim();
      await vi.advanceTimersByTimeAsync(0);
      expect(text()).toBe('');

      // The minute of the clock check, and the frame the page takes to render it
      await vi.advanceTimersByTimeAsync(60_100);
      expect(text()).toBe('Día 02/11 · abierta el 02/11');
    });

    it('la medianoche de otra zona horaria no cuenta', async () => {
      // 00:00 UTC del 03-11 son las 20:00 del 02-11 en La Paz
      vi.useFakeTimers({ now: new Date('2026-11-02T23:59:30Z') });
      const fixture = openTodaysSession();
      await vi.advanceTimersByTimeAsync(60_100);
      expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
    });

    it('al destruir el componente no queda ningún temporizador', async () => {
      vi.useFakeTimers({ now: BEFORE_LA_PAZ_MIDNIGHT });
      const fixture = openTodaysSession();
      await vi.advanceTimersByTimeAsync(0);
      expect(vi.getTimerCount()).toBe(1);

      fixture.destroy();
      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
