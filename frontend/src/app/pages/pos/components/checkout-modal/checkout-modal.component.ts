import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentMethod } from '../../../../core/sale/models/sale.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

// Pago elegido en el cobro; la venta (ítems y descuentos) la arma register-sale
export interface CheckoutPayment {
  paymentMethod: PaymentMethod;
  amountCash: number;
  amountQr: number;
  amountReceived: number;
}

@Component({
  selector: 'app-checkout-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './checkout-modal.component.html',
  styleUrls: ['./checkout-modal.component.css']
})
export class CheckoutModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() cartCount = 0;
  @Input() isSubmitting = false;
  @Input() errorMessage: string | null = null;
  @Input('totalAmount') set totalInput(value: number) {
    this.total.set(value || 0);
  }

  @Output() close = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<CheckoutPayment>();

  readonly total = signal<number>(0);
  paymentMethod = signal<PaymentMethod>('EFECTIVO');
  amountCash = signal<number>(0);
  amountReceived = signal<number>(0);

  totalAmount = computed<number>(() => this.total());

  amountQr = computed<number>(() => {
    if (this.paymentMethod() === 'QR') {
      return this.totalAmount();
    }
    if (this.paymentMethod() === 'MIXTO') {
      const remaining = this.totalAmount() - this.amountCash();
      return remaining > 0 ? Math.round(remaining * 100) / 100 : 0;
    }
    return 0;
  });

  changeGiven = computed<number>(() => {
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      const diff = received - this.totalAmount();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const diff = received - this.amountCash();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    return 0;
  });

  isPaymentValid = computed<boolean>(() => {
    if (this.totalAmount() <= 0) {
      return false;
    }
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      return received >= this.totalAmount();
    }
    if (this.paymentMethod() === 'QR') {
      return true;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const cashPortion = this.amountCash();
      return (
        cashPortion >= 0 &&
        cashPortion <= this.totalAmount() &&
        received >= cashPortion
      );
    }
    return false;
  });

  ngOnChanges(changes: SimpleChanges): void {
    // Al abrir se reinician los montos con el método elegido la última vez
    if (changes['isOpen'] && this.isOpen) {
      this.setPaymentMethod(this.paymentMethod());
    }
  }

  setPaymentMethod(method: PaymentMethod): void {
    this.paymentMethod.set(method);
    const total = this.totalAmount();

    if (method === 'EFECTIVO') {
      this.amountCash.set(total);
      this.amountReceived.set(total);
    } else if (method === 'QR') {
      this.amountCash.set(0);
      this.amountReceived.set(0);
    } else if (method === 'MIXTO') {
      const half = Math.round((total / 2) * 100) / 100;
      this.amountCash.set(half);
      this.amountReceived.set(half);
    }
  }

  addReceived(amount: number): void {
    this.amountReceived.update((current) => Math.round(((current || 0) + amount) * 100) / 100);
  }

  onAmountReceivedChange(val: number | null): void {
    const parsed = val !== null && !isNaN(val) ? Number(val) : 0;
    this.amountReceived.set(parsed);
  }

  setExactAmount(): void {
    if (this.paymentMethod() === 'EFECTIVO') {
      this.amountReceived.set(this.totalAmount());
    } else if (this.paymentMethod() === 'MIXTO') {
      this.amountReceived.set(this.amountCash());
    }
  }

  onCashPortionChange(val: number): void {
    const previousCash = this.amountCash();
    const wasExact = this.amountReceived() === previousCash;
    const newCash = Math.max(0, Math.min(this.totalAmount(), val || 0));
    this.amountCash.set(newCash);
    if (wasExact || this.amountReceived() < newCash) {
      this.amountReceived.set(newCash);
    }
  }

  handleConfirm(): void {
    if (!this.isPaymentValid() || this.isSubmitting) {
      return;
    }
    const method = this.paymentMethod();
    this.confirmed.emit({
      paymentMethod: method,
      amountCash: method === 'EFECTIVO' ? this.totalAmount() : method === 'MIXTO' ? this.amountCash() : 0,
      amountQr: this.amountQr(),
      amountReceived: this.amountReceived()
    });
  }

  handleClose(): void {
    this.close.emit();
  }
}
