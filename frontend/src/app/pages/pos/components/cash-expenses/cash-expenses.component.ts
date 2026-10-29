import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';
import { FinancialTransaction } from '../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { formatBs } from '../../../../shared/utils/money';

/**
 * Cash expenses of the shift (food, transport…): lists those of the open session and voids them with a reason
 * (RG-03). Registering one is the POS's cash expense modal; this component asks for it with registerExpense.
 */
@Component({
  selector: 'app-cash-expenses',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './cash-expenses.component.html',
  styleUrls: ['./cash-expenses.component.css']
})
export class CashExpensesComponent {
  private readonly transactionService = inject(FinancialTransactionService);

  readonly expenses = input<FinancialTransaction[] | null>(null);
  readonly canOperate = input<boolean>(false);

  readonly registerExpense = output<void>();
  /** Emits the success message after a void. */
  readonly changed = output<string>();

  readonly items = computed(() => this.expenses() ?? []);

  readonly voidTarget = signal<FinancialTransaction | null>(null);
  readonly voidReason = signal<string>('');
  readonly voidError = signal<string | null>(null);
  readonly isVoiding = signal<boolean>(false);

  openVoidModal(tx: FinancialTransaction): void {
    if (tx.status === 'ANULADO' || !this.canOperate()) return;
    this.voidTarget.set(tx);
    this.voidReason.set('');
    this.voidError.set(null);
  }

  closeVoidModal(): void {
    this.voidTarget.set(null);
    this.voidReason.set('');
    this.voidError.set(null);
  }

  confirmVoid(): void {
    const tx = this.voidTarget();
    if (!tx || this.isVoiding()) return;

    this.isVoiding.set(true);
    this.voidError.set(null);
    this.transactionService.voidCashExpense(tx.id, { voidReason: this.voidReason().trim() }).subscribe({
      next: (updated) => {
        this.isVoiding.set(false);
        this.closeVoidModal();
        this.changed.emit(`Gasto de ${formatBs(updated.amount)} anulado exitosamente.`);
      },
      error: (err: any) => {
        this.isVoiding.set(false);
        this.voidError.set(err.error?.message || 'Error al anular el gasto.');
      }
    });
  }
}
