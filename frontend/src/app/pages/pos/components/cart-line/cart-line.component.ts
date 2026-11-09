import { afterNextRender, Component, computed, inject, Injector, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { QuantityStepperComponent } from '../../../../shared/components/quantity-stepper/quantity-stepper.component';
import { CartItem, freeLineLabel, isWeightLine, lineAmount, lineName } from '../../register-sale/cart-item';

/**
 * One cart line of the register-sale screen: quantity stepper, unit discount or total, prices and remove. The amounts
 * are computed by register-sale; this row only shows them and reports what the user changes.
 */
@Component({
  selector: 'app-cart-line',
  standalone: true,
  imports: [CommonModule, FormsModule, QuantityStepperComponent],
  templateUrl: './cart-line.component.html',
  styleUrls: ['./cart-line.component.css']
})
export class CartLineComponent {
  private readonly injector = inject(Injector);

  readonly item = input.required<CartItem>();
  // CON-02: an at-cost sale has no discounts
  readonly atCost = input<boolean>(false);

  readonly increment = output<void>();
  readonly decrement = output<void>();
  readonly quantityChange = output<number>();
  readonly discountChange = output<number>();
  readonly totalChange = output<number>();
  readonly gramsChange = output<boolean>();
  readonly remove = output<void>();

  readonly name = computed(() => lineName(this.item()));
  readonly freeLabel = computed(() => freeLineLabel(this.item()));
  readonly weightLine = computed(() => isWeightLine(this.item()));
  readonly inGrams = computed(() => this.weightLine() && !!this.item().inGrams);
  readonly shownQuantity = computed(() =>
    this.inGrams() ? Math.round(this.item().quantity * 1000) : this.item().quantity);
  readonly calculatedTotal = computed(() => lineAmount(this.item().quantity, this.item().unitPrice));

  // POS-10: the parent always gets kilos
  onQuantityTyped(value: number): void {
    this.quantityChange.emit(this.inGrams() ? Number(value) / 1000 : value);
  }

  // POS-10: what the parent rejects (empty or 0) goes back to the last valid total
  onTotalTyped(field: HTMLInputElement): void {
    this.totalChange.emit(field.valueAsNumber);
    afterNextRender(() => {
      field.value = this.item().subtotal.toFixed(2);
    }, { injector: this.injector });
  }
}
