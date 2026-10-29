import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { ConsumptionService } from '../../../../core/consumption/consumption.service';
import { Product, ProductPresentation, activePresentations } from '../../../../core/product/models/product.models';
import { Consumption, ConsumptionRequest } from '../../../../core/consumption/models/consumption.models';
import { round2, todayIso } from '../../../purchases/components/purchase-form-modal/purchase-form-modal.component';
import { WEIGHT_STEP_KG } from '../../../pos/register-sale/register-sale.component';
import { PresentationPickerComponent } from '../../../../shared/components/presentation-picker/presentation-picker.component';

export interface ConsumptionLineDraft {
  product: Product;
  // CAT-11: with a presentation the quantity is whole packs
  presentation: ProductPresentation | null;
  quantity: number;
}

const MAX_MATCHES = 8;

// CON-01: the server sets the cost from the product's buy_price; here it is only shown for reference
@Component({
  selector: 'app-consumption-form-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent, PresentationPickerComponent],
  templateUrl: './consumption-form-modal.component.html',
  styleUrls: ['./consumption-form-modal.component.css']
})
export class ConsumptionFormModalComponent {
  private readonly consumptionService = inject(ConsumptionService);

  isOpen = input<boolean>(false);
  // Catalog without deleted products: an inactive product can be consumed
  products = input<Product[]>([]);

  saved = output<Consumption>();
  close = output<void>();

  readonly maxDate = todayIso();

  consumptionDate = signal<string>(todayIso());
  consumedFor = signal<string>('');
  comment = signal<string>('');
  lines = signal<ConsumptionLineDraft[]>([]);
  productSearch = signal<string>('');
  // CAT-11: product by kilo waiting for its presentation
  pickerProduct = signal<Product | null>(null);

  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  productMatches = computed(() => {
    const term = this.productSearch().trim().toLowerCase();
    if (term.length === 0) {
      return [];
    }
    return this.products()
      .filter(p => p.status !== 'ELIMINADO')
      .filter(p => p.name.toLowerCase().includes(term) || p.code.toLowerCase().includes(term))
      .sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }))
      .slice(0, MAX_MATCHES);
  });

  total = computed(() => round2(this.lines().reduce((sum, line) => sum + this.lineSubtotal(line), 0)));

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.reset();
      }
    });
  }

  private reset(): void {
    this.consumptionDate.set(todayIso());
    this.consumedFor.set('');
    this.comment.set('');
    this.lines.set([]);
    this.productSearch.set('');
    this.pickerProduct.set(null);
    this.formError.set(null);
    this.isSubmitting.set(false);
  }

  isWeighted(product: Product): boolean {
    return product.saleUnit === 'KILOGRAM';
  }

  // «Otro peso»: kilos; by unit or presentation: whole numbers
  isWeightLine(line: ConsumptionLineDraft): boolean {
    return this.isWeighted(line.product) && !line.presentation;
  }

  lineKey(line: ConsumptionLineDraft): string {
    return line.presentation ? `${line.product.id}:${line.presentation.id}` : line.product.id;
  }

  // With a presentation, the cost per kilo applies to the packs' kilos, like the server (CAT-11)
  lineSubtotal(line: ConsumptionLineDraft): number {
    const quantity = Number(line.quantity) || 0;
    const kilos = line.presentation ? Math.round(quantity * line.presentation.weightKg * 1000) / 1000 : quantity;
    return round2(kilos * (line.product.buyPrice || 0));
  }

  // Warning: a product without a registered cost is worth Bs 0,00
  hasNoCost(line: ConsumptionLineDraft): boolean {
    return !(line.product.buyPrice > 0);
  }

  // CAT-11: a product by kilo with presentations opens the picker; without them it goes in as «Otro peso»
  addProduct(product: Product): void {
    this.productSearch.set('');
    if (this.isWeighted(product) && activePresentations(product).length > 0) {
      this.pickerProduct.set(product);
      return;
    }
    this.addLine(product, null);
  }

  onPresentationPicked(presentation: ProductPresentation | null): void {
    const product = this.pickerProduct();
    this.pickerProduct.set(null);
    if (product) {
      this.addLine(product, presentation);
    }
  }

  // The same product (and presentation) twice adds up the quantity, like the POS (POS-01)
  private addLine(product: Product, presentation: ProductPresentation | null): void {
    const draft: ConsumptionLineDraft = { product, presentation, quantity: 0 };
    const index = this.lines().findIndex(l => this.lineKey(l) === this.lineKey(draft));
    const byWeight = this.isWeightLine(draft);
    if (index >= 0) {
      if (!byWeight) {
        this.updateQuantity(index, this.lines()[index].quantity + 1);
      }
    } else {
      this.lines.update(lines => [...lines, { ...draft, quantity: byWeight ? WEIGHT_STEP_KG : 1 }]);
    }
  }

  updateQuantity(index: number, value: number | string): void {
    const quantity = Number(value) || 0;
    this.lines.update(lines => lines.map((line, i) => (i === index ? { ...line, quantity } : line)));
  }

  removeLine(index: number): void {
    this.lines.update(lines => lines.filter((_, i) => i !== index));
  }

  buildRequest(): ConsumptionRequest {
    const consumedFor = this.consumedFor().trim();
    const comment = this.comment().trim();
    return {
      consumptionDate: this.consumptionDate() || null,
      consumedFor: consumedFor.length > 0 ? consumedFor : null,
      comment: comment.length > 0 ? comment : null,
      items: this.lines().map(line => ({
        productId: line.product.id,
        quantity: Number(line.quantity),
        ...(line.presentation ? { presentationId: line.presentation.id } : {})
      }))
    };
  }

  submit(): void {
    if (this.lines().length === 0) {
      this.formError.set('El consumo debe tener al menos una línea.');
      return;
    }
    this.isSubmitting.set(true);
    this.formError.set(null);
    this.consumptionService.registerConsumption(this.buildRequest()).subscribe({
      next: consumption => {
        this.isSubmitting.set(false);
        this.saved.emit(consumption);
      },
      error: err => {
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'No se pudo registrar el consumo.');
      }
    });
  }

  onClose(): void {
    if (!this.isSubmitting()) {
      this.close.emit();
    }
  }
}
