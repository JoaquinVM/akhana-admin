import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { openSessionDayLabel } from '../../utils/store-date';

// How often the mark looks at the clock. A repeating check also catches a device that slept through midnight
const CLOCK_CHECK_MS = 60_000;

// CAJ-12: «Día 28/10 · abierta el 30/10» on an open session that is not today's; nothing on today's
@Component({
  selector: 'app-cash-day-mark',
  standalone: true,
  template: `
    @if (label(); as text) {
      <span class="cash-day-mark" title="Sus ventas y movimientos cuentan para este día">{{ text }}</span>
    }
  `,
  styles: `
    .cash-day-mark {
      display: inline-block;
      padding: 0.15rem 0.5rem;
      font-size: 0.8rem;
      font-weight: 600;
      color: #8a5a00;
      background: #fff4dc;
      border-radius: 6px;
    }
  `
})
export class CashDayMarkComponent {
  operationDate = input<string | null | undefined>(null);
  openedAt = input<string | null | undefined>(null);

  private readonly now = signal(new Date());

  // The clock is a signal so the computed does not keep its first answer past midnight
  readonly label = computed(() => openSessionDayLabel(this.operationDate(), this.openedAt(), this.now()));

  constructor() {
    const clock = setInterval(() => this.now.set(new Date()), CLOCK_CHECK_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(clock));
  }
}
