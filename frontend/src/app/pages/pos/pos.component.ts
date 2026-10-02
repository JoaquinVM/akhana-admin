import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CashService } from '../../core/cash/cash.service';
import { ProductService } from '../../core/product/product.service';
import { QuickProductService } from '../../core/quick-product/quick-product.service';
import { SaleService } from '../../core/sale/sale.service';
import {
  OpenCashRequest,
  CloseCashRequest,
  Sale
} from '../../core/cash/models/cash.models';
import { Product } from '../../core/product/models/product.models';
import { QuickProductGroup } from '../../core/quick-product/models/quick-product.models';
import {
  SaleDetailRequest,
  SaleItemRequest,
  VoidSaleRequest
} from '../../core/sale/models/sale.models';
import { OpenCashModalComponent } from '../cash/components/open-cash-modal/open-cash-modal.component';
import { CloseCashModalComponent } from '../cash/components/close-cash-modal/close-cash-modal.component';
import { CashSessionInfoModalComponent } from '../cash/components/cash-session-info-modal/cash-session-info-modal.component';
import { QuickProductsConfigModalComponent } from './components/quick-products-config-modal/quick-products-config-modal.component';
import { CheckoutModalComponent } from './components/checkout-modal/checkout-modal.component';
import { VoidSaleModalComponent } from './components/void-sale-modal/void-sale-modal.component';

export interface CartItem {
  product: Product;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  unitFinalPrice: number;
  subtotal: number;
}

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    OpenCashModalComponent,
    CloseCashModalComponent,
    CashSessionInfoModalComponent,
    QuickProductsConfigModalComponent,
    CheckoutModalComponent,
    VoidSaleModalComponent
  ],
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.css']
})
export class PosComponent implements OnInit {
  readonly cashService = inject(CashService);
  readonly productService = inject(ProductService);
  readonly quickProductService = inject(QuickProductService);
  readonly saleService = inject(SaleService);

  // Estados de visibilidad de modales
  isOpenModalVisible = signal<boolean>(false);
  isCloseModalVisible = signal<boolean>(false);
  isInfoModalVisible = signal<boolean>(false);
  isConfigModalVisible = signal<boolean>(false);
  isCheckoutModalVisible = signal<boolean>(false);
  isVoidModalVisible = signal<boolean>(false);
  isActionLoading = signal<boolean>(false);

  selectedSaleToVoid = signal<Sale | null>(null);
  feedbackMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  recentSales = signal<Sale[]>([]);

  // Búsqueda y catálogo de productos
  catalogProducts = signal<Product[]>([]);
  productSearchTerm = signal<string>('');

  // Productos Rápidos
  activeGroupId = signal<string | null>(null);

  // Carrito de ventas
  cartItems = signal<CartItem[]>([]);
  globalDiscount = signal<number>(0);

  // Computed: Grupo activo de productos rápidos
  activeGroup = computed<QuickProductGroup | null>(() => {
    const groups = this.quickProductService.groups();
    if (groups.length === 0) return null;
    const currentId = this.activeGroupId();
    if (!currentId) return groups[0];
    return groups.find((g) => g.id === currentId) ?? groups[0];
  });

  // Computed: Resultados filtrados de búsqueda de productos
  filteredSearchResults = computed<Product[]>(() => {
    const term = this.productSearchTerm().toLowerCase().trim();
    if (!term) return [];
    return this.catalogProducts()
      .filter((p) => p.name.toLowerCase().includes(term) || p.code.toLowerCase().includes(term))
      .slice(0, 8);
  });

  // Computed: Métricas del Carrito
  cartCount = computed<number>(() => {
    return this.cartItems().reduce((sum, item) => sum + item.quantity, 0);
  });

  subtotalGross = computed<number>(() => {
    const raw = this.cartItems().reduce((sum, it) => sum + it.quantity * it.unitPrice, 0);
    return Math.round(raw * 100) / 100;
  });

  discountItemsTotal = computed<number>(() => {
    const raw = this.cartItems().reduce((sum, it) => sum + it.quantity * it.unitDiscount, 0);
    return Math.round(raw * 100) / 100;
  });

  subtotalPostItems = computed<number>(() => {
    return Math.max(0, Math.round((this.subtotalGross() - this.discountItemsTotal()) * 100) / 100);
  });

  discountTotal = computed<number>(() => {
    return Math.round((this.discountItemsTotal() + this.globalDiscount()) * 100) / 100;
  });

  totalAmount = computed<number>(() => {
    return Math.max(0, Math.round((this.subtotalPostItems() - this.globalDiscount()) * 100) / 100);
  });

  checkoutItems = computed<SaleItemRequest[]>(() => {
    return this.cartItems().map((it) => ({
      productId: it.product.id,
      quantity: it.quantity,
      discountPerUnit: it.unitDiscount > 0 ? it.unitDiscount : undefined
    }));
  });

  // Compatibilidad con tests anteriores
  get isSaleModalVisible() {
    return this.isCheckoutModalVisible;
  }

  ngOnInit(): void {
    this.loadCurrentSession();
    this.loadCatalogProducts();
    this.quickProductService.loadGroups().subscribe();
  }

  loadCatalogProducts(): void {
    this.productService.getProducts(undefined, 'ACTIVO').subscribe({
      next: (products) => this.catalogProducts.set(products),
      error: () => this.catalogProducts.set([])
    });
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
        // Fallback a getSessionDetail si el endpoint específico fallara
        this.cashService.getSessionDetail(sessionId).subscribe({
          next: (detail) => this.recentSales.set(detail.sales || []),
          error: () => this.recentSales.set([])
        });
      }
    });
  }

  // Operaciones de Carrito
  addToCart(product: Product): void {
    const existing = this.cartItems().find((it) => it.product.id === product.id);
    if (existing) {
      this.incrementQuantity(product.id);
    } else {
      const newItem: CartItem = {
        product,
        quantity: 1,
        unitPrice: product.sellPrice,
        unitDiscount: 0,
        unitFinalPrice: product.sellPrice,
        subtotal: product.sellPrice
      };
      this.cartItems.update((items) => [...items, newItem]);
    }
    this.productSearchTerm.set('');
  }

  incrementQuantity(productId: string): void {
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.product.id === productId) {
          const newQty = it.quantity + 1;
          return {
            ...it,
            quantity: newQty,
            subtotal: Math.round(newQty * it.unitFinalPrice * 100) / 100
          };
        }
        return it;
      })
    );
  }

  decrementQuantity(productId: string): void {
    const item = this.cartItems().find((it) => it.product.id === productId);
    if (!item) return;

    if (item.quantity > 1) {
      this.cartItems.update((items) =>
        items.map((it) => {
          if (it.product.id === productId) {
            const newQty = it.quantity - 1;
            return {
              ...it,
              quantity: newQty,
              subtotal: Math.round(newQty * it.unitFinalPrice * 100) / 100
            };
          }
          return it;
        })
      );
    } else {
      this.removeFromCart(productId);
    }
  }

  updateQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.removeFromCart(productId);
      return;
    }
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.product.id === productId) {
          return {
            ...it,
            quantity,
            subtotal: Math.round(quantity * it.unitFinalPrice * 100) / 100
          };
        }
        return it;
      })
    );
  }

  updateUnitDiscount(productId: string, discount: number): void {
    const safeDiscount = Math.max(0, discount || 0);
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.product.id === productId) {
          const cappedDiscount = Math.min(it.unitPrice, safeDiscount);
          const unitFinalPrice = Math.max(0, Math.round((it.unitPrice - cappedDiscount) * 100) / 100);
          return {
            ...it,
            unitDiscount: cappedDiscount,
            unitFinalPrice,
            subtotal: Math.round(it.quantity * unitFinalPrice * 100) / 100
          };
        }
        return it;
      })
    );

    // Ajustar descuento global si supera el nuevo subtotal
    if (this.globalDiscount() > this.subtotalPostItems()) {
      this.globalDiscount.set(this.subtotalPostItems());
    }
  }

  updateGlobalDiscount(discount: number): void {
    const safeDiscount = Math.max(0, discount || 0);
    const maxAllowed = this.subtotalPostItems();
    if (safeDiscount > maxAllowed) {
      this.showFeedback('error', `El descuento general no puede superar el subtotal de Bs ${maxAllowed.toFixed(2)}.`);
      this.globalDiscount.set(maxAllowed);
      return;
    }
    this.globalDiscount.set(Math.round(safeDiscount * 100) / 100);
  }

  removeFromCart(productId: string): void {
    this.cartItems.update((items) => items.filter((it) => it.product.id !== productId));
    if (this.globalDiscount() > this.subtotalPostItems()) {
      this.globalDiscount.set(this.subtotalPostItems());
    }
  }

  clearCart(): void {
    this.cartItems.set([]);
    this.globalDiscount.set(0);
  }

  // Grupos de productos rápidos
  selectGroup(groupId: string): void {
    this.activeGroupId.set(groupId);
  }

  openConfigModal(): void {
    this.isConfigModalVisible.set(true);
  }

  closeConfigModal(): void {
    this.isConfigModalVisible.set(false);
  }

  // Modal de Cobro (Checkout)
  openCheckoutModal(): void {
    if (!this.cashService.currentSession()) {
      this.showFeedback('error', 'No se pueden registrar ventas con la caja cerrada. Abre una caja primero.');
      return;
    }
    if (this.cartItems().length === 0) {
      this.showFeedback('error', 'El carrito está vacío. Agrega productos para realizar una venta.');
      return;
    }
    this.isCheckoutModalVisible.set(true);
  }

  closeCheckoutModal(): void {
    this.isCheckoutModalVisible.set(false);
  }

  handleConfirmCheckout(payload: SaleDetailRequest): void {
    this.isActionLoading.set(true);
    this.saleService.registerSale(payload).subscribe({
      next: (createdSale) => {
        this.isActionLoading.set(false);
        this.isCheckoutModalVisible.set(false);
        this.clearCart();
        this.showFeedback('success', `Venta #${createdSale.saleNumber} registrada exitosamente.`);
        const session = this.cashService.currentSession();
        if (session) {
          this.loadSalesForSession(session.id);
          this.cashService.getCurrentSession().subscribe();
        }
      },
      error: (err) => {
        this.isActionLoading.set(false);
        const msg = err.error?.message || 'Error al procesar la venta.';
        this.showFeedback('error', msg);
      }
    });
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

  // Métodos puente para compatibilidad
  openSaleModal(): void {
    this.openCheckoutModal();
  }

  closeSaleModal(): void {
    this.closeCheckoutModal();
  }

  handleConfirmSale(request: any): void {
    // Si se invoca el método legacy
    if (this.cartItems().length > 0) {
      this.handleConfirmCheckout({
        items: this.checkoutItems(),
        globalDiscountAmount: this.globalDiscount(),
        paymentMethod: request.paymentMethod || 'EFECTIVO'
      });
    } else {
      this.cashService.registerSale(request).subscribe({
        next: (savedSale) => {
          this.showFeedback('success', `Venta #${savedSale.saleNumber} registrada exitosamente.`);
          const session = this.cashService.currentSession();
          if (session) {
            this.loadSalesForSession(session.id);
          }
        },
        error: (err) => {
          const msg = err.error?.message || 'Error al registrar la venta.';
          this.showFeedback('error', msg);
        }
      });
    }
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
