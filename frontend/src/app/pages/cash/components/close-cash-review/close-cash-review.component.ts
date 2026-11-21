import { Component, computed, input, output } from '@angular/core';
import { formatBs } from '../../../../shared/utils/money';

// CAJ-05: the step «Revisar diferencia» of the closing, with the adjustment that will be registered
@Component({
  selector: 'app-close-cash-review',
  standalone: true,
  templateUrl: './close-cash-review.component.html',
  styleUrls: ['./close-cash-review.component.css']
})
export class CloseCashReviewComponent {
  readonly expectedCash = input.required<number>();
  readonly closingAmount = input.required<number>();
  readonly difference = input.required<number>();
  readonly sessionNumber = input<number | null>(null);

  readonly back = output<void>();

  readonly expectedText = computed(() => formatBs(this.expectedCash()));
  readonly countedText = computed(() => formatBs(this.closingAmount()));
  readonly differenceText = computed(() => formatBs(Math.abs(this.difference())));
  readonly kind = computed(() => (this.difference() > 0 ? 'sobrante' : 'faltante'));

  // The same description the server gives the adjustment
  readonly adjustmentName = computed(() => {
    const label = this.difference() > 0 ? 'Sobrante' : 'Faltante';
    const number = this.sessionNumber();
    return number ? `${label} al cierre de caja #${number}` : `${label} al cierre de caja`;
  });
}
