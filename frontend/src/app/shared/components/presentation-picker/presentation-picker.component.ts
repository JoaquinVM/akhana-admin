import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  Product,
  ProductPresentation,
  presentationPrice,
  soldPresentations
} from '../../../core/product/models/product.models';

/**
 * CAT-11, CAT-15: choosing a product by kilo asks for one of its packages that are sold (lightest first) or «Otro
 * peso». A package only bought (Saco 25 kg) is not offered. Emits the chosen package, or null for «Otro peso».
 */
@Component({
  selector: 'app-presentation-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './presentation-picker.component.html',
  styleUrls: ['./presentation-picker.component.css']
})
export class PresentationPickerComponent {
  product = input.required<Product>();
  // At-cost sale: shows weight x cost per kilo; in consumptions, the cost
  priceMode = input<'price' | 'atCost' | 'cost'>('price');
  // POS-10: the POS also takes grams
  otherWeightHint = input<string>('kilos');

  picked = output<ProductPresentation | null>();
  cancelled = output<void>();

  options = computed(() =>
    soldPresentations(this.product())
      .slice()
      .sort((a, b) => a.baseQuantity - b.baseQuantity)
      .map((presentation) => ({ presentation, amount: this.amountOf(presentation) }))
  );

  private amountOf(presentation: ProductPresentation): number {
    const mode = this.priceMode();
    if (mode === 'cost') return presentation.cost;
    return presentationPrice(presentation, mode === 'atCost');
  }
}
