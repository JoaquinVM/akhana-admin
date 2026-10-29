import { Component, computed, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FinancialCategory, FinancialTransaction } from '../../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../../shared/components/modal/modal.component';

// Modal to edit description, category or notes; the parent owns the form and submits it
@Component({
  selector: 'app-transaction-edit-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent],
  templateUrl: './transaction-edit-modal.component.html',
  styleUrls: ['../transaction-modal.css']
})
export class TransactionEditModalComponent {
  isOpen = input<boolean>(false);
  form = input.required<FormGroup>();
  transaction = input<FinancialTransaction | null>(null);
  categories = input<FinancialCategory[]>([]);
  error = input<string | null>(null);
  isSubmitting = input<boolean>(false);

  submitted = output<void>();
  close = output<void>();

  // Only categories of the same type (income or expense) that take manual movements are offered;
  // the current one is kept even if it is a system one
  sameTypeCategories = computed(() => {
    const current = this.transaction()?.category;
    return this.categories().filter(cat => cat.type === current?.type
      && (cat.manualEntryAllowed !== false || cat.id === current?.id));
  });
}
