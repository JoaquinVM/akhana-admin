import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { ConsumptionService } from '../../../../core/consumption/consumption.service';
import { Product } from '../../../../core/product/models/product.models';
import { Consumption, ConsumptionRequest } from '../../../../core/consumption/models/consumption.models';
import { round2, todayIso } from '../../../purchases/components/purchase-form-modal/purchase-form-modal.component';
import { WEIGHT_PRESETS } from '../../../pos/register-sale/register-sale.component';

export interface ConsumptionLineDraft {
  product: Product;
  quantity: number;
}

const MAX_MATCHES = 8;

// CON-01: el costo lo pone el servidor con el buy_price del producto; aquí solo se muestra como referencia
@Component({
  selector: 'app-consumption-form-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './consumption-form-modal.component.html',
  styleUrls: ['./consumption-form-modal.component.css']
})
export class ConsumptionFormModalComponent {
  private readonly consumptionService = inject(ConsumptionService);

  isOpen = input<boolean>(false);
  // Catálogo sin eliminados: un producto inactivo puede consumirse
  products = input<Product[]>([]);

  saved = output<Consumption>();
  close = output<void>();

  readonly weightPresets = WEIGHT_PRESETS;
  readonly maxDate = todayIso();

  consumptionDate = signal<string>(todayIso());
  consumedFor = signal<string>('');
  comment = signal<string>('');
  lines = signal<ConsumptionLineDraft[]>([]);
  productSearch = signal<string>('');

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
    this.formError.set(null);
    this.isSubmitting.set(false);
  }

  isWeighted(product: Product): boolean {
    return product.saleUnit === 'KILOGRAM';
  }

  lineSubtotal(line: ConsumptionLineDraft): number {
    return round2((Number(line.quantity) || 0) * (line.product.buyPrice || 0));
  }

  // Advertencia: un producto sin costo registrado vale Bs 0,00
  hasNoCost(line: ConsumptionLineDraft): boolean {
    return !(line.product.buyPrice > 0);
  }

  // El mismo producto dos veces suma la cantidad, como el POS (POS-01)
  addProduct(product: Product): void {
    const index = this.lines().findIndex(l => l.product.id === product.id);
    if (index >= 0) {
      if (!this.isWeighted(product)) {
        this.updateQuantity(index, this.lines()[index].quantity + 1);
      }
    } else {
      this.lines.update(lines => [...lines, { product, quantity: this.isWeighted(product) ? WEIGHT_PRESETS[0].kilograms : 1 }]);
    }
    this.productSearch.set('');
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
      items: this.lines().map(line => ({ productId: line.product.id, quantity: Number(line.quantity) }))
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
