import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CashService } from '../../../core/cash/cash.service';
import {
  CashSessionSummary,
  CashSessionDetail,
  CashSessionStatus
} from '../../../core/cash/models/cash.models';
import { CashDetailModalComponent } from '../components/cash-detail-modal/cash-detail-modal.component';
import { ReopenCashModalComponent } from '../components/reopen-cash-modal/reopen-cash-modal.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { StoreDayPipe } from '../../../shared/utils/store-day.pipe';
import { sessionDayLabel } from '../../../shared/utils/store-date';

@Component({
  selector: 'app-cash-history',
  standalone: true,
  imports: [CommonModule, CashDetailModalComponent, ReopenCashModalComponent, StoreDayPipe],
  templateUrl: './cash-history.component.html',
  styleUrls: ['./cash-history.component.css']
})
export class CashHistoryComponent implements OnInit {
  private readonly cashService = inject(CashService);
  private readonly toast = inject(ToastService);

  sessions = signal<CashSessionSummary[]>([]);
  isLoading = signal<boolean>(false);
  statusFilter = signal<'ALL' | 'ABIERTA' | 'CERRADA'>('ALL');

  selectedDetail = signal<CashSessionDetail | null>(null);
  isDetailModalVisible = signal<boolean>(false);
  isDetailLoading = signal<boolean>(false);

  // CAJ-14
  reopenTarget = signal<CashSessionSummary | null>(null);
  isReopening = signal<boolean>(false);
  reopenError = signal<string | null>(null);

  filteredSessions = computed(() => {
    const list = this.sessions();
    const filter = this.statusFilter();
    if (filter === 'ALL') {
      return list;
    }
    return list.filter((s) => s.status === filter);
  });

  ngOnInit(): void {
    this.loadSessions();
  }

  // CAJ-11: «Día 28/10 · abierta el 29/10» when its day is not the day it was opened
  dayMark(session: CashSessionSummary): string | null {
    return sessionDayLabel(session.operationDate, session.openedAt);
  }

  loadSessions(): void {
    this.isLoading.set(true);
    this.cashService.getAllSessions().subscribe({
      next: (data) => {
        this.sessions.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.sessions.set([]);
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(status: 'ALL' | 'ABIERTA' | 'CERRADA'): void {
    this.statusFilter.set(status);
  }

  openDetail(session: CashSessionSummary): void {
    this.isDetailLoading.set(true);
    this.cashService.getSessionDetail(session.id).subscribe({
      next: (detail) => {
        this.selectedDetail.set(detail);
        this.isDetailModalVisible.set(true);
        this.isDetailLoading.set(false);
      },
      error: () => {
        this.isDetailLoading.set(false);
      }
    });
  }

  openReopen(session: CashSessionSummary): void {
    this.reopenError.set(null);
    this.reopenTarget.set(session);
  }

  closeReopen(): void {
    this.reopenTarget.set(null);
  }

  confirmReopen(reason: string): void {
    const session = this.reopenTarget();
    if (!session || this.isReopening()) {
      return;
    }
    this.isReopening.set(true);
    this.reopenError.set(null);
    this.cashService.reopenSession(session.id, reason).subscribe({
      next: (reopened) => {
        this.isReopening.set(false);
        this.reopenTarget.set(null);
        this.toast.success(`Caja #${reopened.sessionNumber} reabierta. Ya se ve en el POS.`);
        this.loadSessions();
      },
      error: (err) => {
        this.isReopening.set(false);
        this.reopenError.set(err.error?.message || 'Error al reabrir la caja.');
      }
    });
  }

  closeDetail(): void {
    this.isDetailModalVisible.set(false);
    this.selectedDetail.set(null);
  }
}
