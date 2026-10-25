import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import {
  PAYMENT_STATUS_LABELS,
  PaymentAccount,
  Purchase,
  PurchasePaymentStatus
} from '../../../../core/purchase/models/purchase.models';

@Component({
  selector: 'app-purchase-detail-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './purchase-detail-modal.component.html',
  styleUrls: ['./purchase-detail-modal.component.css']
})
export class PurchaseDetailModalComponent {
  private readonly purchaseService = inject(PurchaseService);

  purchaseId = input<string | null>(null);
  accounts = input<PaymentAccount[]>([]);

  // Emite el pedido actualizado tras un pago, para refrescar el listado
  paid = output<Purchase>();
  close = output<void>();

  purchase = signal<Purchase | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  paymentAccountId = signal<string>('');
  paymentAmount = signal<number | null>(null);
  paymentNotes = signal<string>('');
  paymentError = signal<string | null>(null);
  isPaying = signal<boolean>(false);

  canPay = computed(() => {
    const purchase = this.purchase();
    return !!purchase && purchase.status === 'REGISTRADO' && purchase.balance > 0;
  });

  constructor() {
    effect(() => {
      const id = this.purchaseId();
      if (id) {
        this.load(id);
      } else {
        this.purchase.set(null);
      }
    });
  }

  statusLabel(status: PurchasePaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  private load(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.paymentError.set(null);
    this.purchaseService.getPurchaseById(id).subscribe({
      next: purchase => {
        this.purchase.set(purchase);
        this.resetPaymentForm(purchase);
        this.isLoading.set(false);
      },
      error: err => {
        this.errorMessage.set(err.error?.message || 'No se pudo cargar el pedido.');
        this.isLoading.set(false);
      }
    });
  }

  private resetPaymentForm(purchase: Purchase): void {
    this.paymentAccountId.set('');
    this.paymentAmount.set(purchase.balance > 0 ? purchase.balance : null);
    this.paymentNotes.set('');
  }

  // COM-03: hubo diferencia entre el costo de la línea y el registrado al comprar
  costChanged(item: Purchase['items'][number]): boolean {
    return item.unitCost !== item.previousUnitCost;
  }

  registerPayment(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    if (!this.paymentAccountId()) {
      this.paymentError.set('Elija la cuenta de origen del pago.');
      return;
    }
    const amount = Number(this.paymentAmount());
    if (!amount || amount <= 0) {
      this.paymentError.set('Ingrese un monto mayor a 0.');
      return;
    }
    this.isPaying.set(true);
    this.paymentError.set(null);
    const notes = this.paymentNotes().trim();
    this.purchaseService.registerPayment(purchase.id, {
      accountId: this.paymentAccountId(),
      amount,
      notes: notes.length > 0 ? notes : null
    }).subscribe({
      next: updated => {
        this.purchase.set(updated);
        this.resetPaymentForm(updated);
        this.isPaying.set(false);
        this.paid.emit(updated);
      },
      error: err => {
        this.paymentError.set(err.error?.message || 'No se pudo registrar el pago.');
        this.isPaying.set(false);
      }
    });
  }

  onClose(): void {
    if (!this.isPaying()) {
      this.close.emit();
    }
  }
}
