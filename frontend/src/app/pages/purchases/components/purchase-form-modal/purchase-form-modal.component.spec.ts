import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { PurchaseFormModalComponent, PurchaseLineDraft, round2 } from './purchase-form-modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { ProductService } from '../../../../core/product/product.service';
import { CategoryService } from '../../../../core/category/category.service';
import { Product } from '../../../../core/product/models/product.models';
import { Purchase } from '../../../../core/purchase/models/purchase.models';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { CashService } from '../../../../core/cash/cash.service';

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
  let cashService: any;

  const tea = product({});
  const nuts = product({ id: 'p-2', code: 'NUE-1', name: 'Nueces', saleUnit: 'KILOGRAM', buyPrice: 40, sellPrice: 60, supplierId: 'sup-2' });

  beforeEach(async () => {
    purchaseService = {
      registerPurchase: vi.fn().mockReturnValue(of({ id: 'pur-1' } as Purchase)),
      updatePurchase: vi.fn().mockReturnValue(of({ id: 'pur-7', purchaseNumber: 'PED-000007' } as Purchase)),
      getPriceSuggestion: vi.fn().mockReturnValue(of({
        productId: 'p-1', currentCost: 10, currentSellPrice: 15, currentPercentageProfit: 50, newCost: 12.3, suggestedSellPrice: 19
      }))
    };
    productService = {
      getProducts: vi.fn().mockReturnValue(of([tea, nuts])),
      createProduct: vi.fn()
    };

    cashService = {
      currentSession: signal<any>({ id: 's1', expectedCash: 300 }),
      getCurrentSession: vi.fn().mockReturnValue(of({ id: 's1', expectedCash: 300 }))
    };

    await TestBed.configureTestingModule({
      imports: [PurchaseFormModalComponent],
      providers: [
        { provide: CashService, useValue: cashService },
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

  it('el buscador muestra todos los resultados y busca por proveedor, sin tildes', () => {
    const many = Array.from({ length: 10 }, (_, i) =>
      product({ id: `g-${i}`, code: `GR-${i}`, name: `Almendra ${i}`, supplierName: 'Frutos Andinos' }));
    component.products.set([tea, nuts, ...many]);
    component.productSearch.set('andinos');
    expect(component.productMatches().length).toBe(10);
    component.productSearch.set('te verde');
    expect(component.productMatches()).toEqual([tea]);
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

  it('al registrar el pedido muestra el aviso de éxito', () => {
    purchaseService.registerPurchase.mockReturnValue(of({ id: 'pur-9', purchaseNumber: 'PED-000029' } as Purchase));
    component.supplierId.set('sup-1');
    component.addProduct(tea);
    component.submit();
    expect(TestBed.inject(ToastService).toasts().map(t => t.text)).toContain('Pedido PED-000029 registrado.');
  });
  describe('COM-13: precio de venta como referencia', () => {
    const sellPrices = (): string[] => {
      fixture.detectChanges();
      return Array.from(fixture.nativeElement.querySelectorAll('.line-sell-price'))
        .map((el: any) => el.textContent.trim());
    };

    it('el buscador lo muestra aparte del costo, por kilo si se vende por kilo', () => {
      const resultOf = (search: string): { meta: string; sellPrice: string } => {
        component.productSearch.set(search);
        fixture.detectChanges();
        const text = (selector: string): string =>
          fixture.nativeElement.querySelector(selector).textContent.replace(/\s+/g, ' ').trim();
        return { meta: text('.match-meta'), sellPrice: text('.match-sell-price') };
      };

      expect(resultOf('te-1').sellPrice).toBe('Venta Bs 15.00');
      expect(resultOf('te-1').meta).toContain('costo Bs 10.00');
      expect(resultOf('te-1').meta).not.toContain('Bs 15.00');
      expect(resultOf('nue-1').sellPrice).toBe('Venta Bs 60.00/kg');
      expect(resultOf('nue-1').meta).toContain('costo Bs 40.00/kg');
      expect(resultOf('nue-1').meta).not.toContain('Bs 60.00');
    });

    it('la columna muestra el precio de una unidad o un kilo, sin multiplicar por la cantidad', () => {
      component.addProduct(tea);
      component.addProduct(nuts);
      expect(sellPrices()).toEqual(['Bs 15.00', 'Bs 60.00/kg']);
      component.onQuantityChange(0, 2);
      expect(sellPrices()).toEqual(['Bs 15.00', 'Bs 60.00/kg']);
    });

    it('la columna es texto, no un campo', () => {
      component.addProduct(tea);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.line-sell-price input')).toBeNull();
    });

    it('un producto creado desde el pedido muestra su precio', () => {
      component.supplierId.set('sup-1');
      component.openNewProduct();
      component.patchNewProduct({ code: 'NV-1', name: 'Nuevo', categoryId: 'c-1', buyPrice: 12, sellPrice: 20 });
      productService.createProduct.mockReturnValue(of(product({ id: 'p-9', code: 'NV-1', name: 'Nuevo', buyPrice: 12, sellPrice: 20 })));
      component.createProduct();
      expect(sellPrices()).toEqual(['Bs 20.00']);
    });

    it('un producto con precio 0 muestra Bs 0.00, porque el precio se conoce', () => {
      const free = product({ id: 'p-0', code: 'GR-0', name: 'Muestra', sellPrice: 0 });
      component.products.update(list => [...list, free]);
      component.addProduct(free);
      expect(sellPrices()).toEqual(['Bs 0.00']);
    });

    it('COM-03: la alerta de costo no mueve la referencia', () => {
      component.addProduct(tea);
      component.onCostChange(0, 12.3);
      expect(sellPrices()).toEqual(['Bs 15.00']);
      component.onUpdateCostToggle(0, true);
      expect(component.chosenSellPrice(component.lines()[0])).toBe(19);
      expect(sellPrices()).toEqual(['Bs 15.00']);
      component.updateLine(0, { priceChoice: 'CUSTOM', customPrice: 25 });
      expect(sellPrices()).toEqual(['Bs 15.00']);
      expect(fixture.nativeElement.querySelector('.price-title').textContent).toContain('actual Bs 15.00');
      expect(component.chosenSellPrice(component.lines()[0])).toBe(25);
    });

    it('la alerta y la fila vacía ocupan todo el ancho de la tabla', () => {
      fixture.detectChanges();
      const columns = String(fixture.nativeElement.querySelectorAll('.lines-table th').length);
      expect(fixture.nativeElement.querySelector('.empty-lines').getAttribute('colspan')).toBe(columns);
      component.addProduct(tea);
      component.onCostChange(0, 12.3);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.cost-alert-row td').getAttribute('colspan')).toBe(columns);
    });

    it('no cambia lo que se envía ni los totales', () => {
      component.addProduct(tea);
      component.onQuantityChange(0, 2);
      expect(component.lineSubtotal(component.lines()[0])).toBe(20);
      expect(component.itemsTotal()).toBe(20);
      expect(component.buildRequest().items).toEqual([
        { productId: 'p-1', quantity: 2, unitCost: 10, updateProductCost: false, newSellPrice: null }
      ]);
    });

    it('al editar muestra el precio del catálogo, y — si no cargó o el producto ya no está', () => {
      const editable = {
        id: 'pur-8', purchaseNumber: 'PED-000008', supplierId: 'sup-1', purchaseDate: '2026-10-20', shippingCost: 0,
        consignment: false, deliveryType: 'EN_TIENDA', deliveryStatus: 'PENDIENTE', paidAmount: 0,
        items: [
          { id: 'it-1', productId: 'p-1', productName: 'Té verde', productCode: 'TE-1', saleUnit: 'UNIT', quantity: 1, unitCost: 8 },
          { id: 'it-2', productId: 'p-gone', productName: 'Eliminado', productCode: 'EL-1', saleUnit: 'UNIT', quantity: 1, unitCost: 5 }
        ]
      } as unknown as Purchase;
      const catalog = new Subject<Product[]>();
      productService.getProducts.mockReturnValue(catalog);
      fixture.componentRef.setInput('isOpen', false);
      fixture.detectChanges();
      fixture.componentRef.setInput('purchase', editable);
      fixture.componentRef.setInput('isOpen', true);
      expect(sellPrices()).toEqual(['—', '—']);

      catalog.next([tea, nuts]);
      expect(sellPrices()).toEqual(['Bs 15.00', '—']);
      expect(component.buildUpdateRequest().items.map(i => i.newSellPrice)).toEqual([null, null]);
    });

    describe('al volver a abrir el formulario', () => {
      const reopen = (catalog: Subject<Product[]>): void => {
        productService.getProducts.mockReturnValue(catalog);
        fixture.componentRef.setInput('isOpen', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('isOpen', true);
        fixture.detectChanges();
      };

      it('no muestra el catálogo anterior en el buscador mientras carga, y luego el precio nuevo', () => {
        const catalog = new Subject<Product[]>();
        reopen(catalog);
        component.productSearch.set('te-1');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.match-sell-price')).toBeNull();

        catalog.next([{ ...tea, sellPrice: 19 }]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.match-sell-price').textContent.replace(/\s+/g, ' ').trim()).toBe('Venta Bs 19.00');
      });

      it('una línea muestra — si el catálogo no carga, junto al error', () => {
        const catalog = new Subject<Product[]>();
        reopen(catalog);
        component.lines.set([{ product: tea, quantity: 1, unitCost: 10, updateCost: false, priceChoice: 'SUGGESTED', customPrice: null, suggestion: null }]);
        expect(sellPrices()).toEqual(['—']);

        catalog.error(new Error('sin conexión'));
        expect(sellPrices()).toEqual(['—']);
        expect(component.formError()).toBe('No se pudo cargar el catálogo de productos.');
      });
    });

    it('el buscador conserva código y proveedor, con el precio aparte, y la columna va después del subtotal', () => {
      component.productSearch.set('te-1');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.match-meta').textContent.trim())
        .toBe('TE-1 · Granel · costo Bs 10.00');
      expect(fixture.nativeElement.querySelector('.match-sell-price').textContent.replace(/\s+/g, ' ').trim()).toBe('Venta Bs 15.00');
      const headers = Array.from(fixture.nativeElement.querySelectorAll('.lines-table th'))
        .map((th: any) => th.textContent.trim());
      expect(headers.slice(2, 5)).toEqual(['Costo unitario (Bs)', 'Subtotal', 'Precio de venta']);
      component.addProduct(tea);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.lines-table tbody tr').children[4].classList).toContain('line-sell-price');
    });
  });

  describe('COM-14: botones de cantidad', () => {
    const steppers = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('app-quantity-stepper'));
    const minus = (stepper: HTMLElement) => stepper.querySelectorAll<HTMLButtonElement>('.qty-btn')[0];
    const plus = (stepper: HTMLElement) => stepper.querySelectorAll<HTMLButtonElement>('.qty-btn')[1];

    it('por unidad suma y resta 1, y recalcula el subtotal y el total', () => {
      component.addProduct(tea);
      fixture.detectChanges();

      plus(steppers()[0]).click();
      plus(steppers()[0]).click();
      expect(component.lines()[0].quantity).toBe(3);
      expect(component.itemsTotal()).toBe(30);

      fixture.detectChanges();
      minus(steppers()[0]).click();
      expect(component.lines()[0].quantity).toBe(2);
      expect(component.lineSubtotal(component.lines()[0])).toBe(20);
    });

    it('por kilo suma y resta 0,1 kg, sin decimales de más', () => {
      component.addProduct(nuts);
      component.onQuantityChange(0, 1.5);
      component.incrementQuantity(0);
      expect(component.lines()[0].quantity).toBe(1.6);
      component.decrementQuantity(0);
      component.decrementQuantity(0);
      expect(component.lines()[0].quantity).toBe(1.4);

      component.onQuantityChange(0, 0.3);
      component.decrementQuantity(0);
      expect(component.lines()[0].quantity).toBe(0.2);
      component.onQuantityChange(0, 0.1);
      component.incrementQuantity(0);
      component.incrementQuantity(0);
      expect(component.lines()[0].quantity).toBe(0.3);
    });

    it('un producto entra con la misma cantidad de hoy: 1 unidad o 1 kg', () => {
      component.addProduct(tea);
      component.addProduct(nuts);
      expect(component.lines().map(l => l.quantity)).toEqual([1, 1]);
    });

    it('el − se deshabilita en el mínimo por unidad y la línea no se quita', () => {
      component.addProduct(tea);
      fixture.detectChanges();
      expect(minus(steppers()[0]).disabled).toBe(true);

      minus(steppers()[0]).click();
      component.decrementQuantity(0);
      expect(component.lines().length).toBe(1);
      expect(component.lines()[0].quantity).toBe(1);

      plus(steppers()[0]).click();
      fixture.detectChanges();
      expect(minus(steppers()[0]).disabled).toBe(false);
    });

    it('por kilo, el − se deshabilita cuando restar dejaría 0 o menos', () => {
      component.addProduct(nuts);
      const canDecrementAt = (quantity: number) => {
        component.onQuantityChange(0, quantity);
        return component.canDecrement(component.lines()[0]);
      };
      expect(canDecrementAt(0.1)).toBe(false);
      expect(canDecrementAt(0.05)).toBe(false);
      expect(canDecrementAt(0.15)).toBe(true);
      component.decrementQuantity(0);
      expect(component.lines()[0].quantity).toBe(0.05);
    });

    it('vaciar el campo o escribir 0 no quita la línea, deshabilita el − y el + sigue desde 0', () => {
      component.addProduct(tea);
      component.addProduct(nuts);
      component.onQuantityChange(0, '');
      component.onQuantityChange(1, 0);

      expect(component.lines().map(l => l.quantity)).toEqual([0, 0]);
      expect(component.lines().map(l => component.canDecrement(l))).toEqual([false, false]);

      component.incrementQuantity(0);
      component.incrementQuantity(1);
      expect(component.lines().map(l => l.quantity)).toEqual([1, 0.1]);
    });

    it('se sigue escribiendo a mano, y los botones siguen desde ese valor', () => {
      component.addProduct(tea);
      component.addProduct(nuts);
      component.onQuantityChange(0, 24);
      component.onQuantityChange(1, 2.125);
      component.incrementQuantity(0);
      component.decrementQuantity(1);
      expect(component.lines().map(l => l.quantity)).toEqual([25, 2.025]);
    });

    it('apretar los botones no envía el pedido, y la X sí quita la línea', () => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      fixture.detectChanges();
      plus(steppers()[0]).click();
      expect(purchaseService.registerPurchase).not.toHaveBeenCalled();

      component.removeLine(0);
      expect(component.lines()).toEqual([]);
    });

    it('Enter en el campo de cantidad, dentro del control, no envía el pedido', () => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      fixture.detectChanges();
      const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

      steppers()[0].querySelector('.qty-input')!.dispatchEvent(enter);

      expect(enter.defaultPrevented).toBe(true);
      expect(purchaseService.registerPurchase).not.toHaveBeenCalled();
    });

    it('la petición sigue teniendo la misma forma', () => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      component.incrementQuantity(0);
      expect(component.buildRequest().items).toEqual([
        { productId: 'p-1', quantity: 2, unitCost: 10, updateProductCost: false, newSellPrice: null }
      ]);
    });

    it('cantidad, costo unitario, envío y monto pagado no muestran flechitas; la unidad va junto a la cantidad', () => {
      component.addProduct(tea);
      component.initialPaymentSource.set('account:acc-1');
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;

      expect(el.querySelector('#purchase-shipping')!.classList).toContain('no-spinner');
      expect(el.querySelector('#purchase-initial-amount')!.classList).toContain('no-spinner');
      expect(el.querySelector('app-quantity-stepper .qty-input')).not.toBeNull();
      expect(el.querySelector('.lines-table input.input-number')!.classList).toContain('no-spinner');
      expect(steppers()[0].nextElementSibling!.classList).toContain('unit-hint');
    });
  });

  describe('COM-19: el monto del pago inicial viene con la suma de las líneas', () => {
    const amountInput = (): HTMLInputElement => fixture.nativeElement.querySelector('#purchase-initial-amount');
    const hint = (): string => amountInput().parentElement!.querySelector('.form-hint')!.textContent!.trim();

    const render = async (): Promise<void> => {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    };

    const type = async (value: string): Promise<void> => {
      const input = amountInput();
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await render();
    };

    // Lines of Bs 300.00 and shipping of Bs 25.00
    const orderOf300 = (): void => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      component.updateLine(0, { quantity: 30 });
      component.shippingCost.set(25);
      component.shippingAccountId.set('acc-2');
    };

    it('al elegir el origen viene lleno con la suma de las líneas, sin el envío, y se envía eso', async () => {
      orderOf300();
      await render();
      expect(amountInput()).toBeNull();

      component.onInitialSourceChange('account:acc-1');
      await render();

      expect(amountInput().value).toBe('300.00');
      expect(component.buildRequest().initialPayment).toEqual({ accountId: 'acc-1', creditorId: null, amount: 300, notes: null });
      expect(component.buildRequest().shippingCost).toBe(25);
    });

    it('sin origen no se envía pago', async () => {
      orderOf300();
      await render();
      expect(component.buildRequest().initialPayment).toBeNull();
    });

    it('si paga una persona viene igual y se envía con la persona', async () => {
      orderOf300();
      component.onInitialSourceChange('creditor:cre-1');
      await render();

      expect(amountInput().value).toBe('300.00');
      expect(component.buildRequest().initialPayment).toEqual({ accountId: null, creditorId: 'cre-1', amount: 300, notes: null });
    });

    it('sin tocarlo sigue a las líneas y no al envío', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      component.addProduct(nuts);
      component.updateLine(1, { quantity: 0.5 });
      await render();
      expect(amountInput().value).toBe('320.00');

      component.incrementQuantity(0);
      await render();
      expect(amountInput().value).toBe('330.00');

      component.onCostChange(0, 20);
      await render();
      expect(amountInput().value).toBe('640.00');

      component.removeLine(1);
      await render();
      expect(amountInput().value).toBe('620.00');
      expect(component.buildRequest().initialPayment?.amount).toBe(620);

      component.shippingCost.set(90);
      await render();
      expect(amountInput().value).toBe('620.00');
    });

    it('sin líneas o con líneas en 0 queda vacío, nunca 0.00, y registrar pide el monto', async () => {
      component.supplierId.set('sup-1');
      component.onInitialSourceChange('account:acc-1');
      await render();
      expect(amountInput().value).toBe('');

      component.addProduct(tea);
      component.onCostChange(0, 0);
      await render();
      expect(amountInput().value).toBe('');

      component.submit();
      expect(component.formError()).toBe('Ingrese el monto del pago inicial.');
      expect(purchaseService.registerPurchase).not.toHaveBeenCalled();

      component.onCostChange(0, 10);
      await render();
      expect(amountInput().value).toBe('10.00');
    });

    it('al entrar al campo selecciona el contenido, y entrar sin escribir no cambia nada', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();
      const select = vi.spyOn(amountInput(), 'select');

      amountInput().dispatchEvent(new Event('focus'));
      amountInput().dispatchEvent(new Event('blur'));
      await render();
      expect(select).toHaveBeenCalled();

      component.incrementQuantity(0);
      await render();
      expect(amountInput().value).toBe('310.00');
    });

    it('lo escrito no cambia solo y la nota sigue con el total al día', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      await type('100');
      component.incrementQuantity(0);
      await render();

      expect(amountInput().value).toBe('100');
      expect(component.buildRequest().initialPayment?.amount).toBe(100);
      expect(hint()).toContain('310.00');
    });

    it('un monto escrito igual al total tampoco sigue a las líneas', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      await type('300');
      component.incrementQuantity(0);
      await render();

      expect(component.buildRequest().initialPayment?.amount).toBe(300);
    });

    it('vaciado queda vacío y registrar pide el monto', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      await type('');
      component.incrementQuantity(0);
      await render();

      expect(amountInput().value).toBe('');
      component.submit();
      expect(component.formError()).toBe('Ingrese el monto del pago inicial.');
      expect(purchaseService.registerPurchase).not.toHaveBeenCalled();
    });

    it('otro origen conserva el monto; volver a «Sin pago» lo olvida y viene lleno de nuevo', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();
      await type('100');

      component.onInitialSourceChange('creditor:cre-1');
      await render();
      expect(amountInput().value).toBe('100');

      component.onInitialSourceChange('');
      await render();
      expect(amountInput()).toBeNull();

      component.onInitialSourceChange('account:acc-1');
      await render();
      expect(amountInput().value).toBe('300.00');
    });

    it('al volver a abrir no queda el monto escrito antes', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();
      await type('100');

      fixture.componentRef.setInput('isOpen', false);
      await render();
      fixture.componentRef.setInput('isOpen', true);
      await render();
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      expect(amountInput().value).toBe('300.00');
    });

    it('si el servidor rechaza, se ve su mensaje y el formulario conserva todo', async () => {
      const message = 'Los movimientos de la cuenta Caja requieren una caja abierta.';
      purchaseService.registerPurchase.mockReturnValue(throwError(() => ({ error: { message } })));
      orderOf300();
      component.onInitialSourceChange('account:caja');
      await render();

      component.submit();
      await render();

      expect(component.formError()).toBe(message);
      expect(component.lines().length).toBe(1);
      expect(amountInput().value).toBe('300.00');
    });

    it('registrar sin tocarlo envía la suma', async () => {
      orderOf300();
      component.onInitialSourceChange('account:acc-1');
      await render();

      component.submit();

      expect(purchaseService.registerPurchase.mock.calls[0][0].initialPayment.amount).toBe(300);
    });

    it.each([
      [0.015, 137, 2.06],
      [0.022, 112.5, 2.48],
      [0.025, 170.6, 4.27],
      [0.036, 138.75, 5],
      [1.5, 40, 60]
    ])('%s kg × Bs %s da Bs %s, igual que el servidor', async (quantity, unitCost, expected) => {
      component.supplierId.set('sup-1');
      component.addProduct(nuts);
      component.updateLine(0, { quantity, unitCost });
      component.onInitialSourceChange('account:acc-1');
      await render();

      expect(component.lineSubtotal(component.lines()[0])).toBe(expected);
      expect(component.itemsTotal()).toBe(expected);
      expect(component.buildRequest().initialPayment?.amount).toBe(expected);
    });

    it('el subtotal es el del redondeo HALF_UP exacto en cantidades con 3 decimales y costos con 2', () => {
      const line = { product: nuts } as PurchaseLineDraft;
      for (let thousandths = 1; thousandths <= 5000; thousandths += 1) {
        for (let cents = 1; cents <= 20000; cents += 97) {
          const exact = Number((BigInt(thousandths) * BigInt(cents) + 500n) / 1000n) / 100;
          const subtotal = component.lineSubtotal({ ...line, quantity: thousandths / 1000, unitCost: cents / 100 });
          if (subtotal !== exact) {
            throw new Error(`${thousandths / 1000} × ${cents / 100}: ${subtotal} en vez de ${exact}`);
          }
        }
      }
    });
  });

  describe('COM-11: edición', () => {
    const editable = {
      id: 'pur-7', purchaseNumber: 'PED-000007', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-25',
      status: 'REGISTRADO', itemsTotal: 300, shippingCost: 25, totalAmount: 325, paidAmount: 300, balance: 0,
      paymentStatus: 'PAGADO', consignment: false, comment: 'Semanal', shippingAccountId: 'acc-bisa',
      shippingAccountName: 'Bisa', deliveryType: 'EN_TIENDA', deliveryStatus: 'ENTREGADO', deliveryDate: '2026-10-26',
      createdBy: 'operator', createdAt: '2026-10-25T12:00:00Z', updatedBy: null, updatedAt: null, voidedBy: null,
      voidedAt: null, voidReason: null, payments: [], revisions: [],
      items: [
        { id: 'it-1', productId: 'p-1', productName: 'Té verde', productCode: 'TE-1', saleUnit: 'UNIT', quantity: 24,
          unitCost: 10, subtotal: 240, previousUnitCost: 10, costUpdated: false },
        { id: 'it-2', productId: 'p-2', productName: 'Nueces', productCode: 'NUE-1', saleUnit: 'KILOGRAM', quantity: 1.5,
          unitCost: 40, subtotal: 60, previousUnitCost: 40, costUpdated: false }
      ]
    } as Purchase;

    beforeEach(() => {
      fixture.componentRef.setInput('isOpen', false);
      fixture.detectChanges();
      fixture.componentRef.setInput('purchase', editable);
      fixture.componentRef.setInput('isOpen', true);
      fixture.detectChanges();
    });

    it('carga el pedido con sus líneas y el producto del catálogo, sin pago inicial', () => {
      expect(component.isEdit()).toBe(true);
      expect(component.supplierId()).toBe('sup-1');
      expect(component.shippingAccountId()).toBe('acc-bisa');
      expect(component.lines().map(l => [l.itemId, l.product.sellPrice])).toEqual([['it-1', 15], ['it-2', 60]]);
      expect(component.itemsTotal()).toBe(300);
      expect(fixture.nativeElement.querySelector('#purchase-initial-source')).toBeNull();
      expect(fixture.nativeElement.querySelector('#purchase-edit-reason')).not.toBeNull();
    });

    it('agrega lo no pedido, quita lo que faltó y envía las líneas conservadas con su id y el motivo', () => {
      component.removeLine(1);
      component.addProduct(product({ id: 'p-3', code: 'CH-1', name: 'Barra Chia', buyPrice: 5 }));
      component.onQuantityChange(0, 26);
      component.reason.set('  Faltaron las nueces  ');
      component.submit();

      const [id, request] = purchaseService.updatePurchase.mock.calls[0];
      expect(id).toBe('pur-7');
      expect(request.reason).toBe('Faltaron las nueces');
      expect('initialPayment' in request).toBe(false);
      expect(request.items.map((i: any) => [i.productId, i.itemId, i.quantity])).toEqual([['p-1', 'it-1', 26], ['p-3', undefined, 1]]);
      expect(TestBed.inject(ToastService).toasts().map(t => t.text)).toContain('Pedido PED-000007 actualizado.');
    });

    it('COM-14: la línea registrada tiene los botones y conserva su id al cambiar la cantidad', () => {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('app-quantity-stepper').length).toBe(2);

      component.decrementQuantity(0);
      component.incrementQuantity(1);
      component.reason.set('Llegó otra cantidad');
      component.submit();

      const [, request] = purchaseService.updatePurchase.mock.calls[0];
      expect(request.items.map((i: any) => [i.itemId, i.quantity])).toEqual([['it-1', 23], ['it-2', 1.6]]);
    });

    it('exige el motivo antes de guardar', () => {
      component.onQuantityChange(0, 20);
      component.submit();
      expect(component.formError()).toBe('Escriba el motivo del cambio.');
      expect(purchaseService.updatePurchase).not.toHaveBeenCalled();
    });

    it('COM-12: alerta que el gasto de envío se rehace al cambiar monto o cuenta', () => {
      expect(component.shippingChanged()).toBe(false);
      component.shippingCost.set(30);
      fixture.detectChanges();
      expect(component.shippingChanged()).toBe(true);
      expect(fixture.nativeElement.querySelector('.alert-warning')?.textContent)
        .toContain('Se anulará el gasto de envío de Bs 25.00');
      component.shippingCost.set(25);
      component.shippingAccountId.set('acc-caja');
      expect(component.shippingChanged()).toBe(true);
    });

    it('muestra el error del servidor (total menor a lo pagado)', () => {
      purchaseService.updatePurchase.mockReturnValue(throwError(() => ({ error: {
        message: 'El total del pedido (Bs 240,00) no puede ser menor a lo pagado (Bs 300,00); anule primero el pago.' } })));
      component.removeLine(1);
      component.reason.set('Faltaron las nueces');
      component.submit();
      expect(component.formError()).toContain('anule primero el pago');
    });
  });

  describe('CAJ-03: aviso de efectivo y Caja cerrada', () => {
    const CAJA = 'acc-caja';
    const BISA = 'acc-bisa';
    const accounts = [
      { id: BISA, name: 'Bisa', color: '#000', cashAccount: false, qrAccount: false },
      { id: CAJA, name: 'Caja', color: '#000', cashAccount: true, qrAccount: false }
    ];
    const warning = (): HTMLElement | null => fixture.nativeElement.querySelector('.cash-limit-warning');
    const note = (): HTMLElement | null => fixture.nativeElement.querySelector('.cash-closed-note');
    const text = (el: HTMLElement | null): string => el!.textContent!.replace(/\s+/g, ' ').trim();

    const render = async (): Promise<void> => {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    };

    // First payment and shipping as the form holds them; null means it does not apply
    const fill = async (values: { payFrom?: string; pay?: number | null; shipFrom?: string; ship?: number }): Promise<void> => {
      fixture.componentRef.setInput('accounts', accounts);
      component.initialPaymentSource.set(values.payFrom ?? '');
      component.initialPaymentAmount.set(values.pay);
      component.shippingCost.set(values.ship ?? 0);
      component.shippingAccountId.set(values.shipFrom ?? '');
      await render();
    };
    const fromCaja = 'account:' + CAJA;

    it('avisa si el pago desde Caja supera el esperado, y no si es exactamente el esperado', async () => {
      await fill({ payFrom: fromCaja, pay: 300 });
      expect(warning()).toBeNull();

      await fill({ payFrom: fromCaja, pay: 300.01 });
      expect(text(warning())).toContain('El monto supera el efectivo esperado en caja (Bs 300.00).');
    });

    it('nunca bloquea: el pedido se registra con el aviso a la vista', async () => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      component.updateLine(0, { quantity: 40 });
      await fill({ payFrom: fromCaja, pay: 400 });
      expect(warning()).not.toBeNull();

      component.submit();
      expect(purchaseService.registerPurchase).toHaveBeenCalled();
    });

    it('avisa si solo el envío sale de Caja', async () => {
      await fill({ payFrom: 'account:' + BISA, pay: 500, shipFrom: CAJA, ship: 300.01 });
      expect(text(warning())).toContain('El monto supera el efectivo esperado');
    });

    it('un solo aviso con la suma si el pago y el envío salen de Caja', async () => {
      await fill({ payFrom: fromCaja, pay: 290, shipFrom: CAJA, ship: 25 });
      expect(fixture.nativeElement.querySelectorAll('.cash-limit-warning').length).toBe(1);
      expect(text(warning())).toContain('El pago y el envío suman Bs 315.00 y superan el efectivo esperado en caja (Bs 300.00).');
    });

    it('no avisa si la suma no supera el esperado', async () => {
      await fill({ payFrom: fromCaja, pay: 275, shipFrom: CAJA, ship: 25 });
      expect(warning()).toBeNull();
    });

    it('solo cuenta lo que sale de Caja', async () => {
      await fill({ payFrom: fromCaja, pay: 290, shipFrom: BISA, ship: 25 });
      expect(warning()).toBeNull();
      await fill({ payFrom: 'account:' + BISA, pay: 290, shipFrom: CAJA, ship: 25 });
      expect(warning()).toBeNull();
    });

    it('no cuenta el pago de una persona ni «Sin pago por ahora»', async () => {
      await fill({ payFrom: 'creditor:c-1', pay: 500, shipFrom: CAJA, ship: 25 });
      expect(warning()).toBeNull();
      await fill({ pay: 500, shipFrom: CAJA, ship: 25 });
      expect(warning()).toBeNull();
    });

    it('un envío de 0 no cuenta aunque antes se haya elegido Caja', async () => {
      await fill({ payFrom: fromCaja, pay: 300, shipFrom: CAJA, ship: 0 });
      expect(warning()).toBeNull();
      expect(component.shipsFromCash()).toBe(false);
    });

    it('se actualiza al cambiar el monto lleno por una línea (COM-19)', async () => {
      component.supplierId.set('sup-1');
      component.addProduct(tea);
      component.updateLine(0, { quantity: 30 });
      await fill({ payFrom: fromCaja, pay: undefined });
      expect(warning()).toBeNull();

      component.updateLine(0, { quantity: 31 });
      await render();
      expect(warning()).not.toBeNull();
    });

    it('no avisa sin monto aunque el esperado sea negativo', async () => {
      cashService.currentSession.set({ id: 's1', expectedCash: -50 });
      await fill({ payFrom: fromCaja, pay: null });
      expect(warning()).toBeNull();
    });

    it('sin caja abierta no hay aviso', async () => {
      cashService.currentSession.set(null);
      await fill({ payFrom: fromCaja, pay: 500 });
      expect(warning()).toBeNull();
    });

    it('consulta el esperado al elegir Caja, no al abrir el formulario', async () => {
      await fill({ payFrom: 'account:' + BISA, pay: 100 });
      expect(cashService.getCurrentSession).not.toHaveBeenCalled();

      await fill({ payFrom: fromCaja, pay: 100 });
      expect(cashService.getCurrentSession).toHaveBeenCalled();
    });

    describe('al editar', () => {
      const edit = async (previous: { shippingCost: number; shippingAccountId: string | null }): Promise<void> => {
        cashService.currentSession.set({ id: 's1', expectedCash: 10 });
        fixture.componentRef.setInput('accounts', accounts);
        fixture.componentRef.setInput('isOpen', false);
        fixture.componentRef.setInput('purchase', {
          id: 'pur-7', purchaseNumber: 'PED-000007', supplierId: 'sup-1', purchaseDate: '2026-10-20', consignment: false,
          comment: null, deliveryType: 'EN_TIENDA', deliveryStatus: 'PENDIENTE', deliveryDate: null, items: [], paidAmount: 0,
          shippingAccountName: 'Caja', ...previous
        } as unknown as Purchase);
        fixture.detectChanges();
        fixture.componentRef.setInput('isOpen', true);
        await render();
      };

      it('no avisa ni muestra la nota si el envío no cambia', async () => {
        await edit({ shippingCost: 25, shippingAccountId: CAJA });
        cashService.getCurrentSession.mockReturnValue(of(null));
        await render();
        expect(warning()).toBeNull();
        expect(note()).toBeNull();
      });

      it('si el envío era de otra cuenta compara el envío nuevo completo', async () => {
        await edit({ shippingCost: 25, shippingAccountId: BISA });
        component.shippingAccountId.set(CAJA);
        await render();
        expect(warning()).not.toBeNull();
      });

      it('si el envío ya era de Caja compara solo la diferencia', async () => {
        await edit({ shippingCost: 25, shippingAccountId: CAJA });
        component.shippingCost.set(30);
        await render();
        expect(warning()).toBeNull();

        component.shippingCost.set(40);
        await render();
        expect(component.cashOutflow()).toBe(15);
        expect(warning()).not.toBeNull();
      });
    });

    describe('Caja sin «(caja abierta)»', () => {
      const optionTexts = (selector: string): string[] =>
        Array.from<HTMLOptionElement>(fixture.nativeElement.querySelectorAll(selector + ' option')).map(o => o.textContent!.trim());

      it('la opción dice Caja en las dos listas', async () => {
        await fill({ ship: 25 });
        expect(optionTexts('#purchase-initial-source')).toContain('Caja');
        expect(optionTexts('#purchase-shipping-account')).toContain('Caja');
      });

      const saveButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('.purchase-form button[type=submit]');
      const closedCash = (): void => {
        cashService.getCurrentSession.mockReturnValue(of(null));
        cashService.currentSession.set(null);
      };

      it('con la caja cerrada muestra la nota bajo la lista elegida y desactiva el botón', async () => {
        closedCash();
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(fixture.nativeElement.querySelector('#purchase-initial-source').parentElement.querySelector('.cash-closed-note'))
          .not.toBeNull();
        expect(saveButton().disabled).toBe(true);
      });

      it('también desactiva el botón si solo el envío sale de Caja', async () => {
        closedCash();
        await fill({ shipFrom: CAJA, ship: 25 });
        expect(saveButton().disabled).toBe(true);
      });

      it('al elegir otra cuenta el botón se activa, sin perder lo escrito', async () => {
        closedCash();
        component.supplierId.set('sup-1');
        component.addProduct(tea);
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(saveButton().disabled).toBe(true);

        component.initialPaymentSource.set('account:' + BISA);
        await render();
        expect(saveButton().disabled).toBe(false);
        expect(component.lines().length).toBe(1);
        expect(component.initialPaymentAmount()).toBe(10);
      });

      it('al volver a elegir Caja consulta otra vez y, con la caja ya abierta, activa el botón', async () => {
        closedCash();
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(saveButton().disabled).toBe(true);

        cashService.getCurrentSession.mockReturnValue(of({ id: 's1', expectedCash: 300 }));
        await fill({ payFrom: 'account:' + BISA, pay: 10 });
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(note()).toBeNull();
        expect(saveButton().disabled).toBe(false);
      });

      it('si falla la consulta no bloquea', async () => {
        cashService.getCurrentSession.mockReturnValue(throwError(() => ({ status: 0 })));
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(saveButton().disabled).toBe(false);
      });

      it('al editar, un envío de Caja sin cambios no bloquea', async () => {
        closedCash();
        fixture.componentRef.setInput('accounts', accounts);
        fixture.componentRef.setInput('isOpen', false);
        fixture.componentRef.setInput('purchase', {
          id: 'pur-7', purchaseNumber: 'PED-000007', supplierId: 'sup-1', purchaseDate: '2026-10-20', consignment: false,
          comment: null, deliveryType: 'EN_TIENDA', deliveryStatus: 'PENDIENTE', deliveryDate: null, items: [], paidAmount: 0,
          shippingAccountName: 'Caja', shippingCost: 25, shippingAccountId: CAJA
        } as unknown as Purchase);
        fixture.detectChanges();
        fixture.componentRef.setInput('isOpen', true);
        await render();
        expect(saveButton().disabled).toBe(false);

        component.shippingCost.set(30);
        await render();
        expect(saveButton().disabled).toBe(true);
      });

      it('sin nota con la caja abierta, con otra cuenta o si falla la consulta', async () => {
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(note()).toBeNull();

        cashService.getCurrentSession.mockReturnValue(of(null));
        await fill({ payFrom: 'account:' + BISA, pay: 10 });
        expect(note()).toBeNull();

        cashService.getCurrentSession.mockReturnValue(throwError(() => ({ status: 0 })));
        await fill({ payFrom: fromCaja, pay: 10 });
        expect(note()).toBeNull();
      });

      it('si el servidor rechaza porque la caja está cerrada, muestra su mensaje y no pierde lo escrito', async () => {
        purchaseService.registerPurchase.mockReturnValue(throwError(() => ({
          error: { message: 'Los movimientos de la cuenta Caja requieren una caja abierta.' }
        })));
        component.supplierId.set('sup-1');
        component.addProduct(tea);
        await fill({ payFrom: fromCaja, pay: 10 });

        component.submit();
        expect(component.formError()).toBe('Los movimientos de la cuenta Caja requieren una caja abierta.');
        expect(component.lines().length).toBe(1);
        expect(component.initialPaymentSource()).toBe(fromCaja);
      });
    });
  });
});
