import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { VoidReasonModalComponent } from '../../../purchases/components/void-reason-modal/void-reason-modal.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CreditorHistory, CreditorHistoryEntry, CreditorRepaymentConcept, DebtAccount } from '../../../../core/debt/models/debt.models';

// DEU-03 y DEU-04: historial de la persona con el saldo tras cada registro, devolución y su anulación
@Component({
  selector: 'app-creditor-detail-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent, VoidReasonModalComponent],
  templateUrl: './creditor-detail-modal.component.html',
  styleUrls: ['./creditor-detail-modal.component.css']
})
export class CreditorDetailModalComponent {
  private readonly creditorService = inject(CreditorService);

  creditorId = input<string | null>(null);
  accounts = input<DebtAccount[]>([]);
  // Cambia cuando algo fuera del modal movió la deuda (p. ej. un pago anulado en el pedido)
  refreshKey = input<number>(0);

  // Emite tras una devolución o su anulación, para refrescar el listado
  changed = output<void>();
  openPurchase = output<string>();
  // POS-13: ventas con productos de la persona
  openSale = output<string>();
  close = output<void>();

  history = signal<CreditorHistory | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  repaymentAccountId = signal<string>('');
  repaymentAmount = signal<number | null>(null);
  repaymentComment = signal<string>('');
  repaymentConcept = signal<CreditorRepaymentConcept>('PURCHASE');
  repaymentError = signal<string | null>(null);
  isRepaying = signal<boolean>(false);

  voidTarget = signal<CreditorHistoryEntry | null>(null);
  voidError = signal<string | null>(null);
  isVoiding = signal<boolean>(false);

  debt = computed(() => this.history()?.creditor.debt ?? 0);
  canRepay = computed(() => this.debt() > 0);

  voidMessage = computed(() => {
    const target = this.voidTarget();
    return target
      ? `Se anula la devolución de Bs ${target.amount.toFixed(2)} desde ${target.accountName}: la cuenta recupera el monto y la deuda vuelve a subir.`
      : '';
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
    this.repaymentAccountId.set('');
    this.repaymentAmount.set(history.creditor.debt > 0 ? history.creditor.debt : null);
    this.repaymentComment.set('');
    // POS-13: si solo vende productos suyos, se propone «Pago a tercero»
    const hasSales = history.entries.some(e => e.type === 'SALE');
    const hasPayments = history.entries.some(e => e.type === 'PAYMENT');
    this.repaymentConcept.set(hasSales && !hasPayments ? 'THIRD_PARTY' : 'PURCHASE');
  }

  registerRepayment(): void {
    const history = this.history();
    if (!history) {
      return;
    }
    if (!this.repaymentAccountId()) {
      this.repaymentError.set('Elija la cuenta desde la que se devuelve.');
      return;
    }
    const amount = Number(this.repaymentAmount());
    if (!amount || amount <= 0) {
      this.repaymentError.set('Ingrese un monto mayor a 0.');
      return;
    }
    this.isRepaying.set(true);
    this.repaymentError.set(null);
    const comment = this.repaymentComment().trim();
    this.creditorService.registerRepayment(history.creditor.id, {
      accountId: this.repaymentAccountId(),
      amount,
      comment: comment.length > 0 ? comment : null,
      concept: this.repaymentConcept()
    }).subscribe({
      next: updated => {
        this.setHistory(updated);
        this.isRepaying.set(false);
        this.changed.emit();
      },
      error: err => {
        this.repaymentError.set(err.error?.message || 'No se pudo registrar la devolución.');
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
