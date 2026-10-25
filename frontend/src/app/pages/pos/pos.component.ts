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
import { SaleDetailModalComponent } from '../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FinancialTransactionService } from '../../core/finance/financial-transaction.service';
import { FinancialCategoryService } from '../../core/finance/financial-category.service';
import {
  FinancialCategory,
  CashExpenseRequest,
  FinancialTransaction,
  VoidFinancialTransactionRequest
} from '../../core/finance/models/finance.models';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    OpenCashModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent,
    VoidSaleModalComponent,
    SaleDetailModalComponent,
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
  private readonly categoryService = inject(FinancialCategoryService);
  private readonly fb = inject(FormBuilder);

  // Selector de vista: Ventas o Compras
  activeTab = signal<'VENTAS' | 'COMPRAS'>('VENTAS');

  // Estados de visibilidad de modales
  isOpenModalVisible = signal<boolean>(false);
  isCloseModalVisible = signal<boolean>(false);
  isInfoModalVisible = signal<boolean>(false);
  isVoidModalVisible = signal<boolean>(false);
  isDetailModalVisible = signal<boolean>(false);
  isCashExpenseModalVisible = signal<boolean>(false);
  isActionLoading = signal<boolean>(false);

  expenseCategories = signal<FinancialCategory[]>([]);
  cashExpenseError = signal<string | null>(null);

  cashExpenseForm: FormGroup = this.fb.group({
    categoryId: ['', Validators.required],
    amount: [null, [Validators.required, Validators.min(0.01)]],
    description: ['', [Validators.required, Validators.maxLength(255)]],
    notes: ['']
  });

  selectedSaleToVoid = signal<Sale | null>(null);
  selectedSaleForDetailId = signal<string | null>(null);
  selectedSaleForDetail = signal<Sale | null>(null);
  feedbackMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  recentSales = signal<Sale[]>([]);

  // Compras y Gastos de la sesión
  recentExpenses = signal<FinancialTransaction[]>([]);
  isLoadingExpenses = signal<boolean>(false);

  // Computados de gastos (los gastos anulados no se cuentan como activos)
  activeExpenses = computed(() =>
    this.recentExpenses().filter((e) => e.status === 'ACTIVO')
  );
  activeExpensesCount = computed(() => this.activeExpenses().length);
  totalExpensesAmount = computed(() =>
    this.activeExpenses().reduce((sum, e) => sum + e.amount, 0)
  );

  // Monto esperado en gaveta = monto inicial + ventas en efectivo - gastos activos de Caja
  expectedCashInDrawer = computed(() => {
    const session = this.cashService.currentSession();
    if (!session) return 0;
    const opening = Number(session.openingAmount) || 0;
    const salesCash = Number(session.totalSalesCash) || 0;
    const activeExpenses = this.totalExpensesAmount();
    return opening + salesCash - activeExpenses;
  });

  // Modal de anulación de gastos
  selectedExpenseToVoid = signal<FinancialTransaction | null>(null);
  isVoidExpenseModalVisible = signal<boolean>(false);
  voidExpenseReason = signal<string>('');
  voidExpenseError = signal<string | null>(null);

  ngOnInit(): void {
    this.checkNavigationState();
    this.loadCurrentSession();
  }

  setActiveTab(tab: 'VENTAS' | 'COMPRAS'): void {
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
        }
      },
      error: () => {
        this.recentSales.set([]);
        this.recentExpenses.set([]);
      }
    });
  }

  loadExpensesForSession(sessionId: string): void {
    this.isLoadingExpenses.set(true);
    this.transactionService.getExpensesByCashSession(sessionId).subscribe({
      next: (expenses) => {
        this.recentExpenses.set(expenses || []);
        this.isLoadingExpenses.set(false);
      },
      error: () => {
        this.recentExpenses.set([]);
        this.isLoadingExpenses.set(false);
      }
    });
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
    this.cashExpenseError.set(null);
    this.categoryService.getCategories('GASTO').subscribe({
      next: (cats) => {
        this.expenseCategories.set(cats);
        this.cashExpenseForm.reset({
          categoryId: cats[0]?.id || '',
          amount: null,
          description: '',
          notes: ''
        });
        this.isCashExpenseModalVisible.set(true);
      },
      error: () => {
        this.showFeedback('error', 'Error al cargar categorías de gasto.');
      }
    });
  }

  closeCashExpenseModal(): void {
    this.isCashExpenseModalVisible.set(false);
    this.cashExpenseError.set(null);
  }

  handleConfirmCashExpense(): void {
    if (this.cashExpenseForm.invalid) {
      this.cashExpenseForm.markAllAsTouched();
      return;
    }

    this.isActionLoading.set(true);
    this.cashExpenseError.set(null);

    const val = this.cashExpenseForm.value;
    const req: CashExpenseRequest = {
      categoryId: val.categoryId,
      amount: Number(val.amount),
      description: val.description.trim(),
      notes: val.notes ? val.notes.trim() : undefined
    };

    this.transactionService.createCashExpense(req).subscribe({
      next: (tx) => {
        this.isActionLoading.set(false);
        this.closeCashExpenseModal();
        this.showFeedback('success', `Gasto de caja de Bs. ${tx.amount.toFixed(2)} registrado exitosamente.`);
        const session = this.cashService.currentSession();
        if (session) {
          this.loadExpensesForSession(session.id);
        }
        this.loadCurrentSession();
      },
      error: (err: any) => {
        this.isActionLoading.set(false);
        this.cashExpenseError.set(err.error?.message || 'Error al registrar el gasto de caja.');
      }
    });
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
      voidReason: this.voidExpenseReason().trim() || undefined
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
