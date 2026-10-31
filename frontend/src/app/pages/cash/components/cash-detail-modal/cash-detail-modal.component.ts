import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashSessionDetail, Sale } from '../../../../core/cash/models/cash.models';
import { StoreDayPipe } from '../../../../shared/utils/store-day.pipe';
import { operationDayLabel, sessionDayLabel } from '../../../../shared/utils/store-date';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-cash-detail-modal',
  standalone: true,
  imports: [IconComponent, CommonModule, ModalComponent, StoreDayPipe],
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

  // CAJ-11: «Día 28/10 · abierta el 29/10» when its day is not the day it was opened
  sessionMark(session: CashSessionDetail): string | null {
    return sessionDayLabel(session.operationDate, session.openedAt);
  }

  // CAJ-11: «Día 31/10 · cargada el 01/11 00:20» for a sale recorded on another day
  saleMark(sale: Sale): string | null {
    return operationDayLabel(sale.operationDate, sale.createdAt);
  }

  onClose(): void {
    this.close.emit();
  }
}
