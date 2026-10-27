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
import { QuickProductGroup } from '../../../core/quick-product/models/quick-product.models';
import {
  SaleDetailRequest,
  SaleItemRequest
} from '../../../core/sale/models/sale.models';
import { QuickProductsConfigModalComponent } from '../components/quick-products-config-modal/quick-products-config-modal.component';
import { CheckoutModalComponent, CheckoutPayment } from '../components/checkout-modal/checkout-modal.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PresentationPickerComponent } from '../../../shared/components/presentation-picker/presentation-picker.component';
import { FreeLine, FreeLineFormComponent } from '../components/free-line-form/free-line-form.component';

// POS-10 / CAT-08: el precio de los productos por peso es por kilo («Otro peso»)
export const WEIGHT_STEP_KG = 0.1;

// CAT-11: una línea es un producto, o un producto por kilo con una presentación (bolsitas enteras)
export function cartKey(product: Product, presentation: ProductPresentation | null): string {
  return presentation ? `${product.id}:${presentation.id}` : product.id;
}

export interface CartItem {
  key: string;
  product: Product;
  presentation: ProductPresentation | null;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  unitFinalPrice: number;
  subtotal: number;
  // POS-11: línea libre de un producto especial, con el nombre escrito; cada una es una línea aparte
  freeName?: string | null;
}

export const AT_COST_NO_FREE_LINES = 'Una venta al costo no admite líneas libres.';

@Component({
  selector: 'app-register-sale',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    QuickProductsConfigModalComponent,
    CheckoutModalComponent,
    ModalComponent,
    PresentationPickerComponent,
    FreeLineFormComponent
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

  // Estados de catálogo y búsqueda
  catalogProducts = signal<Product[]>([]);
  productSearchTerm = signal<string>('');
  isLoadingCatalog = signal<boolean>(false);

  // Productos Rápidos
  activeGroupId = signal<string | null>(null);
  isConfigModalVisible = signal<boolean>(false);

  // CAT-11: producto por kilo esperando que se elija su presentación
  pickerProduct = signal<Product | null>(null);

  // POS-11: producto especial esperando su nombre y precio
  freeLineProduct = signal<Product | null>(null);
  private freeLineCount = 0;

  // Carrito de ventas
  cartItems = signal<CartItem[]>([]);
  globalDiscount = signal<number>(0);

  // CON-02: toda la venta al costo; clientes activos para el crédito (DEU-05)
  atCost = signal<boolean>(false);
  customers = signal<Customer[]>([]);

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

  // POS-11: con líneas libres no hay descuento general
  hasFreeLines = computed<boolean>(() => this.cartItems().some((it) => !!it.freeName));

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

  // «Otro peso»: cantidad en kilos con hasta 3 decimales
  isWeightLine(item: CartItem): boolean {
    return this.isWeighted(item.product) && !item.presentation;
  }

  lineName(item: CartItem): string {
    if (item.freeName) return item.freeName;
    return item.presentation ? `${item.product.name} · ${item.presentation.name}` : item.product.name;
  }

  // CAT-11: un producto por kilo con presentaciones abre el selector; sin ellas entra como «Otro peso»
  addToCart(product: Product): void {
    if (isSpecialProduct(product)) {
      this.productSearchTerm.set('');
      if (this.atCost()) {
        this.errorMessage.set(AT_COST_NO_FREE_LINES);
        return;
      }
      this.freeLineProduct.set(product);
      return;
    }
    if (this.isWeighted(product) && activePresentations(product).length > 0) {
      this.pickerProduct.set(product);
      this.productSearchTerm.set('');
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

  // POS-11: sin descuentos; el descuento general se quita al agregar una línea libre
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
    this.globalDiscount.set(0);
    this.errorMessage.set(null);
  }

  closeFreeLine(): void {
    this.freeLineProduct.set(null);
  }

  freeLineLabel(item: CartItem): string {
    return item.product.specialType === 'THIRD_PARTY'
      ? `De ${item.product.ownerCreditorName ?? 'tercero'}`
      : 'Sin identificar';
  }

  private addLine(product: Product, presentation: ProductPresentation | null): void {
    const key = cartKey(product, presentation);
    const existing = this.cartItems().find((it) => it.key === key);
    if (existing) {
      this.incrementQuantity(key);
    } else {
      if (this.atCost() && !this.hasCost(product)) {
        this.errorMessage.set(this.zeroCostMessage(product));
        this.productSearchTerm.set('');
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
    this.productSearchTerm.set('');
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

  // «Otro peso»: kilos con hasta 3 decimales (paso de 100 g); por unidad o presentación: enteros
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

    if (this.globalDiscount() > this.subtotalPostItems()) {
      this.globalDiscount.set(this.subtotalPostItems());
    }
  }

  updateGlobalDiscount(discount: number): void {
    if (this.hasFreeLines()) {
      this.globalDiscount.set(0);
      return;
    }
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

  removeFromCart(key: string): void {
    this.cartItems.update((items) => items.filter((it) => it.key !== key));
    if (this.globalDiscount() > this.subtotalPostItems()) {
      this.globalDiscount.set(this.subtotalPostItems());
    }
  }

  clearCart(): void {
    this.cartItems.set([]);
    this.globalDiscount.set(0);
    this.errorMessage.set(null);
  }

  // CON-02: venta al costo. El servidor pone precio = costo; aquí solo se muestra igual
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
    // Sin descuentos en la venta al costo; al volver a normal se parte de cero también
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
      description: this.atCost()
        ? `Venta al costo POS (${payment.paymentMethod})`
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
