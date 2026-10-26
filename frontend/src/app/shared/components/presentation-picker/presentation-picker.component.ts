import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  Product,
  ProductPresentation,
  activePresentations,
  presentationPrice
} from '../../../core/product/models/product.models';

/**
 * CAT-11: al elegir un producto por kilo se elige su presentación (de menor a mayor peso) u «Otro peso».
 * Emite la presentación elegida, o null para «Otro peso» (kilos a mano).
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
  // Venta al costo: se muestra peso x costo por kilo; en consumos, el costo
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
