import { Component, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../modal/modal.component';
import { Sale, SaleItem } from '../../../core/cash/models/cash.models';
import { SaleService } from '../../../core/sale/sale.service';

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

  isOpen = input<boolean>(false);
  saleId = input<string | null>(null);
  initialSale = input<Sale | null>(null);

  close = output<void>();

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
    } else {
      list.push({
        label: sale.paymentMethod || 'Otro',
        amount: sale.totalAmount
      });
    }
    return list;
  }
}
