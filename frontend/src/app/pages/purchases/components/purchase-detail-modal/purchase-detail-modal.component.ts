import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { storeToday } from '../../../../shared/utils/store-date';
import { CashLimitWarningComponent } from '../../../../shared/components/cash-limit-warning/cash-limit-warning.component';
import { VoidReasonModalComponent } from '../void-reason-modal/void-reason-modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import {
  DELIVERY_STATUS_LABELS,
  DELIVERY_TYPE_LABELS,
  DeliveryStatus,
  DeliveryType,
  PAYMENT_STATUS_LABELS,
  PaymentAccount,
  Purchase,
  PurchasePayment,
  PurchasePaymentStatus,
  paymentSourceFields,
  paymentSourceName
} from '../../../../core/purchase/models/purchase.models';
import { Creditor } from '../../../../core/debt/models/debt.models';
import { formatBs } from '../../../../shared/utils/money';
import { StoreDayPipe } from '../../../../shared/utils/store-day.pipe';

@Component({
  selector: 'app-purchase-detail-modal',
  standalone: true,
  imports: [StoreDayPipe, CommonModule, FormsModule, ModalComponent, VoidReasonModalComponent, CashLimitWarningComponent],
  templateUrl: './purchase-detail-modal.component.html',
  styleUrls: ['./purchase-detail-modal.component.css']
})
export class PurchaseDetailModalComponent {
  private readonly purchaseService = inject(PurchaseService);

  purchaseId = input<string | null>(null);
  accounts = input<PaymentAccount[]>([]);
  // Active people who can pay a purchase for the business (DEU-02)
  creditors = input<Creditor[]>([]);

  // Emits the updated purchase after a payment, a delivery change or a void, to refresh the list
  updated = output<Purchase>();
  close = output<void>();

  purchase = signal<Purchase | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // 'account:<id>' o 'creditor:<id>'
  paymentSource = signal<string>('');
  paymentAmount = signal<number | null>(null);
  paymentNotes = signal<string>('');
  paymentError = signal<string | null>(null);
  isPaying = signal<boolean>(false);

  // COM-07: entrega
  readonly deliveryTypes = Object.entries(DELIVERY_TYPE_LABELS) as [DeliveryType, string][];
  readonly deliveryStatuses = Object.entries(DELIVERY_STATUS_LABELS) as [DeliveryStatus, string][];
  readonly today = todayIso();
  isEditingDelivery = signal<boolean>(false);
  deliveryType = signal<DeliveryType>('EN_TIENDA');
  deliveryStatus = signal<DeliveryStatus>('PENDIENTE');
  deliveryDate = signal<string>('');
  deliveryError = signal<string | null>(null);
  isSavingDelivery = signal<boolean>(false);

  // COM-09: void of the purchase (voidTarget = 'PURCHASE') or of a payment
  voidTarget = signal<'PURCHASE' | PurchasePayment | null>(null);
  voidError = signal<string | null>(null);
  isVoiding = signal<boolean>(false);

  isActive = computed(() => this.purchase()?.status === 'REGISTRADO');
  // FIN-11: a historical purchase is never edited or voided; it only takes payments
  isEditable = computed(() => this.isActive() && !this.purchase()?.historical);

  canPay = computed(() => {
    const purchase = this.purchase();
    return !!purchase && purchase.status === 'REGISTRADO' && purchase.balance > 0;
  });

  voidTitle = computed(() => {
    const target = this.voidTarget();
    if (!target) {
      return '';
    }
    return target === 'PURCHASE' ? 'Anular pedido ' + this.purchase()?.purchaseNumber : 'Anular pago';
  });

  voidMessage = computed(() => {
    const target = this.voidTarget();
    if (target === 'PURCHASE') {
      return 'Se anulan todos los pagos y el envío del pedido y cada cuenta recupera su monto. '
        + 'El costo y precio de los productos no cambian.';
    }
    if (target) {
      if (target.creditorName) {
        return `Se anula el pago de ${formatBs(target.amount)} hecho por ${target.creditorName} y baja la deuda con esa persona.`;
      }
      return `Se anula el pago de ${formatBs(target.amount)} desde ${target.accountName} y la cuenta recupera el monto.`;
    }
    return '';
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

  // A payment made by a person moves no money: the note goes on the account movement
  paysFromAccount = computed(() => this.paymentSource().startsWith('account:'));
  paysFromCash = computed(() => !!this.accounts().find((a) => 'account:' + a.id === this.paymentSource())?.cashAccount);

  sourceName(payment: PurchasePayment): string {
    return paymentSourceName(payment);
  }

  statusLabel(status: PurchasePaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  deliveryTypeLabel(type: DeliveryType): string {
    return DELIVERY_TYPE_LABELS[type];
  }

  deliveryStatusLabel(status: DeliveryStatus): string {
    return DELIVERY_STATUS_LABELS[status];
  }

  private load(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.paymentError.set(null);
    this.purchaseService.getPurchaseById(id).subscribe({
      next: purchase => {
        this.purchase.set(purchase);
        this.resetPaymentForm(purchase);
        this.isEditingDelivery.set(false);
        this.isLoading.set(false);
      },
      error: err => {
        this.errorMessage.set(err.error?.message || 'No se pudo cargar el pedido.');
        this.isLoading.set(false);
      }
    });
  }

  private resetPaymentForm(purchase: Purchase): void {
    this.paymentSource.set('');
    this.paymentAmount.set(purchase.balance > 0 ? purchase.balance : null);
    this.paymentNotes.set('');
  }

  // COM-03: the line cost differed from the registered one when buying
  costChanged(item: Purchase['items'][number]): boolean {
    return item.unitCost !== item.previousUnitCost;
  }

  registerPayment(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    if (!this.paymentSource()) {
      this.paymentError.set('Elija la cuenta o la persona que pagó.');
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
      ...paymentSourceFields(this.paymentSource()),
      amount,
      notes: this.paysFromAccount() && notes.length > 0 ? notes : null
    }).subscribe({
      next: updated => {
        this.purchase.set(updated);
        this.resetPaymentForm(updated);
        this.isPaying.set(false);
        this.updated.emit(updated);
      },
      error: err => {
        this.paymentError.set(err.error?.message || 'No se pudo registrar el pago.');
        this.isPaying.set(false);
      }
    });
  }

  startDeliveryEdit(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    this.deliveryType.set(purchase.deliveryType);
    this.deliveryStatus.set(purchase.deliveryStatus);
    this.deliveryDate.set(purchase.deliveryDate ?? '');
    this.deliveryError.set(null);
    this.isEditingDelivery.set(true);
  }

  saveDelivery(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    this.isSavingDelivery.set(true);
    this.deliveryError.set(null);
    this.purchaseService.updateDelivery(purchase.id, {
      deliveryType: this.deliveryType(),
      deliveryStatus: this.deliveryStatus(),
      deliveryDate: this.deliveryDate() || null
    }).subscribe({
      next: updated => {
        this.purchase.set(updated);
        this.isEditingDelivery.set(false);
        this.isSavingDelivery.set(false);
        this.updated.emit(updated);
      },
      error: err => {
        this.deliveryError.set(err.error?.message || 'No se pudo guardar la entrega.');
        this.isSavingDelivery.set(false);
      }
    });
  }

  openVoid(target: 'PURCHASE' | PurchasePayment): void {
    this.voidError.set(null);
    this.voidTarget.set(target);
  }

  closeVoid(): void {
    if (!this.isVoiding()) {
      this.voidTarget.set(null);
    }
  }

  confirmVoid(voidReason: string): void {
    const purchase = this.purchase();
    const target = this.voidTarget();
    if (!purchase || !target) {
      return;
    }
    this.isVoiding.set(true);
    this.voidError.set(null);
    const request = target === 'PURCHASE'
      ? this.purchaseService.voidPurchase(purchase.id, { voidReason })
      : this.purchaseService.voidPayment(purchase.id, target.id, { voidReason });
    request.subscribe({
      next: updated => {
        this.purchase.set(updated);
        this.resetPaymentForm(updated);
        this.isVoiding.set(false);
        this.voidTarget.set(null);
        this.updated.emit(updated);
      },
      error: err => {
        this.voidError.set(err.error?.message || 'No se pudo anular.');
        this.isVoiding.set(false);
      }
    });
  }

  onClose(): void {
    if (!this.isPaying() && !this.isVoiding() && !this.isSavingDelivery()) {
      this.close.emit();
    }
  }
}

function todayIso(): string {
  return storeToday();
}
