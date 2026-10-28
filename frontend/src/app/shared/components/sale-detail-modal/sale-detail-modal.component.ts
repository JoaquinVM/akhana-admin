import { Component, Injector, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../modal/modal.component';
import { Sale, SaleItem } from '../../../core/cash/models/cash.models';
import { SaleService } from '../../../core/sale/sale.service';
import { ProductService } from '../../../core/product/product.service';
import { Product, isSpecialProduct } from '../../../core/product/models/product.models';

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
  // POS-12: el catálogo solo se pide al asignar, desde el historial
  private readonly injector = inject(Injector);

  isOpen = input<boolean>(false);
  saleId = input<string | null>(null);
  initialSale = input<Sale | null>(null);
  // POS-12: solo el historial de ventas asigna las líneas sin identificar
  canAssign = input<boolean>(false);

  close = output<void>();
  // POS-12: la venta después de asignar una línea, para refrescar el listado
  saleUpdated = output<Sale>();

  // POS-12: línea que se está asignando y productos que se pueden elegir (activos, normales, por unidad)
  assigningItemId = signal<string | null>(null);
  closingItemId = signal<string | null>(null);
  assignSearch = signal<string>('');
  // Producto elegido, a confirmar antes de asignar: la asignación no se deshace
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
        // Al cerrar se libera la memoria de la venta consultada
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
        // Si fallara la red y disponemos de initialSale, usamos initial como fallback
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
      // FIN-11: sin pagos; el dinero de ese día está en los movimientos migrados
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
    // DEU-05: la parte que quedó por cobrar al cliente
    if ((sale.creditAmount ?? 0) > 0) {
      list.push({
        label: 'Crédito',
        amount: sale.creditAmount ?? 0
      });
    }
    return list;
  }
}
