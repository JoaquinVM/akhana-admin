import { Component, Injector, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../modal/modal.component';
import { Sale, SaleItem } from '../../../core/cash/models/cash.models';
import { SaleService } from '../../../core/sale/sale.service';
import { ProductService } from '../../../core/product/product.service';
import { Product, isSpecialProduct } from '../../../core/product/models/product.models';
import { operationDayLabel } from '../../utils/store-date';

export interface PaymentMethodItem {
  label: string;
  amount: number;
}

@Component({
  selector: 'app-sale-detail-modal',
  standalone: true,
  imports: [CommonModule, ModalComponent],
  templateUrl: './sale-detail-modal.component.html',
  styleUrls: ['./sale-detail-modal.component.css']
})
export class SaleDetailModalComponent {
  private readonly saleService = inject(SaleService);
  // POS-12: the catalog is only fetched when assigning, from the history
  private readonly injector = inject(Injector);

  isOpen = input<boolean>(false);
  saleId = input<string | null>(null);
  initialSale = input<Sale | null>(null);
  // POS-12: only the sales history assigns unidentified lines
  canAssign = input<boolean>(false);

  close = output<void>();

  // CAJ-11: «Día 31/10 · cargada el 01/11 00:20» when the sale counts for another day than the one it was recorded
  dayMark(sale: Sale): string | null {
    return operationDayLabel(sale.operationDate, sale.createdAt);
  }
  // POS-12: the sale after assigning a line, to refresh the list
  saleUpdated = output<Sale>();

  // POS-12: line being assigned and the products that can be chosen (active, normal, by unit)
  assigningItemId = signal<string | null>(null);
  closingItemId = signal<string | null>(null);
  assignSearch = signal<string>('');
  // Chosen product, to confirm before assigning: the assignment cannot be undone
  chosenProduct = signal<Product | null>(null);
  assignableProducts = signal<Product[]>([]);
  assignError = signal<string | null>(null);
  isAssigning = signal<boolean>(false);

  assignResults = computed<Product[]>(() => {
    const term = this.assignSearch().toLowerCase().trim();
    if (!term) return [];
    return this.assignableProducts()
      .filter((p) => p.name.toLowerCase().includes(term) || p.code.toLowerCase().includes(term))
      .slice(0, 6);
  });

  currentSale = signal<Sale | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  constructor() {
    effect(() => {
      const open = this.isOpen();
      const id = this.saleId();
      const initial = this.initialSale();

      if (open) {
        if (id) {
          this.loadSale(id);
        } else if (initial) {
          this.currentSale.set(initial);
          this.isLoading.set(false);
          this.errorMessage.set(null);
        }
      } else {
        // Closing releases the sale that was being viewed
        this.cancelAssignment();
        this.currentSale.set(null);
        this.isLoading.set(false);
        this.errorMessage.set(null);
      }
    });
  }

  loadSale(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.saleService.getSaleById(id).subscribe({
      next: (sale) => {
        this.currentSale.set(sale);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error al cargar detalle de venta:', err);
        // If the network fails and there is an initialSale, use it as fallback
        if (this.initialSale() && this.initialSale()?.id === id) {
          this.currentSale.set(this.initialSale());
          this.isLoading.set(false);
        } else {
          this.errorMessage.set('No se pudo cargar la información de la venta. Por favor, intenta nuevamente.');
          this.isLoading.set(false);
        }
      }
    });
  }

  onClose(): void {
    this.close.emit();
  }

  freeLineLabel(item: SaleItem): string | null {
    if (item.freeLineType === 'THIRD_PARTY') return `De ${item.creditorName ?? 'tercero'}`;
    switch (item.assignmentStatus) {
      case 'POR_ASIGNAR': return 'Por asignar';
      case 'ASIGNADO': return 'Asignado';
      case 'SIN_IDENTIFICAR': return 'Sin identificar definitivo';
      default: return null;
    }
  }

  canAssignItem(sale: Sale, item: SaleItem): boolean {
    return this.canAssign() && sale.status !== 'ANULADA' && item.assignmentStatus === 'POR_ASIGNAR';
  }

  startAssignment(item: SaleItem): void {
    this.assigningItemId.set(item.id ?? null);
    this.closingItemId.set(null);
    this.assignSearch.set('');
    this.chosenProduct.set(null);
    this.assignError.set(null);
    if (this.assignableProducts().length === 0) {
      this.injector.get(ProductService).getProducts(undefined, 'ACTIVO').subscribe({
        next: (products) => this.assignableProducts.set(
          products.filter((p) => !isSpecialProduct(p) && p.saleUnit === 'UNIT')),
        error: () => this.assignError.set('No se pudo cargar el catálogo de productos.')
      });
    }
  }

  startClosing(item: SaleItem): void {
    this.closingItemId.set(item.id ?? null);
    this.assigningItemId.set(null);
    this.assignError.set(null);
  }

  cancelAssignment(): void {
    this.assigningItemId.set(null);
    this.closingItemId.set(null);
    this.assignSearch.set('');
    this.chosenProduct.set(null);
    this.assignError.set(null);
  }

  chooseProduct(product: Product): void {
    this.chosenProduct.set(product);
    this.assignError.set(null);
  }

  /** Profit the line will have with the chosen product's current cost (POS-12): amount charged − cost × quantity. */
  assignmentProfit(item: SaleItem, product: Product): number {
    const cost = Math.round((product.buyPrice || 0) * (item.quantity || 1) * 100) / 100;
    return Math.round(((item.subtotal || 0) - cost) * 100) / 100;
  }

  assignTo(sale: Sale, item: SaleItem, product: Product): void {
    if (!item.id || this.isAssigning()) return;
    this.isAssigning.set(true);
    this.saleService.assignItem(sale.id, item.id, product.id).subscribe({
      next: (updated) => this.onAssigned(updated),
      error: (err) => this.onAssignError(err)
    });
  }

  confirmClose(sale: Sale, item: SaleItem): void {
    if (!item.id || this.isAssigning()) return;
    this.isAssigning.set(true);
    this.saleService.closeUnidentified(sale.id, item.id).subscribe({
      next: (updated) => this.onAssigned(updated),
      error: (err) => this.onAssignError(err)
    });
  }

  private onAssigned(updated: Sale): void {
    this.isAssigning.set(false);
    this.cancelAssignment();
    this.currentSale.set(updated);
    this.saleUpdated.emit(updated);
  }

  private onAssignError(err: { error?: { message?: string } }): void {
    this.isAssigning.set(false);
    this.assignError.set(err.error?.message || 'No se pudo asignar la línea.');
  }

  getItemDiscountTotal(item: SaleItem): number {
    return (item.discountPerUnit || 0) * (item.quantity || 1);
  }

  getItemDiscountPercentage(item: SaleItem): number {
    if (!item.unitPrice || item.unitPrice <= 0 || !item.discountPerUnit) {
      return 0;
    }
    return (item.discountPerUnit / item.unitPrice) * 100;
  }

  getAccumulatedDiscounts(sale: Sale): number {
    if (sale.discountTotal != null) {
      return sale.discountTotal;
    }
    const itemDiscounts = sale.discountItemsTotal ?? 0;
    const globalDiscount = sale.globalDiscountAmount ?? 0;
    return itemDiscounts + globalDiscount;
  }

  getPaymentMethodsList(sale: Sale): PaymentMethodItem[] {
    const list: PaymentMethodItem[] = [];
    if (sale.paymentMethod === 'EFECTIVO') {
      list.push({
        label: 'Efectivo',
        amount: sale.amountCash != null ? sale.amountCash : sale.totalAmount
      });
    } else if (sale.paymentMethod === 'QR') {
      list.push({
        label: 'QR',
        amount: sale.amountQr != null ? sale.amountQr : sale.totalAmount
      });
    } else if (sale.paymentMethod === 'MIXTO') {
      list.push({
        label: 'Efectivo',
        amount: sale.amountCash ?? 0
      });
      list.push({
        label: 'QR',
        amount: sale.amountQr ?? 0
      });
    } else if (sale.paymentMethod === 'HISTORICO') {
      // FIN-11: without payments; that day's money is in the migrated movements
      list.push({
        label: 'Histórico (dinero en Transacc.)',
        amount: sale.totalAmount
      });
    } else if (sale.paymentMethod !== 'CREDITO') {
      list.push({
        label: sale.paymentMethod || 'Otro',
        amount: sale.totalAmount
      });
    }
    // DEU-05: the part left to collect from the customer
    if ((sale.creditAmount ?? 0) > 0) {
      list.push({
        label: 'Crédito',
        amount: sale.creditAmount ?? 0
      });
    }
    return list;
  }
}
