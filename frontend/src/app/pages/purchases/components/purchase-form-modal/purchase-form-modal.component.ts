import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { storeToday } from '../../../../shared/utils/store-date';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { ProductService } from '../../../../core/product/product.service';
import { CategoryService } from '../../../../core/category/category.service';
import { Product, SaleUnit, isSpecialProduct } from '../../../../core/product/models/product.models';
import { Category } from '../../../../core/category/models/category.models';
import { Supplier } from '../../../../core/supplier/models/supplier.models';
import { Creditor } from '../../../../core/debt/models/debt.models';
import {
  DELIVERY_STATUS_LABELS,
  DELIVERY_TYPE_LABELS,
  DeliveryStatus,
  DeliveryType,
  PaymentAccount,
  PriceSuggestion,
  Purchase,
  PurchaseItemRequest,
  PurchasePaymentRequest,
  PurchaseRequest,
  PurchaseUpdateRequest,
  paymentSourceFields
} from '../../../../core/purchase/models/purchase.models';
import { productForEnter, searchProducts, stopImplicitSubmit } from '../../../../shared/utils/product-search';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../../shared/components/search-select/search-select.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { QuantityStepperComponent, WEIGHT_STEP_KG } from '../../../../shared/components/quantity-stepper/quantity-stepper.component';
import { CashLimitWarningComponent } from '../../../../shared/components/cash-limit-warning/cash-limit-warning.component';
import { CashClosedNoteComponent } from '../../../../shared/components/cash-closed-note/cash-closed-note.component';

// COM-03: which sell price the product takes if its cost is updated
export type PriceChoice = 'SUGGESTED' | 'CURRENT' | 'CUSTOM';

export interface PurchaseLineDraft {
  product: Product;
  quantity: number;
  unitCost: number;
  updateCost: boolean;
  priceChoice: PriceChoice;
  customPrice: number | null;
  suggestion: PriceSuggestion | null;
  // COM-11: the purchase line it edits; absent for a new line
  itemId?: string | null;
}

export interface NewProductDraft {
  code: string;
  name: string;
  categoryId: string;
  saleUnit: SaleUnit;
  buyPrice: number | null;
  sellPrice: number | null;
}


export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

// Store day (yyyy-MM-dd) for the purchase date field: the server rejects a date after today in Bolivia
export function todayIso(): string {
  return storeToday();
}

@Component({
  selector: 'app-purchase-form-modal',
  standalone: true,
  imports: [IconComponent, QuantityStepperComponent, SearchSelectComponent, CommonModule, FormsModule, ModalComponent, CashLimitWarningComponent, CashClosedNoteComponent],
  templateUrl: './purchase-form-modal.component.html',
  styleUrls: ['./purchase-form-modal.component.css']
})
export class PurchaseFormModalComponent {
  private readonly toast = inject(ToastService);
  private readonly purchaseService = inject(PurchaseService);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);

  isOpen = input<boolean>(false);
  suppliers = input<Supplier[]>([]);
  accounts = input<PaymentAccount[]>([]);
  // Active people who can pay the purchase for the business (DEU-02)
  creditors = input<Creditor[]>([]);
  // COM-11: the purchase to edit; null registers a new one
  purchase = input<Purchase | null>(null);

  saved = output<Purchase>();
  close = output<void>();

  supplierId = signal<string>('');
  purchaseDate = signal<string>(todayIso());
  consignment = signal<boolean>(false);
  comment = signal<string>('');
  shippingCost = signal<number>(0);
  shippingAccountId = signal<string>('');
  // COM-07: entrega
  readonly deliveryTypes = Object.entries(DELIVERY_TYPE_LABELS) as [DeliveryType, string][];
  readonly deliveryStatuses = Object.entries(DELIVERY_STATUS_LABELS) as [DeliveryStatus, string][];
  deliveryType = signal<DeliveryType>('EN_TIENDA');
  deliveryStatus = signal<DeliveryStatus>('PENDIENTE');
  deliveryDate = signal<string>('');
  // Optional first payment (COM-05, DEU-02): 'account:<id>' or 'creditor:<id>'; empty, no payment
  initialPaymentSource = signal<string>('');
  // COM-19: what was typed in the amount, null if it was emptied; undefined until someone types, and the lines total applies
  initialPaymentAmount = signal<number | null | undefined>(undefined);
  lines = signal<PurchaseLineDraft[]>([]);
  // COM-11: every edit needs a reason
  reason = signal<string>('');

  products = signal<Product[]>([]);
  productSearch = signal<string>('');
  categories = signal<Category[]>([]);
  showNewProduct = signal<boolean>(false);
  newProduct = signal<NewProductDraft>(this.emptyNewProduct());
  newProductError = signal<string | null>(null);
  isCreatingProduct = signal<boolean>(false);

  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  readonly maxDate = todayIso();

  activeSuppliers = computed(() => this.suppliers().filter(s => s.status === 'ACTIVO'));
  // ~100 suppliers: chosen with a search box
  supplierOptions = computed<SearchSelectOption[]>(() =>
    this.activeSuppliers().map(s => ({ value: s.id, label: s.name, hint: s.code })));

  // Every search match: the purchase supplier's products first, then by relevance
  productMatches = computed(() => {
    const supplierId = this.supplierId();
    return searchProducts(this.productSearch(), this.products().filter(p => p.status !== 'ELIMINADO'))
      .sort((a, b) => Number(b.supplierId === supplierId) - Number(a.supplierId === supplierId));
  });

  // COM-13: current sell price by product; an edited line's product starts without one until the catalog loads
  private sellPrices = computed(() => new Map(this.products().map(p => [p.id, p.sellPrice])));

  itemsTotal = computed(() => round2(this.lines().reduce((sum, line) => sum + this.lineSubtotal(line), 0)));
  totalAmount = computed(() => round2(this.itemsTotal() + (Number(this.shippingCost()) || 0)));
  // COM-19: the amount shown and sent: the typed one or, until someone types, the lines total (shipping is paid apart)
  initialPaymentValue = computed<number | null>(() => {
    const typed = this.initialPaymentAmount();
    if (typed !== undefined) {
      return typed;
    }
    return this.itemsTotal() > 0 ? this.itemsTotal() : null;
  });
  // The lines total reads 300.00 in the field; a typed amount stays as it was written
  initialPaymentInput = computed<number | string | null>(() => {
    const value = this.initialPaymentValue();
    return this.initialPaymentAmount() === undefined && value !== null ? value.toFixed(2) : value;
  });
  needsShippingAccount = computed(() => (Number(this.shippingCost()) || 0) > 0);

  isEdit = computed(() => this.purchase() !== null);
  // COM-12: a different shipping (amount or account) voids its expense and records a new one
  shippingChanged = computed(() => {
    const purchase = this.purchase();
    if (!purchase) {
      return false;
    }
    const shipping = round2(Number(this.shippingCost()) || 0);
    return shipping !== round2(purchase.shippingCost)
      || (shipping > 0 && this.shippingAccountId() !== (purchase.shippingAccountId ?? ''));
  });

  // CAJ-03: what leaves Caja with this form
  private isCashAccount(accountId: string | null | undefined): boolean {
    return !!this.accounts().find(a => a.id === accountId)?.cashAccount;
  }
  paysFromCash = computed(() => {
    const source = this.initialPaymentSource();
    return source.startsWith('account:') && this.isCashAccount(source.slice('account:'.length));
  });
  // An edit that leaves the shipping as it was moves no money
  shipsFromCash = computed(() => this.needsShippingAccount() && this.isCashAccount(this.shippingAccountId())
    && (!this.isEdit() || this.shippingChanged()));
  leavesCash = computed(() => this.paysFromCash() || this.shipsFromCash());
  private cashPayment = computed(() => this.paysFromCash() ? Number(this.initialPaymentValue()) || 0 : 0);
  // COM-12 voids the shipping it replaces: if that one was from Caja, only the difference leaves
  private cashShipping = computed(() => {
    if (!this.shipsFromCash()) {
      return 0;
    }
    const purchase = this.purchase();
    const replaced = purchase && this.isCashAccount(purchase.shippingAccountId) ? purchase.shippingCost : 0;
    return Number(this.shippingCost()) - replaced;
  });
  // Null when nothing leaves: with a negative expected cash, 0 would still read as exceeding it
  cashOutflow = computed<number | null>(() => {
    const outflow = round2(this.cashPayment() + this.cashShipping());
    return outflow > 0 ? outflow : null;
  });
  cashPaymentAndShipping = computed(() => this.cashPayment() > 0 && this.cashShipping() > 0);

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.reset();
        this.loadProducts();
      }
    });
  }

  private emptyNewProduct(): NewProductDraft {
    return { code: '', name: '', categoryId: '', saleUnit: 'UNIT', buyPrice: null, sellPrice: null };
  }

  private reset(): void {
    const purchase = this.purchase();
    if (purchase) {
      this.fillFrom(purchase);
      return;
    }
    this.supplierId.set('');
    this.purchaseDate.set(todayIso());
    this.consignment.set(false);
    this.comment.set('');
    this.shippingCost.set(0);
    this.shippingAccountId.set('');
    this.deliveryType.set('EN_TIENDA');
    this.deliveryStatus.set('PENDIENTE');
    this.deliveryDate.set('');
    this.initialPaymentSource.set('');
    this.initialPaymentAmount.set(undefined);
    this.lines.set([]);
    this.reason.set('');
    this.productSearch.set('');
    this.showNewProduct.set(false);
    this.newProduct.set(this.emptyNewProduct());
    this.newProductError.set(null);
    this.formError.set(null);
    this.isSubmitting.set(false);
  }

  // COM-11: the form starts with the purchase as it is; its lines take the catalog product once it loads
  private fillFrom(purchase: Purchase): void {
    this.supplierId.set(purchase.supplierId);
    this.purchaseDate.set(purchase.purchaseDate);
    this.consignment.set(purchase.consignment);
    this.comment.set(purchase.comment ?? '');
    this.shippingCost.set(purchase.shippingCost);
    this.shippingAccountId.set(purchase.shippingAccountId ?? '');
    this.deliveryType.set(purchase.deliveryType);
    this.deliveryStatus.set(purchase.deliveryStatus);
    this.deliveryDate.set(purchase.deliveryDate ?? '');
    this.initialPaymentSource.set('');
    this.initialPaymentAmount.set(undefined);
    this.lines.set(purchase.items.map(item => ({
      product: {
        id: item.productId,
        code: item.productCode ?? '',
        name: item.productName,
        saleUnit: item.saleUnit,
        buyPrice: item.unitCost,
        sellPrice: 0
      } as Product,
      quantity: item.quantity,
      unitCost: item.unitCost,
      updateCost: false,
      priceChoice: 'SUGGESTED',
      customPrice: null,
      suggestion: null,
      itemId: item.id
    })));
    this.reason.set('');
    this.productSearch.set('');
    this.showNewProduct.set(false);
    this.newProduct.set(this.emptyNewProduct());
    this.newProductError.set(null);
    this.formError.set(null);
    this.isSubmitting.set(false);
  }

  private loadProducts(): void {
    // COM-13: the modal stays mounted between openings; the last catalog must not pass for the current prices
    this.products.set([]);
    this.productService.getProducts().subscribe({
      // POS-11: special products are not purchased
      next: products => {
        this.products.set(products.filter(p => !isSpecialProduct(p)));
        // Edited lines compare their cost with the current catalog cost (COM-03)
        const byId = new Map(products.map(p => [p.id, p]));
        this.lines.update(lines => lines.map(line => {
          const product = byId.get(line.product.id);
          return product ? { ...line, product } : line;
        }));
      },
      error: () => this.formError.set('No se pudo cargar el catálogo de productos.')
    });
  }

  isWeighted(product: Product): boolean {
    return product.saleUnit === 'KILOGRAM';
  }

  // COM-19: in whole numbers, so half a cent rounds up as on the server (scale 2 HALF_UP): 0.015 kg × 137.00 is 2.06
  lineSubtotal(line: PurchaseLineDraft): number {
    const thousandths = Math.round((Number(line.quantity) || 0) * 1000);
    const cents = Math.round((Number(line.unitCost) || 0) * 100);
    return Math.floor((thousandths * cents + 500) / 1000) / 100;
  }

  // COM-19: going back to no payment forgets the typed amount; another source keeps it
  onInitialSourceChange(source: string): void {
    this.initialPaymentSource.set(source);
    if (!source) {
      this.initialPaymentAmount.set(undefined);
    }
  }

  // COM-13: reference only; null while the catalog loads or if the product left it (deleted)
  referenceSellPrice(line: PurchaseLineDraft): number | null {
    return this.sellPrices().get(line.product.id) ?? null;
  }

  // COM-03: alert when the line cost differs from the registered cost
  costDiffers(line: PurchaseLineDraft): boolean {
    return round2(Number(line.unitCost) || 0) !== round2(line.product.buyPrice);
  }

  // Enter never submits the form (only its button does); in the product search it adds the product typed
  onFormEnter(event: Event): void {
    if (!stopImplicitSubmit(event)) {
      return;
    }
    if ((event.target as HTMLElement).hasAttribute('data-product-search')) {
      const product = productForEnter(this.productSearch(), this.products(), this.productMatches());
      if (product) {
        this.addProduct(product);
      }
    }
  }

  addProduct(product: Product): void {
    const existing = this.lines().findIndex(l => l.product.id === product.id);
    if (existing >= 0) {
      const line = this.lines()[existing];
      this.updateLine(existing, { quantity: this.isWeighted(product) ? line.quantity : line.quantity + 1 });
    } else {
      this.lines.update(lines => [...lines, {
        product,
        quantity: 1,
        unitCost: product.buyPrice,
        updateCost: false,
        priceChoice: 'SUGGESTED',
        customPrice: null,
        suggestion: null
      }]);
    }
    this.productSearch.set('');
  }

  removeLine(index: number): void {
    this.lines.update(lines => lines.filter((_, i) => i !== index));
  }

  updateLine(index: number, patch: Partial<PurchaseLineDraft>): void {
    this.lines.update(lines => lines.map((line, i) => (i === index ? { ...line, ...patch } : line)));
  }

  onQuantityChange(index: number, value: number | string): void {
    this.updateLine(index, { quantity: Number(value) || 0 });
  }

  // COM-14: 1 by unit or 100 g by kilo, as in the POS
  private quantityStep(line: PurchaseLineDraft): number {
    return this.isWeighted(line.product) ? WEIGHT_STEP_KG : 1;
  }

  private quantityAfterStep(line: PurchaseLineDraft, step: number): number {
    return Math.round(((Number(line.quantity) || 0) + step) * 1000) / 1000;
  }

  // COM-14: the − stops at the minimum; a line is only removed with its X
  canDecrement(line: PurchaseLineDraft): boolean {
    const remaining = this.quantityAfterStep(line, -this.quantityStep(line));
    return this.isWeighted(line.product) ? remaining > 0 : remaining >= 1;
  }

  incrementQuantity(index: number): void {
    const line = this.lines()[index];
    this.updateLine(index, { quantity: this.quantityAfterStep(line, this.quantityStep(line)) });
  }

  decrementQuantity(index: number): void {
    const line = this.lines()[index];
    if (this.canDecrement(line)) {
      this.updateLine(index, { quantity: this.quantityAfterStep(line, -this.quantityStep(line)) });
    }
  }

  onCostChange(index: number, value: number | string): void {
    this.updateLine(index, { unitCost: Number(value) || 0, suggestion: null });
    const line = this.lines()[index];
    if (line.updateCost && this.costDiffers(line)) {
      this.loadSuggestion(index);
    }
  }

  onUpdateCostToggle(index: number, checked: boolean): void {
    this.updateLine(index, { updateCost: checked });
    if (checked) {
      this.loadSuggestion(index);
    }
  }

  private loadSuggestion(index: number): void {
    const line = this.lines()[index];
    this.purchaseService.getPriceSuggestion(line.product.id, round2(line.unitCost)).subscribe({
      next: suggestion => {
        const current = this.lines()[index];
        if (!current || current.product.id !== suggestion.productId) {
          return;
        }
        this.updateLine(index, {
          suggestion,
          priceChoice: suggestion.suggestedSellPrice === null ? 'CURRENT' : current.priceChoice
        });
      },
      error: err => this.formError.set(err.error?.message || 'No se pudo calcular el precio sugerido.')
    });
  }

  // Sell price sent: null keeps the current one
  chosenSellPrice(line: PurchaseLineDraft): number | null {
    if (line.priceChoice === 'SUGGESTED') {
      return line.suggestion?.suggestedSellPrice ?? null;
    }
    if (line.priceChoice === 'CUSTOM') {
      return line.customPrice !== null ? round2(Number(line.customPrice)) : null;
    }
    return null;
  }

  // --- New product from a line (COM-03) ---

  openNewProduct(): void {
    if (!this.supplierId()) {
      this.formError.set('Elija primero el proveedor del pedido.');
      return;
    }
    this.formError.set(null);
    this.newProductError.set(null);
    this.newProduct.set({ ...this.emptyNewProduct(), name: this.productSearch().trim() });
    this.showNewProduct.set(true);
    if (this.categories().length === 0) {
      this.categoryService.getCategories().subscribe({
        next: categories => this.categories.set(categories.filter(c => c.status === 'ACTIVO')),
        error: () => this.newProductError.set('No se pudieron cargar las categorías.')
      });
    }
  }

  patchNewProduct(patch: Partial<NewProductDraft>): void {
    this.newProduct.update(draft => ({ ...draft, ...patch }));
  }

  createProduct(): void {
    const draft = this.newProduct();
    // CAT-01: the cost may be 0 (the server warns); the price must be above 0, which the server checks
    if (!draft.code.trim() || !draft.name.trim() || !draft.categoryId || draft.buyPrice === null
        || draft.sellPrice === null) {
      this.newProductError.set('Complete código, nombre, categoría, costo y precio de venta.');
      return;
    }
    this.isCreatingProduct.set(true);
    this.newProductError.set(null);
    this.productService.createProduct({
      code: draft.code.trim(),
      name: draft.name.trim(),
      categoryId: draft.categoryId,
      supplierId: this.supplierId(),
      tagIds: [],
      buyPrice: draft.buyPrice,
      sellPrice: draft.sellPrice,
      saleUnit: draft.saleUnit
    }).subscribe({
      next: product => {
        this.products.update(list => [...list, product]);
        this.addProduct(product);
        this.showNewProduct.set(false);
        this.isCreatingProduct.set(false);
      },
      error: err => {
        this.newProductError.set(err.error?.message || 'No se pudo crear el producto.');
        this.isCreatingProduct.set(false);
      }
    });
  }

  // --- Guardar ---

  buildRequest(): PurchaseRequest {
    const items: PurchaseItemRequest[] = this.lines().map(line => {
      const updateProductCost = line.updateCost && this.costDiffers(line);
      return {
        productId: line.product.id,
        quantity: Number(line.quantity),
        // Sent as typed: the server rejects more than 2 decimals instead of rounding them away
        unitCost: Number(line.unitCost),
        updateProductCost,
        newSellPrice: updateProductCost ? this.chosenSellPrice(line) : null,
        // COM-11: only the lines of the edited purchase carry their id
        ...(line.itemId ? { itemId: line.itemId } : {})
      };
    });
    const shipping = round2(Number(this.shippingCost()) || 0);
    const comment = this.comment().trim();
    return {
      supplierId: this.supplierId(),
      purchaseDate: this.purchaseDate() || null,
      items,
      shippingCost: shipping,
      shippingAccountId: shipping > 0 ? this.shippingAccountId() || null : null,
      consignment: this.consignment(),
      comment: comment.length > 0 ? comment : null,
      deliveryType: this.deliveryType(),
      deliveryStatus: this.deliveryStatus(),
      deliveryDate: this.deliveryDate() || null,
      initialPayment: this.buildInitialPayment()
    };
  }

  // COM-11: the same purchase without the first payment, with the reason
  buildUpdateRequest(): PurchaseUpdateRequest {
    const { initialPayment: _, ...request } = this.buildRequest();
    return { ...request, reason: this.reason().trim() };
  }

  private buildInitialPayment(): PurchasePaymentRequest | null {
    const source = this.initialPaymentSource();
    if (!source) {
      return null;
    }
    return { ...paymentSourceFields(source), amount: round2(Number(this.initialPaymentValue()) || 0), notes: null };
  }

  submit(): void {
    if (!this.supplierId()) {
      this.formError.set('Elija el proveedor del pedido.');
      return;
    }
    if (this.lines().length === 0) {
      this.formError.set('El pedido debe tener al menos una línea.');
      return;
    }
    if (this.initialPaymentSource() && !(Number(this.initialPaymentValue()) > 0)) {
      this.formError.set('Ingrese el monto del pago inicial.');
      return;
    }
    const purchase = this.purchase();
    if (purchase) {
      this.submitEdit(purchase);
      return;
    }
    this.isSubmitting.set(true);
    this.formError.set(null);
    this.purchaseService.registerPurchase(this.buildRequest()).subscribe({
      next: purchase => {
        this.toast.success(`Pedido ${purchase.purchaseNumber} registrado.`);
        this.isSubmitting.set(false);
        this.saved.emit(purchase);
      },
      error: err => {
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'No se pudo registrar el pedido.');
      }
    });
  }

  private submitEdit(purchase: Purchase): void {
    if (!this.reason().trim()) {
      this.formError.set('Escriba el motivo del cambio.');
      return;
    }
    this.isSubmitting.set(true);
    this.formError.set(null);
    this.purchaseService.updatePurchase(purchase.id, this.buildUpdateRequest()).subscribe({
      next: updated => {
        this.toast.success(`Pedido ${updated.purchaseNumber} actualizado.`);
        this.isSubmitting.set(false);
        this.saved.emit(updated);
      },
      error: err => {
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'No se pudo guardar el pedido.');
      }
    });
  }

  onClose(): void {
    if (!this.isSubmitting()) {
      this.close.emit();
    }
  }
}
