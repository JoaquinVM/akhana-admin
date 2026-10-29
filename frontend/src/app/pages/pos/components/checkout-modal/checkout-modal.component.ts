import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentMethod } from '../../../../core/sale/models/sale.models';
import { Customer } from '../../../../core/debt/models/debt.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

// Payment chosen at checkout; register-sale builds the sale (items and discounts)
export interface CheckoutPayment {
  paymentMethod: PaymentMethod;
  amountCash: number;
  amountQr: number;
  amountReceived: number;
  // At-cost sale (CON-02, DEU-05): customer optional, required with credit
  customerId: string | null;
  creditAmount: number;
  // P-39: optional comment of the at-cost sale («Mami precio costo»); empty when none
  comment: string;
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
  // Only the at-cost sale takes a customer and credit (DEU-05)
  @Input('atCost') set atCostInput(value: boolean) {
    this.atCost.set(!!value);
  }
  @Input() customers: Customer[] = [];

  @Output() close = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<CheckoutPayment>();

  readonly total = signal<number>(0);
  readonly atCost = signal<boolean>(false);
  paymentMethod = signal<PaymentMethod>('EFECTIVO');
  amountCash = signal<number>(0);
  amountReceived = signal<number>(0);
  customerId = signal<string | null>(null);
  creditAmount = signal<number>(0);
  comment = signal<string>('');

  totalAmount = computed<number>(() => this.total());

  // What is paid now: the total minus the credit part
  amountDue = computed<number>(() => {
    const credit = this.atCost() ? this.creditAmount() : 0;
    return Math.max(0, Math.round((this.totalAmount() - credit) * 100) / 100);
  });

  hasCredit = computed<boolean>(() => this.atCost() && this.creditAmount() > 0);

  // The whole sale on credit: sent as CREDITO with no payment
  isFullCredit = computed<boolean>(() => this.hasCredit() && this.amountDue() === 0);

  amountQr = computed<number>(() => {
    if (this.paymentMethod() === 'QR') {
      return this.amountDue();
    }
    if (this.paymentMethod() === 'MIXTO') {
      const remaining = this.amountDue() - this.amountCash();
      return remaining > 0 ? Math.round(remaining * 100) / 100 : 0;
    }
    return 0;
  });

  changeGiven = computed<number>(() => {
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      const diff = received - this.amountDue();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const diff = received - this.amountCash();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    return 0;
  });

  // POS-04: the server rejects a mixed payment with one part at 0, so the form explains it first
  mixedNeedsBothParts = computed<boolean>(() =>
    this.paymentMethod() === 'MIXTO' && !this.isFullCredit()
    && (this.amountCash() <= 0 || this.amountCash() >= this.amountDue()));

  isPaymentValid = computed<boolean>(() => {
    if (this.totalAmount() <= 0) {
      return false;
    }
    if (this.hasCredit()) {
      if (!this.customerId() || this.creditAmount() > this.totalAmount()) {
        return false;
      }
      if (this.isFullCredit()) {
        return true;
      }
    }
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      return received >= this.amountDue();
    }
    if (this.paymentMethod() === 'QR') {
      return true;
    }
    if (this.paymentMethod() === 'MIXTO') {
      // POS-04: both parts above 0; all in one is Efectivo or QR
      const cashPortion = this.amountCash();
      return (
        cashPortion > 0 &&
        cashPortion < this.amountDue() &&
        received >= cashPortion
      );
    }
    return false;
  });

  ngOnChanges(changes: SimpleChanges): void {
    // On open the amounts restart with the last method chosen, without credit.
    // P-49: the at-cost sale is for anyone, so «Sin cliente» is proposed
    if (changes['isOpen'] && this.isOpen) {
      this.creditAmount.set(0);
      this.comment.set('');
      this.customerId.set(null);
      this.setPaymentMethod(this.paymentMethod());
    }
  }

  setPaymentMethod(method: PaymentMethod): void {
    this.paymentMethod.set(method);
    const due = this.amountDue();

    if (method === 'EFECTIVO') {
      this.amountCash.set(due);
      this.amountReceived.set(due);
    } else if (method === 'QR') {
      this.amountCash.set(0);
      this.amountReceived.set(0);
    } else if (method === 'MIXTO') {
      const half = Math.round((due / 2) * 100) / 100;
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
      this.amountReceived.set(this.amountDue());
    } else if (this.paymentMethod() === 'MIXTO') {
      this.amountReceived.set(this.amountCash());
    }
  }

  onCashPortionChange(val: number): void {
    const previousCash = this.amountCash();
    const wasExact = this.amountReceived() === previousCash;
    const newCash = Math.max(0, Math.min(this.amountDue(), val || 0));
    this.amountCash.set(newCash);
    if (wasExact || this.amountReceived() < newCash) {
      this.amountReceived.set(newCash);
    }
  }

  onCustomerChange(customerId: string | null): void {
    this.customerId.set(customerId || null);
  }

  // Credit goes from 0 to the total; the payment amounts are recomputed on what is left
  onCreditChange(val: number | null): void {
    const parsed = val !== null && !isNaN(val) ? Number(val) : 0;
    const credit = Math.max(0, Math.min(this.totalAmount(), Math.round(parsed * 100) / 100));
    this.creditAmount.set(credit);
    // P-49: giving credit without a customer proposes the only active one
    if (credit > 0 && !this.customerId() && this.customers.length === 1) {
      this.customerId.set(this.customers[0].id);
    }
    // A non-payment method (CREDITO) is never left selected in the tabs
    this.setPaymentMethod(this.paymentMethod() === 'CREDITO' ? 'EFECTIVO' : this.paymentMethod());
  }

  setFullCredit(): void {
    this.onCreditChange(this.totalAmount());
  }

  handleConfirm(): void {
    if (!this.isPaymentValid() || this.isSubmitting) {
      return;
    }
    const customerId = this.atCost() ? this.customerId() : null;
    const creditAmount = this.atCost() ? this.creditAmount() : 0;
    const comment = this.atCost() ? this.comment().trim() : '';
    if (this.isFullCredit()) {
      this.confirmed.emit({ paymentMethod: 'CREDITO', amountCash: 0, amountQr: 0, amountReceived: 0, customerId, creditAmount, comment });
      return;
    }
    const method = this.paymentMethod();
    this.confirmed.emit({
      paymentMethod: method,
      amountCash: method === 'EFECTIVO' ? this.amountDue() : method === 'MIXTO' ? this.amountCash() : 0,
      amountQr: this.amountQr(),
      amountReceived: this.amountReceived(),
      customerId,
      creditAmount,
      comment
    });
  }

  handleClose(): void {
    this.close.emit();
  }
}
