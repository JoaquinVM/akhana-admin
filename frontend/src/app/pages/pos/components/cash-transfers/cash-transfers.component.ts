import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';
import {
  CashTransferAccount,
  CashTransferDirection,
  FinancialTransaction
} from '../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashLimitWarningComponent } from '../../../../shared/components/cash-limit-warning/cash-limit-warning.component';

/**
 * Ingresos y retiros de caja del turno (CAJ-08): cada uno es una transferencia entre Caja y otra cuenta.
 * Lista los de la caja abierta, los registra y los anula; avisa al POS para refrescar la caja.
 */
@Component({
  selector: 'app-cash-transfers',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ModalComponent, CashLimitWarningComponent],
  templateUrl: './cash-transfers.component.html',
  styleUrls: ['./cash-transfers.component.css']
})
export class CashTransfersComponent {
  private readonly transactionService = inject(FinancialTransactionService);
  private readonly fb = inject(FormBuilder);

  private readonly transfersSignal = signal<FinancialTransaction[]>([]);

  @Input() set transfers(value: FinancialTransaction[] | null) {
    this.transfersSignal.set(value || []);
  }
  @Input() canOperate = false;

  /** Emite el mensaje de éxito después de registrar o anular. */
  @Output() changed = new EventEmitter<string>();

  readonly items = this.transfersSignal.asReadonly();
  readonly activeItems = computed(() => this.items().filter((t) => t.status === 'ACTIVO'));
  readonly totalIncome = computed(() =>
    this.activeItems().filter((t) => this.directionOf(t) === 'INGRESO').reduce((sum, t) => sum + t.amount, 0)
  );
  readonly totalWithdrawals = computed(() =>
    this.activeItems().filter((t) => this.directionOf(t) === 'RETIRO').reduce((sum, t) => sum + t.amount, 0)
  );

  accounts = signal<CashTransferAccount[]>([]);
  isLoading = signal<boolean>(false);

  isFormVisible = signal<boolean>(false);
  formDirection = signal<CashTransferDirection>('RETIRO');
  formError = signal<string | null>(null);

  form = this.fb.group({
    accountId: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    comment: ['', [Validators.required, Validators.maxLength(500)]]
  });

  transferToVoid = signal<FinancialTransaction | null>(null);
  voidReason = signal<string>('');
  voidError = signal<string | null>(null);

  /** Un ingreso entra a Caja; un retiro sale de Caja. */
  directionOf(tx: FinancialTransaction): CashTransferDirection {
    return tx.destinationAccount?.cashAccount ? 'INGRESO' : 'RETIRO';
  }

  /** La cuenta que no es Caja. */
  otherAccountOf(tx: FinancialTransaction): string {
    const other = this.directionOf(tx) === 'INGRESO' ? tx.sourceAccount : tx.destinationAccount;
    return other?.name || '-';
  }

  openForm(direction: CashTransferDirection): void {
    if (!this.canOperate) return;
    this.formDirection.set(direction);
    this.formError.set(null);
    this.transactionService.getCashTransferAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.form.reset({ accountId: accounts[0]?.id || '', amount: null, comment: '' });
        this.isFormVisible.set(true);
      },
      error: (err) => {
        this.formError.set(err.error?.message || 'Error al cargar las cuentas.');
        this.isFormVisible.set(true);
      }
    });
  }

  closeForm(): void {
    this.isFormVisible.set(false);
    this.formError.set(null);
  }

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.value;
    this.isLoading.set(true);
    this.formError.set(null);
    this.transactionService.createCashTransfer({
      direction: this.formDirection(),
      accountId: val.accountId!,
      amount: Number(val.amount),
      comment: (val.comment || '').trim()
    }).subscribe({
      next: (tx) => {
        this.isLoading.set(false);
        this.closeForm();
        const label = this.formDirection() === 'INGRESO' ? 'Ingreso' : 'Retiro';
        this.changed.emit(`${label} de caja de Bs. ${tx.amount.toFixed(2)} registrado exitosamente.`);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.formError.set(err.error?.message || 'Error al registrar el movimiento de caja.');
      }
    });
  }

  openVoid(tx: FinancialTransaction): void {
    if (tx.status === 'ANULADO' || !this.canOperate) return;
    this.transferToVoid.set(tx);
    this.voidReason.set('');
    this.voidError.set(null);
  }

  closeVoid(): void {
    this.transferToVoid.set(null);
    this.voidError.set(null);
  }

  confirmVoid(): void {
    const tx = this.transferToVoid();
    if (!tx || this.isLoading()) return;
    this.isLoading.set(true);
    this.voidError.set(null);
    this.transactionService.voidCashTransfer(tx.id, { voidReason: this.voidReason().trim() }).subscribe({
      next: (updated) => {
        this.isLoading.set(false);
        this.closeVoid();
        this.changed.emit(`Movimiento de caja de Bs. ${updated.amount.toFixed(2)} anulado exitosamente.`);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.voidError.set(err.error?.message || 'Error al anular el movimiento de caja.');
      }
    });
  }
}
