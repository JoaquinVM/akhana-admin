import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

// Step of a line by weight: 100 g
export const WEIGHT_STEP_KG = 0.1;

/**
 * Quantity control with − and + buttons and a number field without the browser's spinners. It decides nothing: the
 * parent keeps the quantity and applies its rules (step, minimum, what happens at 0). Width can be set with the CSS
 * variables --qty-stepper-width, --qty-input-width, --qty-stepper-weight-width and --qty-input-weight-width.
 */
@Component({
  selector: 'app-quantity-stepper',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './quantity-stepper.component.html',
  styleUrls: ['./quantity-stepper.component.css']
})
export class QuantityStepperComponent {
  readonly quantity = input.required<number>();
  // By weight: kilos with up to 3 decimals
  readonly weight = input<boolean>(false);
  readonly decrementDisabled = input<boolean>(false);
  // POS-14: on leaving the field empty or in 0, it shows the parent's quantity again
  readonly restoreOnBlur = input<boolean>(false);

  readonly increment = output<void>();
  readonly decrement = output<void>();
  readonly quantityChange = output<number>();

  onBlur(field: HTMLInputElement): void {
    if (this.restoreOnBlur() && !(Number(field.value) > 0)) {
      field.value = String(this.quantity());
    }
  }
}
