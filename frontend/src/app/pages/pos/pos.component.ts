import { Component, OnInit, inject, signal } from '@angular/core';
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

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    OpenCashModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent,
    VoidSaleModalComponent
  ],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.css']
})
export class PosComponent implements OnInit {
  private readonly router = inject(Router);
  readonly cashService = inject(CashService);
  private readonly saleService = inject(SaleService);

  // Estados de visibilidad de modales
  isOpenModalVisible = signal<boolean>(false);
  isCloseModalVisible = signal<boolean>(false);
  isInfoModalVisible = signal<boolean>(false);
  isVoidModalVisible = signal<boolean>(false);
  isActionLoading = signal<boolean>(false);

  selectedSaleToVoid = signal<Sale | null>(null);
  feedbackMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  recentSales = signal<Sale[]>([]);

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
        } else {
          this.recentSales.set([]);
        }
      },
      error: () => {
        this.recentSales.set([]);
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
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al cerrar la caja.';
        this.showFeedback('error', msg);
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
