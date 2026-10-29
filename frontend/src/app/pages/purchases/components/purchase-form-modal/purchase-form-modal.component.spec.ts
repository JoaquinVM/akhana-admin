import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PurchaseFormModalComponent, round2 } from './purchase-form-modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { ProductService } from '../../../../core/product/product.service';
import { CategoryService } from '../../../../core/category/category.service';
import { Product } from '../../../../core/product/models/product.models';
import { Purchase } from '../../../../core/purchase/models/purchase.models';

function product(overrides: Partial<Product>): Product {
  return {
    id: 'p-1', code: 'TE-1', name: 'Té verde', categoryId: 'c-1', categoryName: 'Infusiones', categoryColor: '#000',
    supplierId: 'sup-1', supplierName: 'Granel', tags: [], buyPrice: 10, sellPrice: 15, fixedProfit: 5,
    percentageProfit: 50, status: 'ACTIVO', saleUnit: 'UNIT', createdBy: 'admin', createdAt: '2026-10-01T00:00:00Z',
    ...overrides
  };
}

describe('PurchaseFormModalComponent', () => {
  let component: PurchaseFormModalComponent;
  let fixture: ComponentFixture<PurchaseFormModalComponent>;
  let purchaseService: any;
  let productService: any;

  const tea = product({});
  const nuts = product({ id: 'p-2', code: 'NUE-1', name: 'Nueces', saleUnit: 'KILOGRAM', buyPrice: 40, sellPrice: 60, supplierId: 'sup-2' });

  beforeEach(async () => {
    purchaseService = {
      registerPurchase: vi.fn().mockReturnValue(of({ id: 'pur-1' } as Purchase)),
      getPriceSuggestion: vi.fn().mockReturnValue(of({
        productId: 'p-1', currentCost: 10, currentSellPrice: 15, currentPercentageProfit: 50, newCost: 12.3, suggestedSellPrice: 19
      }))
    };
    productService = {
      getProducts: vi.fn().mockReturnValue(of([tea, nuts])),
      createProduct: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [PurchaseFormModalComponent],
      providers: [
        { provide: PurchaseService, useValue: purchaseService },
        { provide: ProductService, useValue: productService },
        { provide: CategoryService, useValue: { getCategories: vi.fn().mockReturnValue(of([])) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PurchaseFormModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  });

  it('carga el catálogo al abrir', () => {
    expect(productService.getProducts).toHaveBeenCalled();
    expect(component.products().length).toBe(2);
  });

  it('el buscador prioriza los productos del proveedor del pedido', () => {
    component.supplierId.set('sup-2');
    component.productSearch.set('e');
    expect(component.productMatches()[0].id).toBe('p-2');
  });

  it('COM-02: agrega líneas con el costo registrado y calcula subtotales en escala 2', () => {
    component.addProduct(nuts);
    component.onQuantityChange(0, 1.5);
    component.addProduct(tea);
    component.onQuantityChange(1, 24);
    component.shippingCost.set(25);

    expect(component.lines()[0].unitCost).toBe(40);
    expect(component.itemsTotal()).toBe(300);
    expect(component.totalAmount()).toBe(325);
    expect(component.needsShippingAccount()).toBe(true);
  });

  it('COM-02: seleccionar de nuevo un producto por unidad suma una unidad', () => {
    component.addProduct(tea);
    component.addProduct(tea);
    expect(component.lines().length).toBe(1);
    expect(component.lines()[0].quantity).toBe(2);
  });

  it('COM-03: muestra la alerta cuando el costo de la línea difiere del registrado', () => {
    component.addProduct(tea);
    expect(component.costDiffers(component.lines()[0])).toBe(false);
    component.onCostChange(0, 12.3);
    expect(component.costDiffers(component.lines()[0])).toBe(true);
  });

  it('COM-03: al confirmar pide el precio sugerido y lo envía con el pedido', () => {
    component.supplierId.set('sup-1');
    component.addProduct(tea);
    component.onCostChange(0, 12.3);
    component.onUpdateCostToggle(0, true);

    expect(purchaseService.getPriceSuggestion).toHaveBeenCalledWith('p-1', 12.3);
    const request = component.buildRequest();
    expect(request.items[0]).toEqual({ productId: 'p-1', quantity: 1, unitCost: 12.3, updateProductCost: true, newSellPrice: 19 });
  });

  it('COM-03: mantener el precio actual envía newSellPrice nulo; sin confirmar no actualiza el costo', () => {
    component.addProduct(tea);
    component.onCostChange(0, 12.3);
    expect(component.buildRequest().items[0].updateProductCost).toBe(false);

    component.onUpdateCostToggle(0, true);
    component.updateLine(0, { priceChoice: 'CURRENT' });
    expect(component.buildRequest().items[0].newSellPrice).toBeNull();

    component.updateLine(0, { priceChoice: 'CUSTOM', customPrice: 18.5 });
    expect(component.buildRequest().items[0].newSellPrice).toBe(18.5);
  });

  it('el envío en 0 no envía cuenta', () => {
    component.supplierId.set('sup-1');
    component.addProduct(tea);
    component.shippingAccountId.set('acc-1');
    expect(component.buildRequest().shippingAccountId).toBeNull();
  });

  it('DEU-02: el pago inicial puede ser de una cuenta o de una persona, y exige monto', () => {
    expect(component.buildRequest().initialPayment).toBeNull();

    component.initialPaymentSource.set('creditor:cre-1');
    component.initialPaymentAmount.set(40);
    expect(component.buildRequest().initialPayment).toEqual({ accountId: null, creditorId: 'cre-1', amount: 40, notes: null });

    component.initialPaymentSource.set('account:acc-1');
    expect(component.buildRequest().initialPayment?.accountId).toBe('acc-1');
  });

  it('no envía sin proveedor ni líneas y muestra el error del servidor', () => {
    component.submit();
    expect(component.formError()).toBe('Elija el proveedor del pedido.');
    component.supplierId.set('sup-1');
    component.submit();
    expect(component.formError()).toBe('El pedido debe tener al menos una línea.');
    expect(purchaseService.registerPurchase).not.toHaveBeenCalled();

    purchaseService.registerPurchase.mockReturnValue(throwError(() => ({ error: { message: 'La fecha del pedido no puede ser futura.' } })));
    component.addProduct(tea);
    component.submit();
    expect(component.formError()).toBe('La fecha del pedido no puede ser futura.');
  });

  it('emite el pedido guardado', () => {
    const saved = vi.fn();
    component.saved.subscribe(saved);
    component.supplierId.set('sup-1');
    component.addProduct(tea);
    component.submit();
    expect(saved).toHaveBeenCalledWith({ id: 'pur-1' });
  });

  it('round2 redondea a 2 decimales', () => {
    expect(round2(1.005)).toBe(1.01);
    expect(round2(0.1 + 0.2)).toBe(0.3);
  });

  it('CAT-01: crea el producto nuevo con costo 0 (el servidor avisa) y lo agrega como línea', () => {
    const created = product({ id: 'p-9', code: 'BON-1', name: 'Bonificación', buyPrice: 0, sellPrice: 12 });
    productService.createProduct.mockReturnValue(of(created));
    component.supplierId.set('sup-1');
    component.openNewProduct();
    component.patchNewProduct({ code: 'BON-1', name: 'Bonificación', categoryId: 'c-1', buyPrice: 0, sellPrice: 12 });
    component.createProduct();

    expect(component.newProductError()).toBeNull();
    expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({ buyPrice: 0, sellPrice: 12 }));
    expect(component.lines()[0].product.id).toBe('p-9');
  });

  it('sin costo escrito no crea el producto', () => {
    component.supplierId.set('sup-1');
    component.openNewProduct();
    component.patchNewProduct({ code: 'X', name: 'X', categoryId: 'c-1', buyPrice: null, sellPrice: 12 });
    component.createProduct();
    expect(component.newProductError()).toBe('Complete código, nombre, categoría, costo y precio de venta.');
    expect(productService.createProduct).not.toHaveBeenCalled();
  });

  it('el costo de la línea se envía como se escribió, para que el servidor rechace más de 2 decimales', () => {
    component.supplierId.set('sup-1');
    component.addProduct(tea);
    component.onCostChange(0, '30.555');
    expect(component.buildRequest().items[0].unitCost).toBe(30.555);
  });

  it('Enter en el buscador agrega el producto del código y nunca envía el pedido; en un campo tampoco', () => {
    component.supplierId.set('sup-1');
    fixture.detectChanges();
    const search: HTMLInputElement = fixture.nativeElement.querySelector('[data-product-search]');
    component.productSearch.set('te-1');
    search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    expect(component.lines().map(l => l.product.id)).toEqual(['p-1']);
    expect(purchaseService.registerPurchase).not.toHaveBeenCalled();

    const cost: HTMLInputElement = fixture.nativeElement.querySelector('.lines-table input[type=number]');
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    cost.dispatchEvent(enter);
    expect(enter.defaultPrevented).toBe(true);
    expect(purchaseService.registerPurchase).not.toHaveBeenCalled();
  });
});
