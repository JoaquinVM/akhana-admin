import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashSessionSummary } from '../../../../core/cash/models/cash.models';

@Component({
  selector: 'app-cash-session-info-modal',
  standalone: true,
  imports: [CommonModule, ModalComponent],
  templateUrl: './cash-session-info-modal.component.html',
  styleUrls: ['./cash-session-info-modal.component.css']
})
export class CashSessionInfoModalComponent {
  isOpen = input<boolean>(false);
  session = input<CashSessionSummary | null>(null);

  close = output<void>();

  onClose(): void {
    this.close.emit();
  }
}
