import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  Product, Sale, SaleItem, PaymentMethod,
  SaleStatus, SystemConfig
} from '../models/domain.model';
import { StorageService } from './storage.service';
import { CashRegisterService } from './cash-register.service';
import { AuthService } from './auth.service';

export interface CartTotals {
  grossSubtotal: number;
  itemsDiscountTotal: number;
  globalDiscount: number;
  totalDiscounts: number;
  netTotal: number;
  tax13: number;
  itemCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class PosService {
  private cartItemsSubject = new BehaviorSubject<SaleItem[]>([]);
  private globalDiscountSubject = new BehaviorSubject<number>(0);
  private customerNameSubject = new BehaviorSubject<string>('Consumidor Final');
  private customerTaxIdSubject = new BehaviorSubject<string>('');

  constructor(
    private storageService: StorageService,
    private cashRegisterService: CashRegisterService,
    private authService: AuthService
  ) {
    // Populate with the 4 items shown in the design so the terminal loads immediately with the approved products
    this.populateInitialDesignCart();
  }

  // Reactive cart streams
  get cartItems$(): Observable<SaleItem[]> { return this.cartItemsSubject.asObservable(); }
  get globalDiscount$(): Observable<number> { return this.globalDiscountSubject.asObservable(); }
  get customerName$(): Observable<string> { return this.customerNameSubject.asObservable(); }
  get customerTaxId$(): Observable<string> { return this.customerTaxIdSubject.asObservable(); }
  get sales$(): Observable<Sale[]> { return this.storageService.sales$; }

  get config(): SystemConfig {
    return this.storageService.getConfig();
  }

  get cartItems(): SaleItem[] {
    return this.cartItemsSubject.value;
  }

  get globalDiscount(): number {
    return this.globalDiscountSubject.value;
  }

  private populateInitialDesignCart(): void {
    const products = this.storageService.getProducts();
    const jamon = products.find(p => p.code === 'CHA-001');
    const aceite = products.find(p => p.code === 'ABA-001');
    const coca = products.find(p => p.code === 'BEB-001');
    const pan = products.find(p => p.code === 'PAN-001');

    const initialItems: SaleItem[] = [];

    if (jamon) {
      // 350g, base 4.50 por 100g = 15.75, desc 1.50 = 14.25
      initialItems.push({
        productId: jamon.id,
        productCode: jamon.code,
        barcode: jamon.barcode,
        productName: jamon.name,
        saleType: 'weight',
        quantity: 350,
        unitCost: jamon.cost,
        unitPrice: jamon.salePrice,
        grossSubtotal: 15.75,
        discount: 1.50,
        finalSubtotal: 14.25
      });
    }

    if (aceite) {
      initialItems.push({
        productId: aceite.id,
        productCode: aceite.code,
        barcode: aceite.barcode,
        productName: aceite.name,
        saleType: 'unit',
        quantity: 2,
        unitCost: aceite.cost,
        unitPrice: aceite.salePrice,
        grossSubtotal: 9.00,
        discount: 0,
        finalSubtotal: 9.00
      });
    }

    if (coca) {
      initialItems.push({
        productId: coca.id,
        productCode: coca.code,
        barcode: coca.barcode,
        productName: coca.name,
        saleType: 'unit',
        quantity: 3,
        unitCost: coca.cost,
        unitPrice: coca.salePrice,
        grossSubtotal: 5.40,
        discount: 0,
        finalSubtotal: 5.40
      });
    }

    if (pan) {
      initialItems.push({
        productId: pan.id,
        productCode: pan.code,
        barcode: pan.barcode,
        productName: pan.name,
        saleType: 'unit',
        quantity: 1,
        unitCost: pan.cost,
        unitPrice: pan.salePrice,
        grossSubtotal: 0.90,
        discount: 0,
        finalSubtotal: 0.90
      });
    }

    this.cartItemsSubject.next(initialItems);
    this.globalDiscountSubject.next(2.00); // Bs 2.00 Descuento global inicial
  }

  // Add product to cart
  addProductToCart(product: Product, quantityOrWeight?: number): void {
    if (!this.cashRegisterService.isRegisterOpen()) {
      throw new Error('No es posible agregar productos: La caja está cerrada. Abra la caja primero.');
    }

    const items = [...this.cartItems];
    const existingIndex = items.findIndex(item => item.productId === product.id);

    const defaultQty = product.saleType === 'weight' ? (quantityOrWeight || 100) : (quantityOrWeight || 1);

    if (existingIndex > -1) {
      const existing = items[existingIndex];
      const newQty = product.saleType === 'weight'
        ? existing.quantity + defaultQty
        : existing.quantity + 1;
      this.updateItemQuantity(existingIndex, newQty);
    } else {
      const grossSubtotal = this.calculateGrossSubtotal(product.saleType, defaultQty, product.salePrice);
      const newItem: SaleItem = {
        productId: product.id,
        productCode: product.code,
        barcode: product.barcode,
        productName: product.name,
        saleType: product.saleType,
        quantity: defaultQty,
        unitCost: product.cost,
        unitPrice: product.salePrice,
        grossSubtotal,
        discount: 0,
        finalSubtotal: grossSubtotal
      };
      items.push(newItem);
      this.cartItemsSubject.next(items);
    }
  }

  // Update item quantity or grams
  updateItemQuantity(index: number, quantity: number): void {
    const items = [...this.cartItems];
    if (index < 0 || index >= items.length) return;

    const safeQty = Math.max(items[index].saleType === 'weight' ? 10 : 1, quantity);
    const item = items[index];
    const grossSubtotal = this.calculateGrossSubtotal(item.saleType, safeQty, item.unitPrice);
    const safeDiscount = Math.min(item.discount, grossSubtotal, this.config.maxItemDiscount);
    const finalSubtotal = Number(Math.max(0, grossSubtotal - safeDiscount).toFixed(2));

    items[index] = {
      ...item,
      quantity: safeQty,
      grossSubtotal,
      discount: safeDiscount,
      finalSubtotal
    };
    this.cartItemsSubject.next(items);
  }

  // Update line item discount
  updateItemDiscount(index: number, discountAmount: number): void {
    const items = [...this.cartItems];
    if (index < 0 || index >= items.length) return;

    const maxAllowed = this.config.maxItemDiscount;
    const item = items[index];
    const requested = Math.max(0, discountAmount);

    if (requested > maxAllowed) {
      throw new Error(`El descuento máximo permitido por producto es de Bs ${maxAllowed.toFixed(2)}.`);
    }

    if (requested > item.grossSubtotal) {
      throw new Error(`El descuento (Bs ${requested}) no puede exceder el subtotal del producto (Bs ${item.grossSubtotal}).`);
    }

    const safeDiscount = Number(requested.toFixed(2));
    const finalSubtotal = Number(Math.max(0, item.grossSubtotal - safeDiscount).toFixed(2));

    items[index] = {
      ...item,
      discount: safeDiscount,
      finalSubtotal
    };
    this.cartItemsSubject.next(items);
  }

  // Update global ticket discount
  setGlobalDiscount(discountAmount: number): void {
    const maxAllowed = this.config.maxGlobalDiscount;
    const requested = Math.max(0, discountAmount);

    if (requested > maxAllowed) {
      throw new Error(`El descuento global máximo permitido es de Bs ${maxAllowed.toFixed(2)}.`);
    }

    const currentGross = this.cartItems.reduce((acc, item) => acc + item.finalSubtotal, 0);
    if (requested > currentGross) {
      throw new Error(`El descuento global no puede superar el total acumulado.`);
    }

    this.globalDiscountSubject.next(Number(requested.toFixed(2)));
  }

  setCustomer(name: string, taxId?: string): void {
    this.customerNameSubject.next(name.trim() || 'Consumidor Final');
    if (taxId !== undefined) {
      this.customerTaxIdSubject.next(taxId.trim());
    }
  }

  removeItem(index: number): void {
    const items = [...this.cartItems];
    items.splice(index, 1);
    this.cartItemsSubject.next(items);
  }

  clearCart(): void {
    this.cartItemsSubject.next([]);
    this.globalDiscountSubject.next(0);
  }

  calculateTotals(): CartTotals {
    const items = this.cartItems;
    let grossSubtotal = 0;
    let itemsDiscountTotal = 0;
    let finalItemsSum = 0;

    for (const item of items) {
      grossSubtotal += item.grossSubtotal;
      itemsDiscountTotal += item.discount;
      finalItemsSum += item.finalSubtotal;
    }

    const globalDiscount = Math.min(this.globalDiscount, finalItemsSum);
    const totalDiscounts = Number((itemsDiscountTotal + globalDiscount).toFixed(2));
    const netTotal = Number(Math.max(0, grossSubtotal - totalDiscounts).toFixed(2));
    const tax13 = Number((netTotal * this.config.defaultTaxRate).toFixed(2));

    return {
      grossSubtotal: Number(grossSubtotal.toFixed(2)),
      itemsDiscountTotal: Number(itemsDiscountTotal.toFixed(2)),
      globalDiscount: Number(globalDiscount.toFixed(2)),
      totalDiscounts,
      netTotal,
      tax13,
      itemCount: items.length
    };
  }

  // Register confirmed sale
  confirmSale(paymentMethod: PaymentMethod, cashPaid: number, qrPaid: number): Sale {
    const currentRegister = this.cashRegisterService.getCurrentRegister();
    if (!currentRegister || !currentRegister.isOpen) {
      throw new Error('No se puede completar la venta: La caja no está abierta.');
    }

    const totals = this.calculateTotals();
    if (totals.netTotal <= 0 && this.cartItems.length === 0) {
      throw new Error('El carrito de compras está vacío.');
    }

    const totalPaid = Number((cashPaid + qrPaid).toFixed(2));
    if (totalPaid < totals.netTotal) {
      throw new Error(`Monto insuficiente. Total a pagar: Bs ${totals.netTotal.toFixed(2)}, Monto recibido: Bs ${totalPaid.toFixed(2)}.`);
    }

    const changeGiven = Number(Math.max(0, cashPaid - (totals.netTotal - qrPaid)).toFixed(2));
    const actualCashCollected = Math.max(0, totals.netTotal - qrPaid);

    const currentUser = this.authService.currentUser;
    const orderNumber = 'ORD-' + Math.floor(1000 + Math.random() * 9000);

    const newSale: Sale = {
      id: 'sale-' + Date.now(),
      orderNumber,
      registerId: currentRegister.id,
      cashierId: currentUser.id,
      cashierName: currentUser.fullName,
      customerName: this.customerNameSubject.value,
      customerTaxId: this.customerTaxIdSubject.value || undefined,
      items: [...this.cartItems],
      grossSubtotal: totals.grossSubtotal,
      itemsDiscountTotal: totals.itemsDiscountTotal,
      globalDiscount: totals.globalDiscount,
      totalDiscounts: totals.totalDiscounts,
      netTotal: totals.netTotal,
      tax13: totals.tax13,
      paymentMethod,
      cashAmountReceived: cashPaid,
      qrAmountReceived: qrPaid,
      changeGiven,
      status: 'COMPLETADA',
      createdAt: new Date().toISOString()
    };

    // Save sale to storage
    const allSales = this.storageService.getSales();
    this.storageService.saveSales([newSale, ...allSales]);

    // Record tender to cash register
    this.cashRegisterService.recordSaleTender(actualCashCollected, qrPaid);

    // Deduct stock
    this.deductProductStock(newSale.items);

    // Reset cart for next sale
    this.clearCart();

    return newSale;
  }

  // Void / Anular venta
  voidSale(saleId: string, voidReason: string): Sale {
    const cleanReason = voidReason.trim();
    if (!cleanReason) {
      throw new Error('Debe especificar un motivo para anular la venta.');
    }

    const allSales = [...this.storageService.getSales()];
    const index = allSales.findIndex(s => s.id === saleId);
    if (index === -1) throw new Error('Venta no encontrada.');

    const currentSale = allSales[index];
    if (currentSale.status === 'ANULADA') {
      throw new Error('La venta ya se encuentra anulada.');
    }

    const currentUser = this.authService.currentUser;
    const voidedSale: Sale = {
      ...currentSale,
      status: 'ANULADA',
      voidReason: cleanReason,
      voidedBy: currentUser.fullName,
      voidedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    allSales[index] = voidedSale;
    this.storageService.saveSales(allSales);

    // Revert stock
    this.restoreProductStock(voidedSale.items);

    return voidedSale;
  }

  // Update sale details (with confirmation)
  updateSale(saleId: string, customerName: string, customerTaxId?: string, paymentMethod?: PaymentMethod): Sale {
    const allSales = [...this.storageService.getSales()];
    const index = allSales.findIndex(s => s.id === saleId);
    if (index === -1) throw new Error('Venta no encontrada.');

    const currentSale = allSales[index];
    const updated: Sale = {
      ...currentSale,
      customerName: customerName.trim() || currentSale.customerName,
      customerTaxId: customerTaxId !== undefined ? customerTaxId.trim() : currentSale.customerTaxId,
      paymentMethod: paymentMethod || currentSale.paymentMethod,
      updatedAt: new Date().toISOString()
    };

    allSales[index] = updated;
    this.storageService.saveSales(allSales);
    return updated;
  }

  private calculateGrossSubtotal(saleType: 'unit' | 'weight', qty: number, unitPrice: number): number {
    if (saleType === 'weight') {
      // Grams: base price is per 100g
      return Number(((qty / 100) * unitPrice).toFixed(2));
    }
    return Number((qty * unitPrice).toFixed(2));
  }

  private deductProductStock(items: SaleItem[]): void {
    const products = [...this.storageService.getProducts()];
    for (const item of items) {
      const idx = products.findIndex(p => p.id === item.productId);
      if (idx > -1 && products[idx].currentStock !== undefined) {
        const deductAmount = item.saleType === 'weight' ? item.quantity : item.quantity;
        products[idx] = {
          ...products[idx],
          currentStock: Math.max(0, (products[idx].currentStock || 0) - deductAmount)
        };
      }
    }
    this.storageService.saveProducts(products);
  }

  private restoreProductStock(items: SaleItem[]): void {
    const products = [...this.storageService.getProducts()];
    for (const item of items) {
      const idx = products.findIndex(p => p.id === item.productId);
      if (idx > -1 && products[idx].currentStock !== undefined) {
        const restoreAmount = item.saleType === 'weight' ? item.quantity : item.quantity;
        products[idx] = {
          ...products[idx],
          currentStock: (products[idx].currentStock || 0) + restoreAmount
        };
      }
    }
    this.storageService.saveProducts(products);
  }
}
