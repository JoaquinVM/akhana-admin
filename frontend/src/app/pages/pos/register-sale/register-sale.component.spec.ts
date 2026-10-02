import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RegisterSaleComponent } from './register-sale.component';
import { CashService } from '../../../core/cash/cash.service';
import { ProductService } from '../../../core/product/product.service';
import { QuickProductService } from '../../../core/quick-product/quick-product.service';
import { SaleService } from '../../../core/sale/sale.service';
import { CashSessionSummary, Sale } from '../../../core/cash/models/cash.models';
import { Product } from '../../../core/product/models/product.models';
import { QuickProductGroup } from '../../../core/quick-product/models/quick-product.models';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';

describe('RegisterSaleComponent', () => {
  let component: RegisterSaleComponent;
  let fixture: ComponentFixture<RegisterSaleComponent>;
  let router: Router;
  let mockCashService: any;
  let mockProductService: any;
  let mockQuickProductService: any;
  let mockSaleService: any;

  const mockProduct1: Product = {
    id: 'prod-1',
    code: 'P001',
    name: 'Té Verde Matcha',
    categoryId: 'cat-1',
    categoryName: 'Tés',
    categoryColor: '#2e5b27',
    supplierId: 'sup-1',
    supplierName: 'Proveedor A',
    tags: [],
    buyPrice: 10,
    sellPrice: 20,
    fixedProfit: 10,
    percentageProfit: 100,
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-01-01'
  };

  const mockProduct2: Product = {
    id: 'prod-2',
    code: 'P002',
    name: 'Café Espresso',
    categoryId: 'cat-2',
    categoryName: 'Café',
    categoryColor: '#854d0e',
    supplierId: 'sup-1',
    supplierName: 'Proveedor A',
    tags: [],
    buyPrice: 8,
    sellPrice: 15,
    fixedProfit: 7,
    percentageProfit: 87.5,
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-01-01'
  };

  const mockGroup: QuickProductGroup = {
    id: 'grp-1',
    name: 'Bebidas Calientes',
    displayOrder: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    products: [mockProduct1, mockProduct2]
  };

  const mockSession: CashSessionSummary = {
    id: 'session-1',
    sessionNumber: 1,
    status: 'ABIERTA',
    openingAmount: 100.0,
    openingComment: 'Fondo inicio',
    openedBy: 'admin',
    openedAt: '2026-10-01T10:00:00Z',
    totalSalesCash: 0,
    totalSalesQr: 0,
    totalSales: 0,
    expectedCash: 100.0,
    salesCount: 0
  };

  const mockCreatedSale: Sale = {
    id: 'sale-1',
    saleNumber: 'VTA-0001-001',
    cashSessionId: 'session-1',
    status: 'COMPLETADA',
    totalAmount: 35.0,
    paymentMethod: 'EFECTIVO',
    createdBy: 'admin',
    createdAt: '2026-10-01T10:30:00Z'
  };

  beforeEach(async () => {
    mockCashService = {
      currentSession: signal<CashSessionSummary | null>(mockSession),
      getCurrentSession: vi.fn().mockReturnValue(of(mockSession))
    };

    mockProductService = {
      getProducts: vi.fn().mockReturnValue(of([mockProduct1, mockProduct2]))
    };

    mockQuickProductService = {
      groups: signal<QuickProductGroup[]>([mockGroup]),
      loadGroups: vi.fn().mockReturnValue(of([mockGroup]))
    };

    mockSaleService = {
      registerSale: vi.fn().mockReturnValue(of(mockCreatedSale))
    };

    await TestBed.configureTestingModule({
      imports: [RegisterSaleComponent],
      providers: [
        provideRouter([]),
        { provide: CashService, useValue: mockCashService },
        { provide: ProductService, useValue: mockProductService },
        { provide: QuickProductService, useValue: mockQuickProductService },
        { provide: SaleService, useValue: mockSaleService }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate');

    fixture = TestBed.createComponent(RegisterSaleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse, cargar productos activos y grupos de productos rápidos', () => {
    expect(component).toBeTruthy();
    expect(mockProductService.getProducts).toHaveBeenCalledWith(undefined, 'ACTIVO');
    expect(mockQuickProductService.loadGroups).toHaveBeenCalled();
    expect(component.catalogProducts().length).toBe(2);
    expect(component.activeGroup()?.name).toBe('Bebidas Calientes');
  });

  it('debe redirigir a /pos si no hay una caja abierta', () => {
    mockCashService.getCurrentSession.mockReturnValue(of(null));
    component.ngOnInit();
    expect(router.navigate).toHaveBeenCalledWith(['/pos']);
  });

  it('debe agregar producto al carrito e incrementar en 1 si ya existe', () => {
    expect(component.cartItems().length).toBe(0);

    component.addToCart(mockProduct1);
    expect(component.cartItems().length).toBe(1);
    expect(component.cartItems()[0].quantity).toBe(1);
    expect(component.subtotalGross()).toBe(20);

    // Agregar de nuevo el mismo producto -> incrementa a 2
    component.addToCart(mockProduct1);
    expect(component.cartItems().length).toBe(1);
    expect(component.cartItems()[0].quantity).toBe(2);
    expect(component.subtotalGross()).toBe(40);
  });

  it('debe actualizar cantidad y remover ítem si la cantidad llega a 0', () => {
    component.addToCart(mockProduct1);
    component.addToCart(mockProduct2);
    expect(component.cartItems().length).toBe(2);

    // Reducir cantidad
    component.decrementQuantity(mockProduct2.id); // de 1 pasa a 0 -> se remueve
    expect(component.cartItems().length).toBe(1);
    expect(component.cartItems()[0].product.id).toBe('prod-1');
  });

  it('debe calcular descuentos unitarios y descuento general correctamente', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.updateQuantity(mockProduct1.id, 3); // 60 Bs

    // Descuento de 2 Bs por unidad
    component.updateUnitDiscount(mockProduct1.id, 2);
    expect(component.discountItemsTotal()).toBe(6);
    expect(component.subtotalPostItems()).toBe(54);

    // Descuento general de 4 Bs
    component.updateGlobalDiscount(4);
    expect(component.globalDiscount()).toBe(4);
    expect(component.discountTotal()).toBe(10);
    expect(component.totalAmount()).toBe(50);
  });

  it('debe rechazar descuento general si supera el subtotal neto de los productos', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.updateGlobalDiscount(30);

    expect(component.globalDiscount()).toBe(20);
    expect(component.errorMessage()).toContain('no puede superar el subtotal');
  });

  it('debe vaciar el carrito completamente con clearCart()', () => {
    component.addToCart(mockProduct1);
    component.addToCart(mockProduct2);
    expect(component.cartItems().length).toBe(2);

    component.clearCart();
    expect(component.cartItems().length).toBe(0);
    expect(component.globalDiscount()).toBe(0);
    expect(component.totalAmount()).toBe(0);
  });

  it('debe procesar el método de pago EFECTIVO calculando el cambio', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.setPaymentMethod('EFECTIVO');

    component.amountReceived.set(50);
    expect(component.changeGiven()).toBe(30);
    expect(component.isPaymentValid()).toBe(true);

    component.amountReceived.set(15);
    expect(component.isPaymentValid()).toBe(false); // insuficiente
  });

  it('debe procesar el método de pago QR fijando el monto y sin cambio', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.setPaymentMethod('QR');

    expect(component.amountQr()).toBe(20);
    expect(component.changeGiven()).toBe(0);
    expect(component.isPaymentValid()).toBe(true);
  });

  it('debe procesar el método de pago MIXTO calculando el remanente QR y cambio en efectivo', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.setPaymentMethod('MIXTO');

    component.amountCash.set(10);
    component.amountReceived.set(15); // entrega billete de 15 para pagar 10

    expect(component.amountQr()).toBe(10); // 20 - 10
    expect(component.changeGiven()).toBe(5); // 15 - 10
    expect(component.isPaymentValid()).toBe(true);
  });

  it('debe completar la venta exitosamente y redirigir a /pos con estado de confirmación', () => {
    component.addToCart(mockProduct1);
    component.setPaymentMethod('EFECTIVO');
    component.amountReceived.set(20);

    component.submitSale();

    expect(mockSaleService.registerSale).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/pos'], expect.objectContaining({
      state: expect.objectContaining({
        saleSuccessMessage: 'Venta #VTA-0001-001 registrada exitosamente.'
      })
    }));
  });

  it('debe permitir cancelar la venta y regresar al POS', () => {
    component.cancelAndReturn();
    expect(router.navigate).toHaveBeenCalledWith(['/pos']);
  });
});
