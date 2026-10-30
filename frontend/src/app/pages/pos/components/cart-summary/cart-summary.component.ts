import { Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

/**
 * Money summary of the register-sale cart: subtotals, discounts (POS-03), total and the button to charge. Every
 * amount is computed by register-sale, which also checks the general discount; this card only shows them.
 */
@Component({
  selector: 'app-cart-summary',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  templateUrl: './cart-summary.component.html',
  styleUrls: ['./cart-summary.component.css']
})
export class CartSummaryComponent {
  readonly subtotalGross = input<number>(0);
  readonly discountItemsTotal = input<number>(0);
  readonly subtotalPostItems = input<number>(0);
  readonly globalDiscount = input<number>(0);
  // P-44: the general discount is spread only over the normal lines
  readonly maxGlobalDiscount = input<number>(0);
  readonly discountTotal = input<number>(0);
  readonly totalAmount = input<number>(0);
  // CON-02: an at-cost sale has no discounts; with only free lines there is no general discount
  readonly atCost = input<boolean>(false);
  readonly onlyFreeLines = input<boolean>(false);
  readonly canCheckout = input<boolean>(false);

  readonly globalDiscountChange = output<number>();
  readonly checkout = output<void>();
}
