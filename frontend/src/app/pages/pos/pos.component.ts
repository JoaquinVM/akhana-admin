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
import { CashTransfersComponent } from './components/cash-transfers/cash-transfers.component';
import { CashExpenseModalComponent } from './components/cash-expense-modal/cash-expense-modal.component';
import { SaleDetailModalComponent } from '../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { SalesTableComponent } from '../../shared/components/sales-table/sales-table.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { FormsModule } from '@angular/forms';
import { FinancialTransactionService } from '../../core/finance/financial-transaction.service';
import {
  FinancialTransaction,
  VoidFinancialTransactionRequest
} from '../../core/finance/models/finance.models';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    OpenCashModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent,
    VoidSaleModalComponent,
    CashTransfersComponent,
    CashExpenseModalComponent,
    SaleDetailModalComponent,
    SalesTableComponent,
    ModalComponent
  ],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.css']
})
export class PosComponent implements OnInit {
  private readonly router = inject(Router);
  readonly cashService = inject(CashService);
  private readonly saleService = inject(SaleService);
  private readonly transactionService = inject(FinancialTransactionService);

  // Selector de vista: Ventas, Compras o Ingresos/Retiros de caja
  activeTab = signal<'VENTAS' | 'COMPRAS' | 'MOVIMIENTOS'>('VENTAS');

  // Estados de visibilidad de modales
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

  // Compras y Gastos de la sesión
  recentExpenses = signal<FinancialTransaction[]>([]);
  isLoadingExpenses = signal<boolean>(false);

  // Ingresos y retiros de caja de la sesión (CAJ-08)
  recentTransfers = signal<FinancialTransaction[]>([]);

  // Computados de gastos (los gastos anulados no se cuentan como activos)
  activeExpenses = computed(() =>
    this.recentExpenses().filter((e) => e.status === 'ACTIVO')
  );
  activeExpensesCount = computed(() => this.activeExpenses().length);
  totalExpensesAmount = computed(() =>
    this.activeExpenses().reduce((sum, e) => sum + e.amount, 0)
  );

  // Efectivo esperado en gaveta: lo calcula el servidor (CAJ-03: incluye gastos y transferencias de Caja)
  expectedCashInDrawer = computed(() => Number(this.cashService.currentSession()?.expectedCash) || 0);

  // Modal de anulación de gastos
  selectedExpenseToVoid = signal<FinancialTransaction | null>(null);
  isVoidExpenseModalVisible = signal<boolean>(false);
  voidExpenseReason = signal<string>('');
  voidExpenseError = signal<string | null>(null);

  ngOnInit(): void {
    this.checkNavigationState();
    this.loadCurrentSession();
  }

  setActiveTab(tab: 'VENTAS' | 'COMPRAS' | 'MOVIMIENTOS'): void {
    this.activeTab.set(tab);
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
        // La sesión vincula gastos y transferencias de Caja; cada pestaña muestra los suyos
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

  // Tras registrar o anular un ingreso/retiro: refresca movimientos y el efectivo esperado
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

  // Navegación hacia pantalla de Registro de Venta
  navigateToRegisterSale(): void {
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden registrar ventas con la caja cerrada. Abre una caja primero.');
      return;
    }
    this.router.navigate(['/pos/sale']);
  }

  // Compatibilidad con pruebas previas
  openSaleModal(): void {
    this.navigateToRegisterSale();
  }

  // Consulta de Detalle de Venta
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

  // Anulación de Ventas
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

  // Modales y Acciones Operativas de Caja
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
          : `con diferencia de ${diff >= 0 ? '+' : ''}${diff} Bs`;
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

  // Gasto de Caja Directo
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

  // Anulación de Gasto
  openVoidExpenseModal(tx: FinancialTransaction): void {
    if (tx.status === 'ANULADO') return;
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden anular gastos con la caja cerrada.');
      return;
    }
    this.selectedExpenseToVoid.set(tx);
    this.voidExpenseReason.set('');
    this.voidExpenseError.set(null);
    this.isVoidExpenseModalVisible.set(true);
  }

  closeVoidExpenseModal(): void {
    this.selectedExpenseToVoid.set(null);
    this.voidExpenseReason.set('');
    this.voidExpenseError.set(null);
    this.isVoidExpenseModalVisible.set(false);
  }

  handleConfirmVoidExpense(): void {
    const tx = this.selectedExpenseToVoid();
    if (!tx) return;

    this.isActionLoading.set(true);
    this.voidExpenseError.set(null);

    const req: VoidFinancialTransactionRequest = {
      voidReason: this.voidExpenseReason().trim()
    };

    this.transactionService.voidCashExpense(tx.id, req).subscribe({
      next: (updatedTx) => {
        this.isActionLoading.set(false);
        this.closeVoidExpenseModal();
        this.showFeedback('success', `Gasto de Bs. ${updatedTx.amount.toFixed(2)} anulado exitosamente.`);
        const session = this.cashService.currentSession();
        if (session) {
          this.loadExpensesForSession(session.id);
          this.cashService.getCurrentSession().subscribe();
        }
      },
      error: (err: any) => {
        this.isActionLoading.set(false);
        this.voidExpenseError.set(err.error?.message || 'Error al anular el gasto.');
      }
    });
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
