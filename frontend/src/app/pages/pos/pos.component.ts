import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CashService } from '../../core/cash/cash.service';
import { SaleService } from '../../core/sale/sale.service';
import {
  OpenCashRequest,
  CloseCashRequest,
  Sale
} from '../../core/cash/models/cash.models';
import { VoidSaleRequest } from '../../core/sale/models/sale.models';
import { OpenCashModalComponent } from '../cash/components/open-cash-modal/open-cash-modal.component';
import { CloseCashModalComponent } from '../cash/components/close-cash-modal/close-cash-modal.component';
import { CashSessionInfoModalComponent } from '../cash/components/cash-session-info-modal/cash-session-info-modal.component';
import { VoidSaleModalComponent } from './components/void-sale-modal/void-sale-modal.component';
import { CashExpenseModalComponent } from './components/cash-expense-modal/cash-expense-modal.component';
import { PosKpisComponent } from './components/pos-kpis/pos-kpis.component';
import { SaleDetailModalComponent } from '../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { FinancialTransactionService } from '../../core/finance/financial-transaction.service';
import { FinancialTransaction } from '../../core/finance/models/finance.models';
import { formatBs } from '../../shared/utils/money';
import { PosFeedbackBannerComponent } from './components/pos-feedback-banner/pos-feedback-banner.component';
import { PosClosedStateComponent } from './components/pos-closed-state/pos-closed-state.component';
import { SessionTransactionsComponent } from './components/session-transactions/session-transactions.component';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    OpenCashModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent,
    VoidSaleModalComponent,
    CashExpenseModalComponent,
    PosKpisComponent,
    PosFeedbackBannerComponent,
    PosClosedStateComponent,
    SaleDetailModalComponent,
    SessionTransactionsComponent
  ],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.css']
})
export class PosComponent implements OnInit {
  private readonly router = inject(Router);
  readonly cashService = inject(CashService);
  private readonly saleService = inject(SaleService);
  private readonly transactionService = inject(FinancialTransactionService);

  // Modal visibility
  isOpenModalVisible = signal<boolean>(false);
  isCloseModalVisible = signal<boolean>(false);
  isInfoModalVisible = signal<boolean>(false);
  isVoidModalVisible = signal<boolean>(false);
  isDetailModalVisible = signal<boolean>(false);
  isCashExpenseModalVisible = signal<boolean>(false);
  isActionLoading = signal<boolean>(false);


  selectedSaleToVoid = signal<Sale | null>(null);
  selectedSaleForDetailId = signal<string | null>(null);
  selectedSaleForDetail = signal<Sale | null>(null);
  feedbackMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  recentSales = signal<Sale[]>([]);

  // Purchases and expenses of the session
  recentExpenses = signal<FinancialTransaction[]>([]);
  isLoadingExpenses = signal<boolean>(false);

  // Cash ins and outs of the session (CAJ-08)
  recentTransfers = signal<FinancialTransaction[]>([]);

  // Expense totals (voided expenses do not count)
  activeExpenses = computed(() =>
    this.recentExpenses().filter((e) => e.status === 'ACTIVO')
  );
  totalExpensesAmount = computed(() =>
    this.activeExpenses().reduce((sum, e) => sum + e.amount, 0)
  );

  // Expected cash in the drawer: computed by the server (CAJ-03: includes Caja expenses and transfers)
  expectedCashInDrawer = computed(() => Number(this.cashService.currentSession()?.expectedCash) || 0);

  ngOnInit(): void {
    this.checkNavigationState();
    this.loadCurrentSession();
  }

  private checkNavigationState(): void {
    const state = window.history.state;
    if (state?.saleSuccessMessage) {
      this.showFeedback('success', state.saleSuccessMessage);
      window.history.replaceState({}, '');
    }
  }

  loadCurrentSession(): void {
    this.cashService.getCurrentSession().subscribe({
      next: (session) => {
        if (session) {
          this.loadSalesForSession(session.id);
          this.loadExpensesForSession(session.id);
        } else {
          this.recentSales.set([]);
          this.recentExpenses.set([]);
          this.recentTransfers.set([]);
        }
      },
      error: () => {
        this.recentSales.set([]);
        this.recentExpenses.set([]);
        this.recentTransfers.set([]);
      }
    });
  }

  loadExpensesForSession(sessionId: string): void {
    this.isLoadingExpenses.set(true);
    this.transactionService.getExpensesByCashSession(sessionId).subscribe({
      next: (movements) => {
        // The session links Caja expenses and transfers; each tab shows its own
        this.recentExpenses.set((movements || []).filter((m) => m.type === 'GASTO_MANUAL'));
        this.recentTransfers.set((movements || []).filter((m) => m.type === 'TRANSFERENCIA'));
        this.isLoadingExpenses.set(false);
      },
      error: () => {
        this.recentExpenses.set([]);
        this.recentTransfers.set([]);
        this.isLoadingExpenses.set(false);
      }
    });
  }

  // After registering or voiding a cash in/out: refresh movements and the expected cash
  onCashTransfersChanged(message: string): void {
    this.showFeedback('success', message);
    this.loadCurrentSession();
  }

  private loadSalesForSession(sessionId: string): void {
    this.saleService.getSalesBySession(sessionId).subscribe({
      next: (sales) => {
        this.recentSales.set(sales || []);
      },
      error: () => {
        this.cashService.getSessionDetail(sessionId).subscribe({
          next: (detail) => this.recentSales.set(detail.sales || []),
          error: () => this.recentSales.set([])
        });
      }
    });
  }

  // Navigation to the register-sale screen
  navigateToRegisterSale(): void {
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden registrar ventas con la caja cerrada. Abre una caja primero.');
      return;
    }
    this.router.navigate(['/pos/sale']);
  }

  // Kept for older tests
  openSaleModal(): void {
    this.navigateToRegisterSale();
  }

  // Sale detail lookup
  openDetailModal(sale: Sale): void {
    this.selectedSaleForDetailId.set(sale.id);
    this.selectedSaleForDetail.set(sale);
    this.isDetailModalVisible.set(true);
  }

  closeDetailModal(): void {
    this.selectedSaleForDetailId.set(null);
    this.selectedSaleForDetail.set(null);
    this.isDetailModalVisible.set(false);
  }

  // Sale void
  openVoidModal(sale: Sale): void {
    if (sale.status === 'ANULADA') return;
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden anular ventas con la caja cerrada.');
      return;
    }
    this.selectedSaleToVoid.set(sale);
    this.isVoidModalVisible.set(true);
  }

  closeVoidModal(): void {
    this.selectedSaleToVoid.set(null);
    this.isVoidModalVisible.set(false);
  }

  handleConfirmVoid(request: VoidSaleRequest): void {
    const sale = this.selectedSaleToVoid();
    if (!sale) return;

    this.isActionLoading.set(true);
    this.saleService.voidSale(sale.id, request).subscribe({
      next: (updatedSale) => {
        this.isActionLoading.set(false);
        this.closeVoidModal();
        this.showFeedback('success', `Venta #${updatedSale.saleNumber} anulada exitosamente.`);
        const session = this.cashService.currentSession();
        if (session) {
          this.loadSalesForSession(session.id);
          this.cashService.getCurrentSession().subscribe();
        }
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al anular la venta.';
        this.showFeedback('error', msg);
      }
    });
  }

  // Cash session modals and actions
  openInfoModal(): void {
    this.isInfoModalVisible.set(true);
  }

  closeInfoModal(): void {
    this.isInfoModalVisible.set(false);
  }

  openCashModal(): void {
    this.isOpenModalVisible.set(true);
  }

  closeOpenModal(): void {
    this.isOpenModalVisible.set(false);
  }

  handleConfirmOpen(request: OpenCashRequest): void {
    this.isActionLoading.set(true);
    this.cashService.openSession(request).subscribe({
      next: () => {
        this.isActionLoading.set(false);
        this.isOpenModalVisible.set(false);
        this.showFeedback('success', '¡Caja abierta exitosamente! Terminal POS listo para operar.');
        this.loadCurrentSession();
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al abrir la caja.';
        this.showFeedback('error', msg);
      }
    });
  }

  openCloseModal(): void {
    this.isCloseModalVisible.set(true);
  }

  closeCloseModal(): void {
    this.isCloseModalVisible.set(false);
  }

  handleConfirmClose(request: CloseCashRequest): void {
    this.isActionLoading.set(true);
    this.cashService.closeSession(request).subscribe({
      next: (closedSession) => {
        this.isActionLoading.set(false);
        this.isCloseModalVisible.set(false);
        const diff = closedSession.difference ?? 0;
        const diffText = diff === 0
          ? 'con cuadre exacto'
          : `con ${diff > 0 ? 'sobrante' : 'faltante'} de ${formatBs(Math.abs(diff))}`;
        this.showFeedback('success', `Caja #${closedSession.sessionNumber} cerrada exitosamente (${diffText}).`);
        this.recentSales.set([]);
        this.recentExpenses.set([]);
        this.recentTransfers.set([]);
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al cerrar la caja.';
        this.showFeedback('error', msg);
      }
    });
  }

  // Direct cash expense
  openCashExpenseModal(): void {
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se puede registrar un gasto con la caja cerrada.');
      return;
    }
    this.isCashExpenseModalVisible.set(true);
  }

  closeCashExpenseModal(): void {
    this.isCashExpenseModalVisible.set(false);
  }

  onCashExpenseSaved(message: string): void {
    this.closeCashExpenseModal();
    this.showFeedback('success', message);
    const session = this.cashService.currentSession();
    if (session) {
      this.loadExpensesForSession(session.id);
    }
    this.loadCurrentSession();
  }

  // After voiding a cash expense: refresh the expenses and the expected cash
  onCashExpensesChanged(message: string): void {
    this.showFeedback('success', message);
    this.loadCurrentSession();
  }

  dismissFeedback(): void {
    this.feedbackMessage.set(null);
  }

  private showFeedback(type: 'success' | 'error', text: string): void {
    this.feedbackMessage.set({ type, text });
    setTimeout(() => {
      this.dismissFeedback();
    }, 6000);
  }
}
