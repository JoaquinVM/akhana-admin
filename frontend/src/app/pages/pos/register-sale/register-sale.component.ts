import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
  SaleDetailRequest,
  SaleItemRequest
} from '../../../core/sale/models/sale.models';
import { QuickProductsConfigModalComponent } from '../components/quick-products-config-modal/quick-products-config-modal.component';
import { CheckoutModalComponent, CheckoutPayment } from '../components/checkout-modal/checkout-modal.component';

// POS-10 / CAT-08: el precio de los productos por peso es por kilo
export const WEIGHT_STEP_KG = 0.1;
export const WEIGHT_PRESETS: ReadonlyArray<{ label: string; kilograms: number }> = [
  { label: '100 g', kilograms: 0.1 },
  { label: '1/2 libra', kilograms: 0.227 }
];

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
    CheckoutModalComponent
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
  readonly weightPresets = WEIGHT_PRESETS;

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

  // Modal de Cobro (el pago vive en CheckoutModalComponent)
  isCheckoutModalOpen = signal<boolean>(false);

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
  // Un producto por peso cuenta como un ítem, sin importar los kilos
  cartCount = computed<number>(() => {
    return this.cartItems().reduce((sum, item) => sum + (this.isWeighted(item.product) ? 1 : item.quantity), 0);
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
  isWeighted(product: Product): boolean {
    return product.saleUnit === 'KILOGRAM';
  }

  addToCart(product: Product): void {
    const existing = this.cartItems().find((it) => it.product.id === product.id);
    if (existing) {
      this.incrementQuantity(product.id);
    } else {
      const quantity = this.quantityStep(product);
      const newItem: CartItem = {
        product,
        quantity,
        unitPrice: product.sellPrice,
        unitDiscount: 0,
        unitFinalPrice: product.sellPrice,
        subtotal: Math.round(quantity * product.sellPrice * 100) / 100
      };
      this.cartItems.update((items) => [...items, newItem]);
    }
    this.productSearchTerm.set('');
    this.errorMessage.set(null);
  }

  incrementQuantity(productId: string): void {
    const item = this.cartItems().find((it) => it.product.id === productId);
    if (!item) return;
    this.updateQuantity(productId, item.quantity + this.quantityStep(item.product));
  }

  decrementQuantity(productId: string): void {
    const item = this.cartItems().find((it) => it.product.id === productId);
    if (!item) return;
    this.updateQuantity(productId, item.quantity - this.quantityStep(item.product));
  }

  // POS-10: presentaciones rápidas de un producto por peso (precio por kilo)
  applyWeightPreset(productId: string, kilograms: number): void {
    this.updateQuantity(productId, kilograms);
  }

  updateQuantity(productId: string, quantity: number): void {
    const item = this.cartItems().find((it) => it.product.id === productId);
    if (!item) return;
    const normalized = this.normalizeQuantity(item.product, Number(quantity) || 0);
    if (normalized <= 0) {
      this.removeFromCart(productId);
      return;
    }
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.product.id === productId) {
          return {
            ...it,
            quantity: normalized,
            subtotal: Math.round(normalized * it.unitFinalPrice * 100) / 100
          };
        }
        return it;
      })
    );
  }

  // Por peso: kilos con hasta 3 decimales (paso de 100 g); por unidad: cantidades enteras
  private quantityStep(product: Product): number {
    return this.isWeighted(product) ? WEIGHT_STEP_KG : 1;
  }

  private normalizeQuantity(product: Product, quantity: number): number {
    if (quantity <= 0) return 0;
    return this.isWeighted(product)
      ? Math.round(quantity * 1000) / 1000
      : Math.max(1, Math.round(quantity));
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

  // Control del Modal de Cobro
  openCheckoutModal(): void {
    if (this.cartItems().length === 0 || this.totalAmount() <= 0) {
      return;
    }
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

  submitSale(payment: CheckoutPayment): void {
    if (this.isSubmitting() || this.cartItems().length === 0 || this.totalAmount() <= 0) {
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    const payload: SaleDetailRequest = {
      items: this.checkoutItems(),
      globalDiscountAmount: this.globalDiscount(),
      paymentMethod: payment.paymentMethod,
      amountCash: payment.amountCash,
      amountQr: payment.amountQr,
      amountReceived: payment.amountReceived,
      description: `Venta POS (${payment.paymentMethod})`
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
