import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CashService } from '../../../core/cash/cash.service';
import {
  CashSessionSummary,
  CashSessionDetail,
  CashSessionStatus
} from '../../../core/cash/models/cash.models';
import { CashDetailModalComponent } from '../components/cash-detail-modal/cash-detail-modal.component';

@Component({
  selector: 'app-cash-history',
  standalone: true,
  imports: [CommonModule, CashDetailModalComponent],
  templateUrl: './cash-history.component.html',
  styleUrls: ['./cash-history.component.css']
})
export class CashHistoryComponent implements OnInit {
  private readonly cashService = inject(CashService);

  sessions = signal<CashSessionSummary[]>([]);
  isLoading = signal<boolean>(false);
  statusFilter = signal<'ALL' | 'ABIERTA' | 'CERRADA'>('ALL');

  selectedDetail = signal<CashSessionDetail | null>(null);
  isDetailModalVisible = signal<boolean>(false);
  isDetailLoading = signal<boolean>(false);

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

  closeDetail(): void {
    this.isDetailModalVisible.set(false);
    this.selectedDetail.set(null);
  }
}
