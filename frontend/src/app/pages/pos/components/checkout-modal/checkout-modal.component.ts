import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentMethod, SaleDetailRequest, SaleItemRequest } from '../../../../core/sale/models/sale.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

@Component({
  selector: 'app-checkout-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './checkout-modal.component.html',
  styleUrls: ['./checkout-modal.component.css']
})
export class CheckoutModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() isLoading = false;
  @Input() totalAmount = 0;
  @Input() items: SaleItemRequest[] = [];
  @Input() globalDiscountAmount = 0;

  @Output() close = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<SaleDetailRequest>();

  paymentMethod = signal<PaymentMethod>('EFECTIVO');
  amountCash = signal<number>(0);
  amountReceived = signal<number>(0);

  // En MIXTO, amountQr se calcula como total - amountCash
  amountQr = computed(() => {
    if (this.paymentMethod() === 'QR') {
      return this.totalAmount;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const remaining = this.totalAmount - this.amountCash();
      return remaining > 0 ? Math.round(remaining * 100) / 100 : 0;
    }
    return 0;
  });

  changeGiven = computed(() => {
    if (this.paymentMethod() === 'EFECTIVO') {
      const diff = this.amountReceived() - this.totalAmount;
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const diff = this.amountReceived() - this.amountCash();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    return 0;
  });

  isAmountValid = computed(() => {
    if (this.totalAmount <= 0) return false;

    if (this.paymentMethod() === 'EFECTIVO') {
      return this.amountReceived() >= this.totalAmount;
    }
    if (this.paymentMethod() === 'QR') {
      return true;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const cashPortion = this.amountCash();
      return (
        cashPortion >= 0 &&
        cashPortion <= this.totalAmount &&
        this.amountReceived() >= cashPortion
      );
    }
    return false;
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.paymentMethod.set('EFECTIVO');
      this.amountCash.set(this.totalAmount);
      this.amountReceived.set(this.totalAmount);
    }
    if (changes['totalAmount'] && this.isOpen) {
      if (this.paymentMethod() === 'EFECTIVO') {
        this.amountCash.set(this.totalAmount);
        this.amountReceived.set(this.totalAmount);
      }
    }
  }

  setMethod(method: PaymentMethod): void {
    this.paymentMethod.set(method);
    if (method === 'EFECTIVO') {
      this.amountCash.set(this.totalAmount);
      this.amountReceived.set(this.totalAmount);
    } else if (method === 'QR') {
      this.amountCash.set(0);
      this.amountReceived.set(0);
    } else if (method === 'MIXTO') {
      const half = Math.round((this.totalAmount / 2) * 100) / 100;
      this.amountCash.set(half);
      this.amountReceived.set(half);
    }
  }

  addReceived(amount: number): void {
    this.amountReceived.update((current) => Math.round((current + amount) * 100) / 100);
  }

  setExactAmount(): void {
    if (this.paymentMethod() === 'EFECTIVO') {
      this.amountReceived.set(this.totalAmount);
    } else if (this.paymentMethod() === 'MIXTO') {
      this.amountReceived.set(this.amountCash());
    }
  }

  handleConfirm(): void {
    if (!this.isAmountValid() || this.isLoading) return;

    let finalAmountCash = 0;
    let finalAmountQr = 0;

    if (this.paymentMethod() === 'EFECTIVO') {
      finalAmountCash = this.totalAmount;
      finalAmountQr = 0;
    } else if (this.paymentMethod() === 'QR') {
      finalAmountCash = 0;
      finalAmountQr = this.totalAmount;
    } else if (this.paymentMethod() === 'MIXTO') {
      finalAmountCash = this.amountCash();
      finalAmountQr = this.amountQr();
    }

    const payload: SaleDetailRequest = {
      items: this.items,
      globalDiscountAmount: this.globalDiscountAmount,
      paymentMethod: this.paymentMethod(),
      amountCash: finalAmountCash,
      amountQr: finalAmountQr,
      amountReceived: this.amountReceived(),
      description: `Venta POS (${this.paymentMethod()})`
    };

    this.confirmed.emit(payload);
  }

  handleClose(): void {
    this.close.emit();
  }
}
