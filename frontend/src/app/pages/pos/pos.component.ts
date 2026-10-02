import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CashService } from '../../core/cash/cash.service';
import {
  OpenCashRequest,
  CloseCashRequest,
  SaleRequest,
  Sale
} from '../../core/cash/models/cash.models';
import { OpenCashModalComponent } from '../cash/components/open-cash-modal/open-cash-modal.component';
import { RegisterSaleModalComponent } from '../cash/components/register-sale-modal/register-sale-modal.component';
import { CloseCashModalComponent } from '../cash/components/close-cash-modal/close-cash-modal.component';
import { CashSessionInfoModalComponent } from '../cash/components/cash-session-info-modal/cash-session-info-modal.component';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    OpenCashModalComponent,
    RegisterSaleModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent
  ],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.css']
})
export class PosComponent implements OnInit {
  readonly cashService = inject(CashService);

  // Estados de visibilidad de modales
  isOpenModalVisible = signal<boolean>(false);
  isSaleModalVisible = signal<boolean>(false);
  isCloseModalVisible = signal<boolean>(false);
  isInfoModalVisible = signal<boolean>(false);
  isActionLoading = signal<boolean>(false);

  feedbackMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  recentSales = signal<Sale[]>([]);

  ngOnInit(): void {
    this.loadCurrentSession();
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
    this.cashService.getSessionDetail(sessionId).subscribe({
      next: (detail) => {
        this.recentSales.set(detail.sales || []);
      },
      error: () => {
        this.recentSales.set([]);
      }
    });
  }

  // Modales y Acciones Operativas
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
        this.showFeedback('success', '¡Caja abierta exitosamente! Terminal POS listo para registrar ventas.');
        this.loadCurrentSession();
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al abrir la caja.';
        this.showFeedback('error', msg);
      }
    });
  }

  openSaleModal(): void {
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden registrar ventas con la caja cerrada. Abre una caja primero.');
      return;
    }
    this.isSaleModalVisible.set(true);
  }

  closeSaleModal(): void {
    this.isSaleModalVisible.set(false);
  }

  handleConfirmSale(request: SaleRequest): void {
    this.isActionLoading.set(true);
    this.cashService.registerSale(request).subscribe({
      next: (savedSale) => {
        this.isActionLoading.set(false);
        this.isSaleModalVisible.set(false);
        this.showFeedback('success', `Venta #${savedSale.saleNumber} registrada exitosamente.`);
        const session = this.cashService.currentSession();
        if (session) {
          this.loadSalesForSession(session.id);
        }
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al registrar la venta.';
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
