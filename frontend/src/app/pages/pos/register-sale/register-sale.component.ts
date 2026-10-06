import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CashService } from '../../../core/cash/cash.service';
import { ProductService } from '../../../core/product/product.service';
import { QuickProductService } from '../../../core/quick-product/quick-product.service';
import { SaleService } from '../../../core/sale/sale.service';
import { Product } from '../../../core/product/models/product.models';
import { QuickProductGroup } from '../../../core/quick-product/models/quick-product.models';
import {
  PaymentMethod,
  SaleDetailRequest,
  SaleItemRequest
} from '../../../core/sale/models/sale.models';
import { QuickProductsConfigModalComponent } from '../components/quick-products-config-modal/quick-products-config-modal.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';

export interface CartItem {
  product: Product;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  unitFinalPrice: number;
  subtotal: number;
}

@Component({
  selector: 'app-register-sale',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    QuickProductsConfigModalComponent,
    ModalComponent
  ],
  templateUrl: './register-sale.component.html',
  styleUrls: ['./register-sale.component.css']
})
export class RegisterSaleComponent implements OnInit {
  private readonly router = inject(Router);
  readonly cashService = inject(CashService);
  private readonly productService = inject(ProductService);
  readonly quickProductService = inject(QuickProductService);
  private readonly saleService = inject(SaleService);

  // Estados de catálogo y búsqueda
  catalogProducts = signal<Product[]>([]);
  productSearchTerm = signal<string>('');
  isLoadingCatalog = signal<boolean>(false);

  // Productos Rápidos
  activeGroupId = signal<string | null>(null);
  isConfigModalVisible = signal<boolean>(false);

  // Carrito de ventas
  cartItems = signal<CartItem[]>([]);
  globalDiscount = signal<number>(0);

  // Pasarela y Modal de Cobro
  isCheckoutModalOpen = signal<boolean>(false);
  paymentMethod = signal<PaymentMethod>('EFECTIVO');
  amountCash = signal<number>(0);
  amountReceived = signal<number>(0);

  // Estados operativos
  isSubmitting = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Computed: Grupo activo de productos rápidos
  activeGroup = computed<QuickProductGroup | null>(() => {
    const groups = this.quickProductService.groups();
    if (groups.length === 0) return null;
    const currentId = this.activeGroupId();
    if (!currentId) return groups[0];
    return groups.find((g) => g.id === currentId) ?? groups[0];
  });

  // Computed: Resultados filtrados del catálogo
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

  // Computed: Cálculos del Cobro Inline
  amountQr = computed<number>(() => {
    if (this.paymentMethod() === 'QR') {
      return this.totalAmount();
    }
    if (this.paymentMethod() === 'MIXTO') {
      const remaining = this.totalAmount() - this.amountCash();
      return remaining > 0 ? Math.round(remaining * 100) / 100 : 0;
    }
    return 0;
  });

  changeGiven = computed<number>(() => {
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      const diff = received - this.totalAmount();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const diff = received - this.amountCash();
      return diff > 0 ? Math.round(diff * 100) / 100 : 0;
    }
    return 0;
  });

  isPaymentValid = computed<boolean>(() => {
    if (this.cartItems().length === 0 || this.totalAmount() <= 0) {
      return false;
    }
    const received = this.amountReceived() || 0;
    if (this.paymentMethod() === 'EFECTIVO') {
      return received >= this.totalAmount();
    }
    if (this.paymentMethod() === 'QR') {
      return true;
    }
    if (this.paymentMethod() === 'MIXTO') {
      const cashPortion = this.amountCash();
      return (
        cashPortion >= 0 &&
        cashPortion <= this.totalAmount() &&
        received >= cashPortion
      );
    }
    return false;
  });

  constructor() {
    // Sincronizar automáticamente porción de efectivo si cambia el total
    effect(() => {
      const total = this.totalAmount();
      const method = this.paymentMethod();

      if (method === 'EFECTIVO') {
        this.amountCash.set(total);
      } else if (method === 'MIXTO') {
        if (this.amountCash() > total) {
          this.amountCash.set(total);
        }
      }
    });
  }

  ngOnInit(): void {
    // Validar existencia de caja activa
    this.cashService.getCurrentSession().subscribe({
      next: (session) => {
        if (!session || session.status !== 'ABIERTA') {
          this.router.navigate(['/pos']);
        }
      },
      error: () => {
        this.router.navigate(['/pos']);
      }
    });

    this.loadCatalogProducts();
    this.quickProductService.loadGroups().subscribe();
  }

  loadCatalogProducts(): void {
    this.isLoadingCatalog.set(true);
    this.productService.getProducts(undefined, 'ACTIVO').subscribe({
      next: (products) => {
        this.catalogProducts.set(products);
        this.isLoadingCatalog.set(false);
      },
      error: () => {
        this.catalogProducts.set([]);
        this.isLoadingCatalog.set(false);
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
    this.errorMessage.set(null);
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

    if (this.globalDiscount() > this.subtotalPostItems()) {
      this.globalDiscount.set(this.subtotalPostItems());
    }
  }

  updateGlobalDiscount(discount: number): void {
    const safeDiscount = Math.max(0, discount || 0);
    const maxAllowed = this.subtotalPostItems();
    if (safeDiscount > maxAllowed) {
      this.errorMessage.set(`El descuento general no puede superar el subtotal de Bs ${maxAllowed.toFixed(2)}.`);
      this.globalDiscount.set(maxAllowed);
      return;
    }
    this.errorMessage.set(null);
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
    this.errorMessage.set(null);
  }

  // Grupos y Configuración de Productos Rápidos
  selectGroup(groupId: string): void {
    this.activeGroupId.set(groupId);
  }

  openConfigModal(): void {
    this.isConfigModalVisible.set(true);
  }

  closeConfigModal(): void {
    this.isConfigModalVisible.set(false);
  }

  // Métodos de Pago Inline
  setPaymentMethod(method: PaymentMethod): void {
    this.paymentMethod.set(method);
    const total = this.totalAmount();

    if (method === 'EFECTIVO') {
      this.amountCash.set(total);
      this.amountReceived.set(total);
    } else if (method === 'QR') {
      this.amountCash.set(0);
      this.amountReceived.set(0);
    } else if (method === 'MIXTO') {
      const half = Math.round((total / 2) * 100) / 100;
      this.amountCash.set(half);
      this.amountReceived.set(half);
    }
    this.errorMessage.set(null);
  }

  addReceived(amount: number): void {
    this.amountReceived.update((current) => Math.round(((current || 0) + amount) * 100) / 100);
  }

  onAmountReceivedChange(val: number | null): void {
    const parsed = val !== null && !isNaN(val) ? Number(val) : 0;
    this.amountReceived.set(parsed);
  }

  setExactAmount(): void {
    if (this.paymentMethod() === 'EFECTIVO') {
      this.amountReceived.set(this.totalAmount());
    } else if (this.paymentMethod() === 'MIXTO') {
      this.amountReceived.set(this.amountCash());
    }
  }

  onCashPortionChange(val: number): void {
    const previousCash = this.amountCash();
    const wasExact = this.amountReceived() === previousCash;
    const newCash = Math.max(0, Math.min(this.totalAmount(), val || 0));
    this.amountCash.set(newCash);
    if (wasExact || this.amountReceived() < newCash) {
      this.amountReceived.set(newCash);
    }
  }

  // Control del Modal de Cobro
  openCheckoutModal(): void {
    if (this.cartItems().length === 0 || this.totalAmount() <= 0) {
      return;
    }
    this.setPaymentMethod(this.paymentMethod());
    this.errorMessage.set(null);
    this.isCheckoutModalOpen.set(true);
  }

  closeCheckoutModal(): void {
    this.isCheckoutModalOpen.set(false);
  }

  // Navegación y Envío de Venta
  cancelAndReturn(): void {
    this.router.navigate(['/pos']);
  }

  submitSale(): void {
    if (!this.isPaymentValid() || this.isSubmitting()) {
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    let finalAmountCash = 0;
    let finalAmountQr = 0;

    if (this.paymentMethod() === 'EFECTIVO') {
      finalAmountCash = this.totalAmount();
      finalAmountQr = 0;
    } else if (this.paymentMethod() === 'QR') {
      finalAmountCash = 0;
      finalAmountQr = this.totalAmount();
    } else if (this.paymentMethod() === 'MIXTO') {
      finalAmountCash = this.amountCash();
      finalAmountQr = this.amountQr();
    }

    const payload: SaleDetailRequest = {
      items: this.checkoutItems(),
      globalDiscountAmount: this.globalDiscount(),
      paymentMethod: this.paymentMethod(),
      amountCash: finalAmountCash,
      amountQr: finalAmountQr,
      amountReceived: this.amountReceived(),
      description: `Venta POS (${this.paymentMethod()})`
    };

    this.saleService.registerSale(payload).subscribe({
      next: (sale) => {
        this.isSubmitting.set(false);
        this.isCheckoutModalOpen.set(false);
        this.cashService.getCurrentSession().subscribe();
        this.router.navigate(['/pos'], {
          state: {
            saleSuccessMessage: `Venta #${sale.saleNumber} registrada exitosamente.`
          }
        });
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const msg = err.error?.message || 'Error al registrar la venta. Inténtalo nuevamente.';
        this.errorMessage.set(msg);
      }
    });
  }
}
