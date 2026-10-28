import { Component, computed, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FinancialCategory, FinancialTransaction } from '../../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../../shared/components/modal/modal.component';

// Modal para editar descripción, categoría o notas; el padre es dueño del formulario y lo envía
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

  // Solo se ofrece cambiar a una categoría del mismo tipo (ingreso o gasto)
  sameTypeCategories = computed(() => {
    const type = this.transaction()?.category?.type;
    return this.categories().filter(cat => cat.type === type);
  });
}
