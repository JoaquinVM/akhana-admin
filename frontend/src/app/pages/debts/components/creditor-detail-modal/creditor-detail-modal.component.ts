import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CreditorRepaymentFormComponent } from '../creditor-repayment-form/creditor-repayment-form.component';
import { VoidReasonModalComponent } from '../../../purchases/components/void-reason-modal/void-reason-modal.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CreditorHistory, CreditorHistoryEntry, CreditorRepaymentRequest, DebtAccount } from '../../../../core/debt/models/debt.models';
import { formatBs } from '../../../../shared/utils/money';
import { operationDayLabel } from '../../../../shared/utils/store-date';
import { ToastService } from '../../../../shared/components/toast/toast.service';

// DEU-03 and DEU-04: the person's history with the balance after each entry and what is paid or pending of each debt;
// paying the ticked debts (062) and voiding a repayment
@Component({
  selector: 'app-creditor-detail-modal',
  standalone: true,
  imports: [CommonModule, ModalComponent, VoidReasonModalComponent, CreditorRepaymentFormComponent],
  templateUrl: './creditor-detail-modal.component.html',
  styleUrls: ['./creditor-detail-modal.component.css']
})
export class CreditorDetailModalComponent {
  // CAJ-11: «Día 31/10 · cargada el 01/11 00:20» for an entry that counts for another day than the one it was recorded
  readonly dayMark = operationDayLabel;

  private readonly toast = inject(ToastService);
  private readonly creditorService = inject(CreditorService);

  creditorId = input<string | null>(null);
  accounts = input<DebtAccount[]>([]);
  // Changes when something outside the modal moved the debt (e.g. a payment voided in the purchase)
  refreshKey = input<number>(0);

  // Emits after a repayment or its void, to refresh the list
  changed = output<void>();
  openPurchase = output<string>();
  // POS-13: sales with the person's products
  openSale = output<string>();
  close = output<void>();

  history = signal<CreditorHistory | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  repaymentError = signal<string | null>(null);
  isRepaying = signal<boolean>(false);

  voidTarget = signal<CreditorHistoryEntry | null>(null);
  voidError = signal<string | null>(null);
  isVoiding = signal<boolean>(false);

  debt = computed(() => this.history()?.creditor.debt ?? 0);
  canRepay = computed(() => this.debt() > 0);

  voidMessage = computed(() => {
    const target = this.voidTarget();
    if (!target) {
      return '';
    }
    const paid = target.paidItems?.length ? ` Vuelven a quedar pendientes: ${target.paidItems.join(', ')}.` : '';
    return `Se anula la devolución de ${formatBs(target.amount)} desde ${target.accountName}: la cuenta recupera el monto y la deuda vuelve a subir.${paid}`;
  });

  constructor() {
    effect(() => {
      const id = this.creditorId();
      this.refreshKey();
      untracked(() => (id ? this.load(id) : this.history.set(null)));
    });
  }

  private load(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.repaymentError.set(null);
    this.creditorService.getHistory(id).subscribe({
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

  private setHistory(history: CreditorHistory): void {
    this.history.set(history);
  }

  // DEU-03 (062): the form sends the ticked debts with the total it showed
  registerRepayment(request: CreditorRepaymentRequest): void {
    const history = this.history();
    if (!history) {
      return;
    }
    this.isRepaying.set(true);
    this.repaymentError.set(null);
    this.creditorService.registerRepayment(history.creditor.id, request).subscribe({
      next: updated => {
        this.toast.success('Pago registrado.');
        this.setHistory(updated);
        this.isRepaying.set(false);
        this.changed.emit();
      },
      error: err => {
        this.repaymentError.set(err.error?.message || 'No se pudo registrar el pago.');
        this.isRepaying.set(false);
      }
    });
  }

  openVoid(entry: CreditorHistoryEntry): void {
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
    this.creditorService.voidRepayment(history.creditor.id, target.id, { voidReason }).subscribe({
      next: updated => {
        this.toast.success('Devolución anulada.');
        this.setHistory(updated);
        this.isVoiding.set(false);
        this.voidTarget.set(null);
        this.changed.emit();
      },
      error: err => {
        this.voidError.set(err.error?.message || 'No se pudo anular la devolución.');
        this.isVoiding.set(false);
      }
    });
  }

  onClose(): void {
    if (!this.isRepaying() && !this.isVoiding()) {
      this.close.emit();
    }
  }
}
