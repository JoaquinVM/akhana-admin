import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashSessionDetail } from '../../../../core/cash/models/cash.models';

@Component({
  selector: 'app-cash-detail-modal',
  standalone: true,
  imports: [CommonModule, ModalComponent],
  templateUrl: './cash-detail-modal.component.html',
  styleUrls: ['./cash-detail-modal.component.css']
})
export class CashDetailModalComponent {
  isOpen = input<boolean>(false);
  session = input<CashSessionDetail | null>(null);

  close = output<void>();

  activeTab: 'ventas' | 'cortes' = 'ventas';

  setTab(tab: 'ventas' | 'cortes'): void {
    this.activeTab = tab;
  }

  onClose(): void {
    this.close.emit();
  }
}
