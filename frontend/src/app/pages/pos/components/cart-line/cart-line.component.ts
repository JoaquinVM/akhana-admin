import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CartItem, freeLineLabel, isWeightLine, lineName } from '../../register-sale/cart-item';

/**
 * One cart line of the register-sale screen: quantity stepper, unit discount, prices and remove. The amounts
 * are computed by register-sale; this row only shows them and reports what the user changes.
 */
@Component({
  selector: 'app-cart-line',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cart-line.component.html',
  styleUrls: ['./cart-line.component.css']
})
export class CartLineComponent {
  readonly item = input.required<CartItem>();
  // CON-02: an at-cost sale has no discounts
  readonly atCost = input<boolean>(false);

  readonly increment = output<void>();
  readonly decrement = output<void>();
  readonly quantityChange = output<number>();
  readonly discountChange = output<number>();
  readonly remove = output<void>();

  readonly name = computed(() => lineName(this.item()));
  readonly freeLabel = computed(() => freeLineLabel(this.item()));
  readonly weightLine = computed(() => isWeightLine(this.item()));
}
