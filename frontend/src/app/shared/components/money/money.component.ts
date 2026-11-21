import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

// Shows an amount exactly as the number pipe writes it ("Bs 1,234.50"), only with the "Bs" and the cents smaller
// and softer so the bolivianos read first. The text, and so the format, does not change.
@Component({
  selector: 'app-money',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@let text = (value() | number: '1.2-2') ?? '';<span class="money-currency">Bs</span> {{ text.slice(0, -3) }}<span class="money-cents">{{ text.slice(-3) }}</span>`,
  styles: `
    :host { white-space: nowrap; }
    .money-currency { font-size: 0.62em; font-weight: 500; color: var(--text-soft); margin-right: 0.1em; }
    .money-cents { font-size: 0.68em; color: var(--text-soft); }
  `
})
export class MoneyComponent {
  readonly value = input.required<number | null | undefined>();
}
