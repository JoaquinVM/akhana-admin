import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { VoidReasonModalComponent } from '../../../purchases/components/void-reason-modal/void-reason-modal.component';
import { CustomerService } from '../../../../core/debt/customer.service';
import { CustomerHistory, CustomerHistoryEntry, DebtAccount } from '../../../../core/debt/models/debt.models';
import { formatBs } from '../../../../shared/utils/money';
import { operationDayLabel } from '../../../../shared/utils/store-date';
import { ToastService } from '../../../../shared/components/toast/toast.service';

// DEU-06 and DEU-07: the customer's history with the receivable after each entry, collection and its void
@Component({
  selector: 'app-customer-detail-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent, VoidReasonModalComponent],
  templateUrl: './customer-detail-modal.component.html',
  styleUrls: ['./customer-detail-modal.component.css']
})
export class CustomerDetailModalComponent {
  // CAJ-11: «Día 31/10 · cargada el 01/11 00:20» for an entry that counts for another day than the one it was recorded
  readonly dayMark = operationDayLabel;

  private readonly toast = inject(ToastService);
  private readonly customerService = inject(CustomerService);

  customerId = input<string | null>(null);
  accounts = input<DebtAccount[]>([]);
  // Changes when something outside the modal moved the balance (e.g. a sale voided in the POS)
  refreshKey = input<number>(0);

  // Emits after a collection or its void, to refresh the list
  changed = output<void>();
  openSale = output<string>();
  close = output<void>();

  history = signal<CustomerHistory | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  collectionAccountId = signal<string>('');
  collectionAmount = signal<number | null>(null);
  collectionComment = signal<string>('');
  collectionError = signal<string | null>(null);
  isCollecting = signal<boolean>(false);

  voidTarget = signal<CustomerHistoryEntry | null>(null);
  voidError = signal<string | null>(null);
  isVoiding = signal<boolean>(false);

  balance = computed(() => this.history()?.customer.balance ?? 0);
  canCollect = computed(() => this.balance() > 0);
  // DEU-06: collections go only into Caja (with an open session) or the QR account
  collectionAccounts = computed(() => this.accounts().filter(a => a.cashAccount || a.qrAccount));

  voidMessage = computed(() => {
    const target = this.voidTarget();
    return target
      ? `Se anula el cobro de ${formatBs(target.amount)} a ${target.accountName}: la cuenta baja el monto y el saldo por cobrar vuelve a subir.`
      : '';
  });

  constructor() {
    effect(() => {
      const id = this.customerId();
      this.refreshKey();
      untracked(() => (id ? this.load(id) : this.history.set(null)));
    });
  }

  private load(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.collectionError.set(null);
    this.customerService.getHistory(id).subscribe({
      next: history => {
        this.setHistory(history);
        this.isLoading.set(false);
      },
      error: err => {
        this.errorMessage.set(err.error?.message || 'No se pudo cargar el historial.');
        this.isLoading.set(false);
      }
    });
  }

  private setHistory(history: CustomerHistory): void {
    this.history.set(history);
    this.collectionAccountId.set('');
    this.collectionAmount.set(history.customer.balance > 0 ? history.customer.balance : null);
    this.collectionComment.set('');
  }

  registerCollection(): void {
    const history = this.history();
    if (!history) {
      return;
    }
    if (!this.collectionAccountId()) {
      this.collectionError.set('Elija la cuenta a la que entra el cobro.');
      return;
    }
    const amount = Number(this.collectionAmount());
    if (!amount || amount <= 0) {
      this.collectionError.set('Ingrese un monto mayor a 0.');
      return;
    }
    this.isCollecting.set(true);
    this.collectionError.set(null);
    const comment = this.collectionComment().trim();
    this.customerService.registerCollection(history.customer.id, {
      accountId: this.collectionAccountId(),
      amount,
      comment: comment.length > 0 ? comment : null
    }).subscribe({
      next: updated => {
        this.toast.success('Cobro registrado.');
        this.setHistory(updated);
        this.isCollecting.set(false);
        this.changed.emit();
      },
      error: err => {
        this.collectionError.set(err.error?.message || 'No se pudo registrar el cobro.');
        this.isCollecting.set(false);
      }
    });
  }

  openVoid(entry: CustomerHistoryEntry): void {
    this.voidError.set(null);
    this.voidTarget.set(entry);
  }

  closeVoid(): void {
    if (!this.isVoiding()) {
      this.voidTarget.set(null);
    }
  }

  confirmVoid(voidReason: string): void {
    const history = this.history();
    const target = this.voidTarget();
    if (!history || !target) {
      return;
    }
    this.isVoiding.set(true);
    this.voidError.set(null);
    this.customerService.voidCollection(history.customer.id, target.id, { voidReason }).subscribe({
      next: updated => {
        this.toast.success('Cobro anulado.');
        this.setHistory(updated);
        this.isVoiding.set(false);
        this.voidTarget.set(null);
        this.changed.emit();
      },
      error: err => {
        this.voidError.set(err.error?.message || 'No se pudo anular el cobro.');
        this.isVoiding.set(false);
      }
    });
  }

  onClose(): void {
    if (!this.isCollecting() && !this.isVoiding()) {
      this.close.emit();
    }
  }
}
