import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FinancialAccount, FinancialCategory } from '../../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../../shared/components/modal/modal.component';
import { CashLimitWarningComponent } from '../../../../../shared/components/cash-limit-warning/cash-limit-warning.component';

export type TransactionFormMode = 'INGRESO' | 'GASTO' | 'TRANSFERENCIA';

interface TransactionFormTexts {
  title: string;
  subtitle: string;
  idPrefix: string;
  submitLabel: string;
  accountLabel: string;
  balanceLabel: string;
  accountRequired: string;
  accountHint: string | null;
  categoryLabel: string;
  categoryRequired: string;
  descriptionPlaceholder: string;
  notesPlaceholder: string;
}

const TEXTS: Record<TransactionFormMode, TransactionFormTexts> = {
  INGRESO: {
    title: 'Registrar Ingreso Manual',
    subtitle: 'Registra un ingreso directo en una cuenta bancaria o billetera (Caja protegida)',
    idPrefix: 'inc',
    submitLabel: 'Registrar Ingreso',
    accountLabel: 'Cuenta de Destino',
    balanceLabel: 'Saldo actual',
    accountRequired: 'La cuenta de destino es obligatoria.',
    accountHint: 'Nota: La cuenta Caja no admite ingresos manuales directos.',
    categoryLabel: 'Categoría de Ingreso',
    categoryRequired: 'La categoría de ingreso es obligatoria.',
    descriptionPlaceholder: 'Motivo o concepto del ingreso',
    notesPlaceholder: 'Detalles de referencia...'
  },
  GASTO: {
    title: 'Registrar Gasto Manual',
    subtitle: 'Registra una salida de dinero de cualquier cuenta incluyendo Caja',
    idPrefix: 'exp',
    submitLabel: 'Registrar Gasto',
    accountLabel: 'Cuenta de Origen',
    balanceLabel: 'Saldo',
    accountRequired: 'La cuenta de origen es obligatoria.',
    accountHint: null,
    categoryLabel: 'Categoría de Gasto',
    categoryRequired: 'La categoría de gasto es obligatoria.',
    descriptionPlaceholder: 'Motivo del gasto o compra',
    notesPlaceholder: 'Detalles de referencia...'
  },
  TRANSFERENCIA: {
    title: 'Registrar Transferencia',
    subtitle: 'Traspaso de dinero entre dos cuentas financieras diferentes',
    idPrefix: 'trf',
    submitLabel: 'Realizar Transferencia',
    accountLabel: 'Cuenta de Origen',
    balanceLabel: 'Saldo',
    accountRequired: 'La cuenta de origen es obligatoria.',
    accountHint: null,
    categoryLabel: '',
    categoryRequired: '',
    descriptionPlaceholder: '',
    notesPlaceholder: ''
  }
};

// Modal de alta de ingreso, gasto o transferencia; el padre es dueño del formulario y lo envía
@Component({
  selector: 'app-transaction-form-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent, CashLimitWarningComponent],
  templateUrl: './transaction-form-modal.component.html',
  styleUrls: ['../transaction-modal.css']
})
export class TransactionFormModalComponent {
  isOpen = input<boolean>(false);
  mode = input.required<TransactionFormMode>();
  form = input.required<FormGroup>();
  accounts = input<FinancialAccount[]>([]);
  categories = input<FinancialCategory[]>([]);
  error = input<string | null>(null);
  isSubmitting = input<boolean>(false);

  submitted = output<void>();
  close = output<void>();

  texts = computed(() => TEXTS[this.mode()]);

  isInvalid(field: string): boolean {
    const control = this.form().get(field);
    return !!control?.touched && !!control?.invalid;
  }

  /** Un gasto o una transferencia que sale de Caja muestra el aviso de efectivo esperado (CAJ-03). */
  isCashOutflow(): boolean {
    if (this.mode() === 'INGRESO') return false;
    const field = this.mode() === 'TRANSFERENCIA' ? 'sourceAccountId' : 'accountId';
    const accountId = this.form().get(field)?.value;
    return !!this.accounts().find((a) => a.id === accountId)?.isCashAccount;
  }

  hasError(field: string, error: string): boolean {
    return !!this.form().get(field)?.errors?.[error];
  }
}
