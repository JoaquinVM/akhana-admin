import { Component, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinancialTransaction } from '../../../../../core/finance/models/finance.models';
import { ModalComponent } from '../../../../../shared/components/modal/modal.component';

// Void modal with reason; the server validates and reverts the balances
@Component({
  selector: 'app-transaction-void-modal',
  standalone: true,
  imports: [CommonModule, ModalComponent],
  templateUrl: './transaction-void-modal.component.html',
  styleUrls: ['../transaction-modal.css']
})
export class TransactionVoidModalComponent {
  isOpen = input<boolean>(false);
  transaction = input<FinancialTransaction | null>(null);
  error = input<string | null>(null);
  isVoiding = input<boolean>(false);
  reason = model<string>('');

  confirmed = output<void>();
  close = output<void>();
}
