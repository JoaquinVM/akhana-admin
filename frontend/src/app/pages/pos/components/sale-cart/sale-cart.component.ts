import { Component, input, output } from '@angular/core';
import { CartLineComponent } from '../cart-line/cart-line.component';
import { CartItem } from '../../register-sale/cart-item';

// A value typed on one cart line (quantity or unit discount), with the key of the line
export interface CartLineChange {
  key: string;
  value: number;
}

/**
 * The cart of the register-sale screen: item count, empty cart, at-cost switch (CON-02) and the lines. The cart
 * lives in register-sale; this card only shows it and reports what the user does.
 */
@Component({
  selector: 'app-sale-cart',
  standalone: true,
  imports: [CartLineComponent],
  templateUrl: './sale-cart.component.html',
  styleUrls: ['./sale-cart.component.css']
})
export class SaleCartComponent {
  readonly items = input<CartItem[]>([]);
  readonly cartCount = input<number>(0);
  readonly atCost = input<boolean>(false);

  readonly clear = output<void>();
  // The raw change event: register-sale may refuse the switch and put the checkbox back
  readonly atCostToggle = output<Event>();
  readonly increment = output<string>();
  readonly decrement = output<string>();
  readonly quantityChange = output<CartLineChange>();
  readonly discountChange = output<CartLineChange>();
  readonly remove = output<string>();
}
