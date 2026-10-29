import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FinancialAccount, FinancialAccountRequest, FinancialTransaction } from '../../../core/finance/models/finance.models';
import { FinancialAccountService } from '../../../core/finance/financial-account.service';
import { FinancialTransactionService } from '../../../core/finance/financial-transaction.service';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ModalComponent,
    ConfirmModalComponent,
    ColorPickerComponent
  ],
  templateUrl: './accounts.component.html',
  styleUrls: ['./accounts.component.css']
})
export class AccountsComponent implements OnInit {
  private readonly toast = inject(ToastService);
  private readonly accountService = inject(FinancialAccountService);
  private readonly transactionService = inject(FinancialTransactionService);
  private readonly fb = inject(FormBuilder);

  accounts = signal<FinancialAccount[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Form modal (create / edit)
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedAccountId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  // Modal Eliminar
  isDeleteModalOpen = signal<boolean>(false);
  accountToDelete = signal<FinancialAccount | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Movimientos
  isMovementsModalOpen = signal<boolean>(false);
  selectedAccountForMovements = signal<FinancialAccount | null>(null);
  accountTransactions = signal<FinancialTransaction[]>([]);
  isLoadingMovements = signal<boolean>(false);

  // Cutover balance modal (FIN-01)
  isOpeningBalanceModalOpen = signal<boolean>(false);
  accountForOpeningBalance = signal<FinancialAccount | null>(null);
  openingBalanceError = signal<string | null>(null);
  isSavingOpeningBalance = signal<boolean>(false);
  openingBalanceForm: FormGroup = this.fb.group({
    amount: [null as number | null, [Validators.required, Validators.min(0)]]
  });

  get openingBalanceControl() {
    return this.openingBalanceForm.controls['amount'];
  }

  accountForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    color: ['#2e5b27', [Validators.required]],
    initialBalance: [0, [Validators.min(0)]],
    isQrAccount: [false]
  });

  // Total balance of every active account
  totalBalance = computed(() => {
    return this.accounts().reduce((sum, acc) => sum + (acc.currentBalance || 0), 0);
  });

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.accountService.getAccounts().subscribe({
      next: (data) => {
        this.accounts.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message || 'Error al cargar las cuentas financieras.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedAccountId.set(null);
    this.formError.set(null);
    this.accountForm.reset({
      name: '',
      color: '#2e5b27',
      initialBalance: 0,
      isQrAccount: false
    });
    this.accountForm.get('initialBalance')?.enable();
    this.isFormModalOpen.set(true);
  }

  openEditModal(account: FinancialAccount): void {
    this.isEditing.set(true);
    this.selectedAccountId.set(account.id);
    this.formError.set(null);
    this.accountForm.reset({
      name: account.name,
      color: account.color || '#2e5b27',
      initialBalance: account.initialBalance,
      isQrAccount: account.isQrAccount
    });
    this.accountForm.get('initialBalance')?.disable();
    this.isFormModalOpen.set(true);
  }

  closeFormModal(): void {
    this.isFormModalOpen.set(false);
    this.formError.set(null);
  }

  saveAccount(): void {
    if (this.accountForm.invalid) {
      this.accountForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const val = this.accountForm.getRawValue();
    const req: FinancialAccountRequest = {
      name: val.name.trim(),
      color: val.color,
      initialBalance: val.initialBalance,
      isQrAccount: val.isQrAccount
    };

    if (this.isEditing() && this.selectedAccountId()) {
      this.accountService.updateAccount(this.selectedAccountId()!, req).subscribe({
        next: () => {
          this.toast.success('Cuenta actualizada.');
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.loadAccounts();
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al actualizar la cuenta.');
        }
      });
    } else {
      this.accountService.createAccount(req).subscribe({
        next: () => {
          this.toast.success('Cuenta creada.');
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.loadAccounts();
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al crear la cuenta.');
        }
      });
    }
  }

  canDelete(account: FinancialAccount): boolean {
    if (account.isCashAccount) return false;
    if (account.isQrAccount) return false;
    return account.currentBalance === 0;
  }

  getDeleteTooltip(account: FinancialAccount): string {
    if (account.isCashAccount) return 'La cuenta Caja está protegida y no puede eliminarse.';
    if (account.isQrAccount) return 'Esta cuenta está configurada como la cuenta QR activa y no puede eliminarse.';
    if (account.currentBalance !== 0) return 'Solo se pueden eliminar cuentas con saldo 0.00.';
    return 'Eliminar cuenta';
  }

  openDeleteModal(account: FinancialAccount): void {
    if (!this.canDelete(account)) return;
    this.accountToDelete.set(account);
    this.isDeleteModalOpen.set(true);
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen.set(false);
    this.accountToDelete.set(null);
  }

  confirmDelete(): void {
    const acc = this.accountToDelete();
    if (!acc) return;

    this.isDeleting.set(true);
    this.accountService.deleteAccount(acc.id).subscribe({
      next: () => {
        this.toast.success('Cuenta eliminada.');
        this.isDeleting.set(false);
        this.closeDeleteModal();
        this.loadAccounts();
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar la cuenta.');
        this.closeDeleteModal();
      }
    });
  }

  openOpeningBalanceModal(account: FinancialAccount): void {
    this.accountForOpeningBalance.set(account);
    this.openingBalanceError.set(null);
    this.openingBalanceControl.reset(account.initialBalance);
    this.isOpeningBalanceModalOpen.set(true);
  }

  closeOpeningBalanceModal(): void {
    this.isOpeningBalanceModalOpen.set(false);
    this.accountForOpeningBalance.set(null);
  }

  saveOpeningBalance(): void {
    const acc = this.accountForOpeningBalance();
    if (!acc) return;
    if (this.openingBalanceControl.invalid) {
      this.openingBalanceControl.markAsTouched();
      return;
    }

    this.isSavingOpeningBalance.set(true);
    this.openingBalanceError.set(null);
    this.accountService.setOpeningBalance(acc.id, this.openingBalanceControl.value!).subscribe({
      next: () => {
        this.toast.success('Saldo al corte guardado.');
        this.isSavingOpeningBalance.set(false);
        this.closeOpeningBalanceModal();
        this.loadAccounts();
      },
      error: (err) => {
        this.isSavingOpeningBalance.set(false);
        this.openingBalanceError.set(err.error?.message || 'Error al guardar el saldo al corte.');
      }
    });
  }

  openMovementsModal(account: FinancialAccount): void {
    this.selectedAccountForMovements.set(account);
    this.isMovementsModalOpen.set(true);
    this.isLoadingMovements.set(true);
    this.accountTransactions.set([]);

    this.transactionService.getTransactions({ accountId: account.id }).subscribe({
      next: (txs) => {
        this.accountTransactions.set(txs);
        this.isLoadingMovements.set(false);
      },
      error: () => {
        this.isLoadingMovements.set(false);
      }
    });
  }

  closeMovementsModal(): void {
    this.isMovementsModalOpen.set(false);
    this.selectedAccountForMovements.set(null);
    this.accountTransactions.set([]);
  }
}
