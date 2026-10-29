import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  Product,
  ProductPresentation,
  activePresentations,
  presentationPrice
} from '../../../core/product/models/product.models';

/**
 * CAT-11: choosing a product by kilo asks for its presentation (lightest first) or «Otro peso».
 * Emits the chosen presentation, or null for «Otro peso» (kilos typed by hand).
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

  picked = output<ProductPresentation | null>();
  cancelled = output<void>();

  options = computed(() =>
    activePresentations(this.product())
      .slice()
      .sort((a, b) => a.weightKg - b.weightKg)
      .map((presentation) => ({ presentation, amount: this.amountOf(presentation) }))
  );

  private amountOf(presentation: ProductPresentation): number {
    const mode = this.priceMode();
    if (mode === 'cost') return presentation.cost;
    return presentationPrice(presentation, this.product(), mode === 'atCost');
  }
}
