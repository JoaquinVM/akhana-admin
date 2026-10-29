import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FinancialCategoryService } from '../../../../core/finance/financial-category.service';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';
import { FinancialCategory } from '../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashLimitWarningComponent } from '../../../../shared/components/cash-limit-warning/cash-limit-warning.component';
import { formatBs } from '../../../../shared/utils/money';

/**
 * Cash expense of the shift (food, transport…): leaves Caja with an expense category. It only offers the
 * categories that take manual movements: Pedido, Pago a tercero and Ajuste are recorded by the system.
 */
@Component({
  selector: 'app-cash-expense-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent, CashLimitWarningComponent],
  templateUrl: './cash-expense-modal.component.html',
  styleUrls: ['./cash-expense-modal.component.css']
})
export class CashExpenseModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly categoryService = inject(FinancialCategoryService);
  private readonly transactionService = inject(FinancialTransactionService);

  readonly isOpen = input<boolean>(false);

  readonly close = output<void>();
  /** Emits the success message after registering the expense. */
  readonly saved = output<string>();

  private wasOpen = false;

  constructor() {
    // The categories and the form reload each time the modal opens, not while it stays open
    effect(() => {
      const open = this.isOpen();
      if (open && !this.wasOpen) {
        untracked(() => this.load());
      }
      this.wasOpen = open;
    });
  }

  categories = signal<FinancialCategory[]>([]);
  error = signal<string | null>(null);
  isLoading = signal<boolean>(false);

  form = this.fb.group({
    categoryId: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    description: ['', [Validators.required, Validators.maxLength(255)]],
    notes: ['']
  });

  readonly amount = toSignal(this.form.controls.amount.valueChanges, { initialValue: null });

  private load(): void {
    this.error.set(null);
    // No category preselected: whoever registers picks it (the first one, «Ajuste», used to be proposed)
    this.form.reset({ categoryId: '', amount: null, description: '', notes: '' });
    this.categoryService.getCategories('GASTO').subscribe({
      next: (cats) => this.categories.set(cats.filter((c) => c.manualEntryAllowed)),
      error: () => this.error.set('Error al cargar categorías de gasto.')
    });
  }

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    const val = this.form.getRawValue();
    this.transactionService.createCashExpense({
      categoryId: val.categoryId!,
      amount: Number(val.amount),
      description: (val.description || '').trim(),
      notes: val.notes ? val.notes.trim() : undefined
    }).subscribe({
      next: (tx) => {
        this.isLoading.set(false);
        this.saved.emit(`Gasto de caja de ${formatBs(tx.amount)} registrado exitosamente.`);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.error.set(err.error?.message || 'Error al registrar el gasto de caja.');
      }
    });
  }
}
