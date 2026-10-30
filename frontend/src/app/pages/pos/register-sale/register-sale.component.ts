import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CashService } from '../../../core/cash/cash.service';
import { ProductService } from '../../../core/product/product.service';
import { QuickProductService } from '../../../core/quick-product/quick-product.service';
import { SaleService } from '../../../core/sale/sale.service';
import { CustomerService } from '../../../core/debt/customer.service';
import { Customer } from '../../../core/debt/models/debt.models';
import {
  Product,
  ProductPresentation,
  activePresentations,
  isSpecialProduct,
  presentationPrice
} from '../../../core/product/models/product.models';
import {
  SaleDetailRequest,
  SaleItemRequest
} from '../../../core/sale/models/sale.models';
import { QuickProductsConfigModalComponent } from '../components/quick-products-config-modal/quick-products-config-modal.component';
import { CheckoutModalComponent, CheckoutPayment } from '../components/checkout-modal/checkout-modal.component';
import { SaleCatalogComponent } from '../components/sale-catalog/sale-catalog.component';
import { CartLineComponent } from '../components/cart-line/cart-line.component';
import { CartItem, freeLineLabel, isWeightLine, isWeightedProduct, lineName } from './cart-item';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PresentationPickerComponent } from '../../../shared/components/presentation-picker/presentation-picker.component';
import { FreeLine, FreeLineFormComponent } from '../components/free-line-form/free-line-form.component';
import { formatBs } from '../../../shared/utils/money';

// POS-10 / CAT-08: products by weight are priced per kilo («Otro peso»)
export const WEIGHT_STEP_KG = 0.1;

// CAT-11: a line is a product, or a product by kilo with a presentation (whole packs)
export function cartKey(product: Product, presentation: ProductPresentation | null): string {
  return presentation ? `${product.id}:${presentation.id}` : product.id;
}

export type { CartItem } from './cart-item';

export const AT_COST_NO_FREE_LINES = 'Una venta al costo no admite líneas libres.';

import { PosFeedbackBannerComponent } from '../components/pos-feedback-banner/pos-feedback-banner.component';

@Component({
  selector: 'app-register-sale',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    QuickProductsConfigModalComponent,
    SaleCatalogComponent,
    CartLineComponent,
    CheckoutModalComponent,
    ModalComponent,
    PresentationPickerComponent,
    FreeLineFormComponent,
    PosFeedbackBannerComponent
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
  private readonly customerService = inject(CustomerService);

  // Catalog and search state
  catalogProducts = signal<Product[]>([]);
  isLoadingCatalog = signal<boolean>(false);

  // Quick products
  isConfigModalVisible = signal<boolean>(false);

  // CAT-11: product by kilo waiting for its presentation
  pickerProduct = signal<Product | null>(null);

  // POS-11: special product waiting for its name and price
  freeLineProduct = signal<Product | null>(null);
  private freeLineCount = 0;

  // Sale cart
  cartItems = signal<CartItem[]>([]);
  globalDiscount = signal<number>(0);

  // CON-02: the whole sale at cost; active customers for credit (DEU-05)
  atCost = signal<boolean>(false);
  customers = signal<Customer[]>([]);

  // Checkout modal (the payment lives in CheckoutModalComponent)
  isCheckoutModalOpen = signal<boolean>(false);

  // Submit state
  isSubmitting = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Computed: cart totals
  // A product by weight counts as one item, whatever the kilos
  cartCount = computed<number>(() => {
    return this.cartItems().reduce((sum, item) => sum + (this.isWeightLine(item) ? 1 : item.quantity), 0);
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

  hasFreeLines = computed<boolean>(() => this.cartItems().some((it) => !!it.freeName));

  // P-44: the general discount is spread only over the normal lines; with only free lines there is none
  onlyFreeLines = computed<boolean>(() => this.cartItems().length > 0 && this.cartItems().every((it) => !!it.freeName));

  normalSubtotal = computed<number>(() => {
    const raw = this.cartItems()
      .filter((it) => !it.freeName)
      .reduce((sum, it) => sum + it.quantity * it.unitPrice - it.quantity * it.unitDiscount, 0);
    return Math.max(0, Math.round(raw * 100) / 100);
  });

  checkoutItems = computed<SaleItemRequest[]>(() => {
    return this.cartItems().map((it) => it.freeName
      ? { productId: it.product.id, quantity: it.quantity, freeName: it.freeName, freePrice: it.unitPrice }
      : {
          productId: it.product.id,
          quantity: it.quantity,
          discountPerUnit: it.unitDiscount > 0 ? it.unitDiscount : undefined,
          presentationId: it.presentation?.id
        });
  });

  ngOnInit(): void {
    // There must be an open session
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

  // Cart operations
  isWeighted(product: Product): boolean {
    return isWeightedProduct(product);
  }

  isWeightLine(item: CartItem): boolean {
    return isWeightLine(item);
  }

  lineName(item: CartItem): string {
    return lineName(item);
  }

  // CAT-11: a product by kilo with presentations opens the picker; without them it goes in as «Otro peso»
  addToCart(product: Product): void {
    if (isSpecialProduct(product)) {
      if (this.atCost()) {
        this.errorMessage.set(AT_COST_NO_FREE_LINES);
        return;
      }
      this.freeLineProduct.set(product);
      return;
    }
    if (this.isWeighted(product) && activePresentations(product).length > 0) {
      this.pickerProduct.set(product);
      return;
    }
    this.addLine(product, null);
  }

  onPresentationPicked(presentation: ProductPresentation | null): void {
    const product = this.pickerProduct();
    this.pickerProduct.set(null);
    if (product) {
      this.addLine(product, presentation);
    }
  }

  closePicker(): void {
    this.pickerProduct.set(null);
  }

  // POS-11: a free line has no unit discount and stays out of the general discount (P-44)
  onFreeLineConfirmed(line: FreeLine): void {
    const product = this.freeLineProduct();
    this.freeLineProduct.set(null);
    if (!product) return;
    this.freeLineCount += 1;
    const newItem: CartItem = {
      key: `${product.id}:free:${this.freeLineCount}`,
      product,
      presentation: null,
      quantity: 1,
      unitPrice: line.price,
      unitDiscount: 0,
      unitFinalPrice: line.price,
      subtotal: line.price,
      freeName: line.name
    };
    this.cartItems.update((items) => [...items, newItem]);
    this.errorMessage.set(null);
  }

  closeFreeLine(): void {
    this.freeLineProduct.set(null);
  }

  freeLineLabel(item: CartItem): string {
    return freeLineLabel(item);
  }

  private addLine(product: Product, presentation: ProductPresentation | null): void {
    const key = cartKey(product, presentation);
    const existing = this.cartItems().find((it) => it.key === key);
    if (existing) {
      this.incrementQuantity(key);
    } else {
      if (this.atCost() && !this.hasCost(product)) {
        this.errorMessage.set(this.zeroCostMessage(product));
          return;
      }
      const quantity = presentation || !this.isWeighted(product) ? 1 : WEIGHT_STEP_KG;
      const price = this.priceFor(product, presentation);
      const newItem: CartItem = {
        key,
        product,
        presentation,
        quantity,
        unitPrice: price,
        unitDiscount: 0,
        unitFinalPrice: price,
        subtotal: Math.round(quantity * price * 100) / 100
      };
      this.cartItems.update((items) => [...items, newItem]);
    }
    this.errorMessage.set(null);
  }

  incrementQuantity(key: string): void {
    const item = this.cartItems().find((it) => it.key === key);
    if (!item) return;
    this.updateQuantity(key, item.quantity + this.quantityStep(item));
  }

  decrementQuantity(key: string): void {
    const item = this.cartItems().find((it) => it.key === key);
    if (!item) return;
    this.updateQuantity(key, item.quantity - this.quantityStep(item));
  }

  updateQuantity(key: string, quantity: number): void {
    const item = this.cartItems().find((it) => it.key === key);
    if (!item) return;
    const normalized = this.normalizeQuantity(item, Number(quantity) || 0);
    if (normalized <= 0) {
      this.removeFromCart(key);
      return;
    }
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.key === key) {
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

  // «Otro peso»: kilos with up to 3 decimals (100 g step); by unit or presentation: whole numbers
  private quantityStep(item: CartItem): number {
    return this.isWeightLine(item) ? WEIGHT_STEP_KG : 1;
  }

  private normalizeQuantity(item: CartItem, quantity: number): number {
    if (quantity <= 0) return 0;
    return this.isWeightLine(item)
      ? Math.round(quantity * 1000) / 1000
      : Math.max(1, Math.round(quantity));
  }

  updateUnitDiscount(key: string, discount: number): void {
    const safeDiscount = Math.max(0, discount || 0);
    this.cartItems.update((items) =>
      items.map((it) => {
        if (it.key === key) {
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

    if (this.globalDiscount() > this.normalSubtotal()) {
      this.globalDiscount.set(this.normalSubtotal());
    }
  }

  updateGlobalDiscount(discount: number): void {
    const safeDiscount = Math.max(0, discount || 0);
    const maxAllowed = this.normalSubtotal();
    if (safeDiscount > maxAllowed) {
      this.errorMessage.set(this.hasFreeLines()
        ? `El descuento general no puede superar el subtotal de los productos normales (${formatBs(maxAllowed)}).`
        : `El descuento general no puede superar el subtotal de ${formatBs(maxAllowed)}.`);
      this.globalDiscount.set(maxAllowed);
      return;
    }
    this.errorMessage.set(null);
    this.globalDiscount.set(Math.round(safeDiscount * 100) / 100);
  }

  removeFromCart(key: string): void {
    this.cartItems.update((items) => items.filter((it) => it.key !== key));
    if (this.globalDiscount() > this.normalSubtotal()) {
      this.globalDiscount.set(this.normalSubtotal());
    }
  }

  clearCart(): void {
    this.cartItems.set([]);
    this.globalDiscount.set(0);
    this.errorMessage.set(null);
  }

  // CON-02: at-cost sale. The server sets price = cost; here it is only shown the same way
  onAtCostToggle(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.setAtCost(input.checked);
    input.checked = this.atCost();
  }

  setAtCost(enabled: boolean): void {
    if (enabled) {
      if (this.hasFreeLines()) {
        this.errorMessage.set(AT_COST_NO_FREE_LINES);
        return;
      }
      const withoutCost = this.cartItems().find((it) => !this.hasCost(it.product));
      if (withoutCost) {
        this.errorMessage.set(this.zeroCostMessage(withoutCost.product));
        return;
      }
      if (this.customers().length === 0) {
        this.customerService.getCustomers('ACTIVO').subscribe({
          next: (customers) => this.customers.set(customers),
          error: () => this.customers.set([])
        });
      }
    }
    this.atCost.set(enabled);
    this.errorMessage.set(null);
    // No discounts on an at-cost sale; going back to normal also starts from zero
    this.globalDiscount.set(0);
    this.cartItems.update((items) =>
      items.map((it) => {
        const price = this.priceFor(it.product, it.presentation);
        return {
          ...it,
          unitPrice: price,
          unitDiscount: 0,
          unitFinalPrice: price,
          subtotal: Math.round(it.quantity * price * 100) / 100
        };
      })
    );
  }

  private priceFor(product: Product, presentation: ProductPresentation | null): number {
    if (presentation) return presentationPrice(presentation, product, this.atCost());
    return this.atCost() ? product.buyPrice : product.sellPrice;
  }

  private hasCost(product: Product): boolean {
    return (product.buyPrice ?? 0) > 0;
  }

  private zeroCostMessage(product: Product): string {
    return `'${product.name}' no tiene costo registrado; no se puede vender al costo.`;
  }

  // Quick product groups and settings
  openConfigModal(): void {
    this.isConfigModalVisible.set(true);
  }

  closeConfigModal(): void {
    this.isConfigModalVisible.set(false);
  }

  // Checkout modal control
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

  // Navigation and sale submit
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
      // P-39: the at-cost sale comment replaces the automatic description
      description: this.atCost()
        ? payment.comment || `Venta al costo POS (${payment.paymentMethod})`
        : `Venta POS (${payment.paymentMethod})`,
      saleType: this.atCost() ? 'AL_COSTO' : 'NORMAL',
      customerId: this.atCost() ? payment.customerId : null,
      creditAmount: this.atCost() ? payment.creditAmount : 0
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
