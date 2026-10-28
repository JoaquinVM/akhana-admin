import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import {
  FinancialAccount,
  FinancialCategory,
  FinancialTransaction,
  FinancialTransactionType,
  FinancialTransactionStatus,
  TransactionFilterParams,
  FinancialTransactionRequest,
  VoidFinancialTransactionRequest
} from '../../../core/finance/models/finance.models';
import { FinancialAccountService } from '../../../core/finance/financial-account.service';
import { FinancialCategoryService } from '../../../core/finance/financial-category.service';
import { FinancialTransactionService } from '../../../core/finance/financial-transaction.service';
import { AuditModalComponent } from '../../../shared/components/audit-modal/audit-modal.component';
import { SaleDetailModalComponent } from '../../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { TransactionsFiltersComponent } from './components/transactions-filters/transactions-filters.component';
import { TransactionsTableComponent } from './components/transactions-table/transactions-table.component';
import { TransactionFormModalComponent } from './components/transaction-form-modal/transaction-form-modal.component';
import { TransactionEditModalComponent } from './components/transaction-edit-modal/transaction-edit-modal.component';
import { TransactionVoidModalComponent } from './components/transaction-void-modal/transaction-void-modal.component';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [
    CommonModule,
    AuditModalComponent,
    SaleDetailModalComponent,
    TransactionsFiltersComponent,
    TransactionsTableComponent,
    TransactionFormModalComponent,
    TransactionEditModalComponent,
    TransactionVoidModalComponent
  ],
  templateUrl: './transactions.component.html',
  styleUrls: ['./transactions.component.css']
})
export class TransactionsComponent implements OnInit {
  private readonly transactionService = inject(FinancialTransactionService);
  private readonly accountService = inject(FinancialAccountService);
  private readonly categoryService = inject(FinancialCategoryService);
  private readonly fb = inject(FormBuilder);

  transactions = signal<FinancialTransaction[]>([]);
  accounts = signal<FinancialAccount[]>([]);
  categories = signal<FinancialCategory[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Filtros reactivos
  startDate = signal<string>('');
  endDate = signal<string>('');
  selectedType = signal<FinancialTransactionType | ''>('');
  selectedAccountId = signal<string>('');
  selectedCategoryId = signal<string>('');
  selectedStatus = signal<FinancialTransactionStatus | ''>('');
  searchTerm = signal<string>('');

  // Modal Ingreso / Gasto / Transferencia
  activeModalType = signal<'INGRESO' | 'GASTO' | 'TRANSFERENCIA' | 'EDIT' | null>(null);
  selectedTransactionForEdit = signal<FinancialTransaction | null>(null);
  modalFormError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  // Modal Anulación
  isVoidModalOpen = signal<boolean>(false);
  transactionToVoid = signal<FinancialTransaction | null>(null);
  voidReason = signal<string>('');
  isVoiding = signal<boolean>(false);
  voidError = signal<string | null>(null);

  // Modal Detalle Venta
  isSaleDetailOpen = signal<boolean>(false);
  selectedSaleId = signal<string | null>(null);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  // Formularios
  incomeForm: FormGroup = this.fb.group({
    accountId: ['', Validators.required],
    categoryId: ['', Validators.required],
    amount: [null, [Validators.required, Validators.min(0.01)]],
    description: ['', [Validators.required, Validators.maxLength(255)]],
    notes: ['']
  });

  expenseForm: FormGroup = this.fb.group({
    accountId: ['', Validators.required],
    categoryId: ['', Validators.required],
    amount: [null, [Validators.required, Validators.min(0.01)]],
    description: ['', [Validators.required, Validators.maxLength(255)]],
    notes: ['']
  });

  transferForm: FormGroup = this.fb.group({
    sourceAccountId: ['', Validators.required],
    destinationAccountId: ['', Validators.required],
    amount: [null, [Validators.required, Validators.min(0.01)]],
    description: ['Transferencia entre cuentas', [Validators.required, Validators.maxLength(255)]],
    notes: ['']
  });

  editForm: FormGroup = this.fb.group({
    description: ['', [Validators.required, Validators.maxLength(255)]],
    notes: [''],
    categoryId: ['']
  });

  // Cuentas disponibles para ingreso manual (excluye Caja física)
  incomeEligibleAccounts = computed(() => {
    return this.accounts().filter(acc => !acc.isCashAccount);
  });

  // Categorías de ingreso y gasto
  incomeCategories = computed(() => {
    return this.categories().filter(c => c.type === 'INGRESO');
  });

  expenseCategories = computed(() => {
    return this.categories().filter(c => c.type === 'GASTO');
  });

  ngOnInit(): void {
    this.setDefaultDateRange();
    this.loadMetadata();
    this.loadTransactions();
  }

  setDefaultDateRange(): void {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    // Primer día del mes actual (YYYY-MM-01)
    this.startDate.set(`${year}-${month}-01`);
    // Día actual (YYYY-MM-DD)
    this.endDate.set(`${year}-${month}-${day}`);
  }

  loadMetadata(): void {
    this.accountService.getAccounts().subscribe({
      next: (data) => this.accounts.set(data),
      error: (err) => console.error('Error cargando cuentas:', err)
    });

    this.categoryService.getCategories().subscribe({
      next: (data) => this.categories.set(data),
      error: (err) => console.error('Error cargando categorías:', err)
    });
  }

  loadTransactions(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const params: TransactionFilterParams = {};
    if (this.startDate()) {
      const startIso = this.formatStartOfDayIso(this.startDate());
      if (startIso) {
        params.startDate = startIso;
      }
    }
    if (this.endDate()) {
      const endIso = this.formatEndOfDayIso(this.endDate());
      if (endIso) {
        params.endDate = endIso;
      }
    }
    if (this.selectedType()) {
      params.type = this.selectedType() as FinancialTransactionType;
    }
    if (this.selectedAccountId()) {
      params.accountId = this.selectedAccountId();
    }
    if (this.selectedCategoryId()) {
      params.categoryId = this.selectedCategoryId();
    }
    if (this.selectedStatus()) {
      params.status = this.selectedStatus() as FinancialTransactionStatus;
    }
    if (this.searchTerm().trim()) {
      params.search = this.searchTerm().trim();
    }

    this.transactionService.getTransactions(params).subscribe({
      next: (data) => {
        this.transactions.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message || 'Error al cargar las transacciones financieras.');
        this.isLoading.set(false);
      }
    });
  }

  private formatStartOfDayIso(dateStr: string): string | undefined {
    if (!dateStr) return undefined;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return undefined;
    const year = Number(parts[0]);
    const month = Number(parts[1]);
    const day = Number(parts[2]);
    if (!year || !month || !day) return undefined;
    // Inicio del día local: 00:00:00.000 convertido a UTC Instant ISO
    const start = new Date(year, month - 1, day, 0, 0, 0, 0);
    return isNaN(start.getTime()) ? undefined : start.toISOString();
  }

  private formatEndOfDayIso(dateStr: string): string | undefined {
    if (!dateStr) return undefined;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return undefined;
    const year = Number(parts[0]);
    const month = Number(parts[1]);
    const day = Number(parts[2]);
    if (!year || !month || !day) return undefined;
    // Final del día local: 23:59:59.999 convertido a UTC Instant ISO
    const end = new Date(year, month - 1, day, 23, 59, 59, 999);
    return isNaN(end.getTime()) ? undefined : end.toISOString();
  }

  onFilterChange(): void {
    this.loadTransactions();
  }

  resetFilters(): void {
    this.setDefaultDateRange();
    this.selectedType.set('');
    this.selectedAccountId.set('');
    this.selectedCategoryId.set('');
    this.selectedStatus.set('');
    this.searchTerm.set('');
    this.loadTransactions();
  }

  // --- Apertura de Modales ---

  openIncomeModal(): void {
    this.modalFormError.set(null);
    this.incomeForm.reset({
      accountId: this.incomeEligibleAccounts()[0]?.id || '',
      categoryId: this.incomeCategories()[0]?.id || '',
      amount: null,
      description: '',
      notes: ''
    });
    this.activeModalType.set('INGRESO');
  }

  openExpenseModal(): void {
    this.modalFormError.set(null);
    this.expenseForm.reset({
      accountId: this.accounts()[0]?.id || '',
      categoryId: this.expenseCategories()[0]?.id || '',
      amount: null,
      description: '',
      notes: ''
    });
    this.activeModalType.set('GASTO');
  }

  openTransferModal(): void {
    this.modalFormError.set(null);
    const accs = this.accounts();
    this.transferForm.reset({
      sourceAccountId: accs[0]?.id || '',
      destinationAccountId: accs[1]?.id || '',
      amount: null,
      description: 'Transferencia entre cuentas',
      notes: ''
    });
    this.activeModalType.set('TRANSFERENCIA');
  }

  openEditModal(tx: FinancialTransaction): void {
    if (tx.historical) return;
    this.modalFormError.set(null);
    this.selectedTransactionForEdit.set(tx);
    this.editForm.reset({
      description: tx.description,
      notes: tx.notes || '',
      categoryId: tx.category?.id || ''
    });
    this.activeModalType.set('EDIT');
  }

  closeActionModal(): void {
    this.activeModalType.set(null);
    this.selectedTransactionForEdit.set(null);
    this.modalFormError.set(null);
  }

  submitIncome(): void {
    if (this.incomeForm.invalid) {
      this.incomeForm.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.modalFormError.set(null);

    const val = this.incomeForm.value;
    const req: FinancialTransactionRequest = {
      type: 'INGRESO_MANUAL',
      sourceAccountId: val.accountId,
      categoryId: val.categoryId,
      amount: Number(val.amount),
      description: val.description.trim(),
      notes: val.notes ? val.notes.trim() : undefined
    };

    this.transactionService.createTransaction(req).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.closeActionModal();
        this.loadTransactions();
        this.loadMetadata();
      },
      error: (err: any) => {
        this.isSubmitting.set(false);
        this.modalFormError.set(err.error?.message || 'Error al registrar el ingreso.');
      }
    });
  }

  submitExpense(): void {
    if (this.expenseForm.invalid) {
      this.expenseForm.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.modalFormError.set(null);

    const val = this.expenseForm.value;
    const req: FinancialTransactionRequest = {
      type: 'GASTO_MANUAL',
      sourceAccountId: val.accountId,
      categoryId: val.categoryId,
      amount: Number(val.amount),
      description: val.description.trim(),
      notes: val.notes ? val.notes.trim() : undefined
    };

    this.transactionService.createTransaction(req).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.closeActionModal();
        this.loadTransactions();
        this.loadMetadata();
      },
      error: (err: any) => {
        this.isSubmitting.set(false);
        this.modalFormError.set(err.error?.message || 'Error al registrar el gasto.');
      }
    });
  }

  submitTransfer(): void {
    if (this.transferForm.invalid) {
      this.transferForm.markAllAsTouched();
      return;
    }

    const val = this.transferForm.value;
    if (val.sourceAccountId === val.destinationAccountId) {
      this.modalFormError.set('La cuenta origen y destino deben ser diferentes.');
      return;
    }

    this.isSubmitting.set(true);
    this.modalFormError.set(null);

    const req: FinancialTransactionRequest = {
      type: 'TRANSFERENCIA',
      sourceAccountId: val.sourceAccountId,
      destinationAccountId: val.destinationAccountId,
      amount: Number(val.amount),
      description: val.description.trim(),
      notes: val.notes ? val.notes.trim() : undefined
    };

    this.transactionService.createTransaction(req).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.closeActionModal();
        this.loadTransactions();
        this.loadMetadata();
      },
      error: (err: any) => {
        this.isSubmitting.set(false);
        this.modalFormError.set(err.error?.message || 'Error al registrar la transferencia.');
      }
    });
  }

  submitEdit(): void {
    const tx = this.selectedTransactionForEdit();
    if (!tx) return;

    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.modalFormError.set(null);

    const val = this.editForm.value;
    const req: FinancialTransactionRequest = {
      type: tx.type,
      sourceAccountId: tx.sourceAccount.id,
      destinationAccountId: tx.destinationAccount?.id,
      amount: tx.amount,
      description: val.description.trim(),
      notes: val.notes ? val.notes.trim() : undefined,
      categoryId: val.categoryId || undefined
    };

    this.transactionService.updateTransaction(tx.id, req).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.closeActionModal();
        this.loadTransactions();
      },
      error: (err: any) => {
        this.isSubmitting.set(false);
        this.modalFormError.set(err.error?.message || 'Error al actualizar la transacción.');
      }
    });
  }

  // --- Anulación ---

  openVoidModal(tx: FinancialTransaction): void {
    if (tx.status === 'ANULADO' || tx.saleId || tx.historical) return;
    this.transactionToVoid.set(tx);
    this.voidReason.set('');
    this.voidError.set(null);
    this.isVoidModalOpen.set(true);
  }

  closeVoidModal(): void {
    this.isVoidModalOpen.set(false);
    this.transactionToVoid.set(null);
    this.voidReason.set('');
    this.voidError.set(null);
  }

  confirmVoid(): void {
    const tx = this.transactionToVoid();
    if (!tx) return;

    this.isVoiding.set(true);
    this.voidError.set(null);

    const req: VoidFinancialTransactionRequest = {
      voidReason: this.voidReason().trim()
    };

    this.transactionService.voidTransaction(tx.id, req).subscribe({
      next: () => {
        this.isVoiding.set(false);
        this.closeVoidModal();
        this.loadTransactions();
        this.loadMetadata();
      },
      error: (err) => {
        this.isVoiding.set(false);
        this.voidError.set(err.error?.message || 'Error al anular la transacción.');
      }
    });
  }

  // --- Detalle de Venta ---

  openSaleDetail(saleId: string): void {
    this.selectedSaleId.set(saleId);
    this.isSaleDetailOpen.set(true);
  }

  closeSaleDetail(): void {
    this.isSaleDetailOpen.set(false);
    this.selectedSaleId.set(null);
  }

  // --- Auditoría ---

  openAuditModal(tx: FinancialTransaction): void {
    this.auditModalSubtitle.set(`${tx.type} - ${tx.description}`);
    this.selectedAuditData.set({
      createdBy: tx.createdBy,
      createdAt: tx.createdAt,
      updatedBy: tx.updatedBy,
      updatedAt: tx.updatedAt,
      voidedBy: tx.voidedBy,
      voidedAt: tx.voidedAt,
      voidReason: tx.voidReason
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }
}
