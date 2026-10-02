import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PosComponent } from './pos.component';
import { CashService } from '../../core/cash/cash.service';
import { ProductService } from '../../core/product/product.service';
import { QuickProductService } from '../../core/quick-product/quick-product.service';
import { SaleService } from '../../core/sale/sale.service';
import { CashSessionSummary, CashSessionDetail, Sale } from '../../core/cash/models/cash.models';
import { Product } from '../../core/product/models/product.models';
import { QuickProductGroup } from '../../core/quick-product/models/quick-product.models';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('PosComponent', () => {
  let component: PosComponent;
  let fixture: ComponentFixture<PosComponent>;
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
    closingAmount: null,
    closingComment: null,
    closedBy: null,
    closedAt: null,
    totalSalesCash: 50.0,
    totalSalesQr: 25.0,
    totalSales: 75.0,
    expectedCash: 150.0,
    difference: null,
    salesCount: 1
  };

  const mockSale1: Sale = {
    id: 'sale-1',
    saleNumber: 'VTA-0001-001',
    cashSessionId: 'session-1',
    status: 'COMPLETADA',
    totalAmount: 50.0,
    paymentMethod: 'EFECTIVO',
    description: 'Té verde',
    createdBy: 'admin',
    createdAt: '2026-10-01T10:30:00Z'
  };

  const mockDetail: CashSessionDetail = {
    ...mockSession,
    cuts: [],
    sales: [mockSale1]
  };

  beforeEach(async () => {
    mockCashService = {
      currentSession: signal<CashSessionSummary | null>(mockSession),
      isLoadingCurrent: signal<boolean>(false),
      getCurrentSession: vi.fn().mockReturnValue(of(mockSession)),
      getSessionDetail: vi.fn().mockReturnValue(of(mockDetail)),
      openSession: vi.fn().mockReturnValue(of(mockSession)),
      registerSale: vi.fn().mockReturnValue(of(mockSale1)),
      closeSession: vi.fn().mockReturnValue(of({ ...mockSession, status: 'CERRADA' }))
    };

    mockProductService = {
      getProducts: vi.fn().mockReturnValue(of([mockProduct1, mockProduct2]))
    };

    mockQuickProductService = {
      groups: signal<QuickProductGroup[]>([mockGroup]),
      loadGroups: vi.fn().mockReturnValue(of([mockGroup])),
      createGroup: vi.fn().mockReturnValue(of(mockGroup)),
      updateGroup: vi.fn().mockReturnValue(of(mockGroup)),
      deleteGroup: vi.fn().mockReturnValue(of(undefined)),
      reorderGroups: vi.fn().mockReturnValue(of([mockGroup])),
      reorderGroupItems: vi.fn().mockReturnValue(of(mockGroup))
    };

    mockSaleService = {
      sales: signal<Sale[]>([mockSale1]),
      isLoading: signal<boolean>(false),
      registerSale: vi.fn().mockReturnValue(of(mockSale1)),
      voidSale: vi.fn().mockReturnValue(of({ ...mockSale1, status: 'ANULADA', voidReason: 'Error en digitación' })),
      getSalesBySession: vi.fn().mockReturnValue(of([mockSale1])),
      getAllSales: vi.fn().mockReturnValue(of([mockSale1]))
    };

    await TestBed.configureTestingModule({
      imports: [PosComponent],
      providers: [
        provideRouter([]),
        { provide: CashService, useValue: mockCashService },
        { provide: ProductService, useValue: mockProductService },
        { provide: QuickProductService, useValue: mockQuickProductService },
        { provide: SaleService, useValue: mockSaleService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe inicializarse y cargar la sesión de caja activa, productos y grupos rápidos', () => {
    expect(component).toBeTruthy();
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
    expect(mockSaleService.getSalesBySession).toHaveBeenCalledWith('session-1');
    expect(mockProductService.getProducts).toHaveBeenCalled();
    expect(mockQuickProductService.loadGroups).toHaveBeenCalled();
    expect(component.recentSales().length).toBe(1);
    expect(component.catalogProducts().length).toBe(2);
  });

  it('debe mostrar el estado de caja cerrada y no permitir venta si no hay sesión abierta', () => {
    mockCashService.currentSession.set(null);
    fixture.detectChanges();

    component.openCheckoutModal();
    expect(component.isCheckoutModalVisible()).toBe(false);
    expect(component.feedbackMessage()?.type).toBe('error');

    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
  });

  it('debe agregar producto al carrito y si ya existe, incrementar la cantidad en 1', () => {
    expect(component.cartItems().length).toBe(0);

    // Agregar producto 1
    component.addToCart(mockProduct1);
    expect(component.cartItems().length).toBe(1);
    expect(component.cartItems()[0].quantity).toBe(1);
    expect(component.cartItems()[0].subtotal).toBe(20);

    // Agregar producto 1 nuevamente -> debe incrementar cantidad a 2 sin duplicar fila
    component.addToCart(mockProduct1);
    expect(component.cartItems().length).toBe(1);
    expect(component.cartItems()[0].quantity).toBe(2);
    expect(component.cartItems()[0].subtotal).toBe(40);

    // Agregar producto 2
    component.addToCart(mockProduct2);
    expect(component.cartItems().length).toBe(2);
    expect(component.totalAmount()).toBe(55); // 40 + 15
  });

  it('debe aplicar descuento por unidad en ítem y calcular subtotales correctamente', () => {
    component.addToCart(mockProduct1); // precio 20, qty 1
    component.updateQuantity(mockProduct1.id, 3); // qty 3

    // Aplicar descuento de 2 Bs por unidad
    component.updateUnitDiscount(mockProduct1.id, 2);

    const item = component.cartItems()[0];
    expect(item.unitDiscount).toBe(2);
    expect(item.unitFinalPrice).toBe(18); // 20 - 2
    expect(item.subtotal).toBe(54); // 3 * 18
    expect(component.subtotalGross()).toBe(60);
    expect(component.discountItemsTotal()).toBe(6);
    expect(component.subtotalPostItems()).toBe(54);
    expect(component.totalAmount()).toBe(54);
  });

  it('debe aplicar descuento general y calcular el total neto a pagar', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.updateQuantity(mockProduct1.id, 5); // 100 Bs

    component.updateGlobalDiscount(15);
    expect(component.globalDiscount()).toBe(15);
    expect(component.totalAmount()).toBe(85);
  });

  it('debe rechazar descuento general si supera el subtotal de la venta', () => {
    component.addToCart(mockProduct1); // 20 Bs
    component.updateGlobalDiscount(50); // mayor que 20

    expect(component.globalDiscount()).toBe(20); // capped at subtotal
    expect(component.feedbackMessage()?.type).toBe('error');
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

  it('debe abrir y cerrar los modales correspondientes', () => {
    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
    component.closeOpenModal();
    expect(component.isOpenModalVisible()).toBe(false);

    component.addToCart(mockProduct1);
    component.openCheckoutModal();
    expect(component.isCheckoutModalVisible()).toBe(true);
    component.closeCheckoutModal();
    expect(component.isCheckoutModalVisible()).toBe(false);

    component.openCloseModal();
    expect(component.isCloseModalVisible()).toBe(true);
    component.closeCloseModal();
    expect(component.isCloseModalVisible()).toBe(false);

    component.openInfoModal();
    expect(component.isInfoModalVisible()).toBe(true);
    component.closeInfoModal();
    expect(component.isInfoModalVisible()).toBe(false);

    component.openConfigModal();
    expect(component.isConfigModalVisible()).toBe(true);
    component.closeConfigModal();
    expect(component.isConfigModalVisible()).toBe(false);
  });

  it('debe confirmar el cobro en Checkout y reiniciar el carrito', () => {
    component.addToCart(mockProduct1);

    component.handleConfirmCheckout({
      items: [{ productId: mockProduct1.id, quantity: 1 }],
      paymentMethod: 'EFECTIVO',
      amountCash: 20,
      amountReceived: 20
    });

    expect(mockSaleService.registerSale).toHaveBeenCalled();
    expect(component.isCheckoutModalVisible()).toBe(false);
    expect(component.cartItems().length).toBe(0);
    expect(component.feedbackMessage()?.type).toBe('success');
  });

  it('debe abrir modal de anulación y anular venta con motivo obligatorio', () => {
    component.openVoidModal(mockSale1);
    expect(component.isVoidModalVisible()).toBe(true);
    expect(component.selectedSaleToVoid()?.id).toBe('sale-1');

    component.handleConfirmVoid({ voidReason: 'Error en digitación de productos' });

    expect(mockSaleService.voidSale).toHaveBeenCalledWith('sale-1', {
      voidReason: 'Error en digitación de productos'
    });
    expect(component.isVoidModalVisible()).toBe(false);
    expect(component.feedbackMessage()?.type).toBe('success');
  });

  it('no debe utilizar emojis como iconos en la interfaz del POS y debe emplear SVGs vectoriales', () => {
    const posText = fixture.nativeElement.textContent || '';
    const emojiRegex = /[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;
    expect(emojiRegex.test(posText)).toBe(false);

    const svgIcons = fixture.debugElement.queryAll(By.css('svg'));
    expect(svgIcons.length).toBeGreaterThan(5);
  });

  it('no debe mostrar enlaces al historial de cajas en el terminal POS', () => {
    const historyLinks = fixture.debugElement.queryAll(By.css('a[href*="cash/history"], a[routerLink*="cash/history"]'));
    expect(historyLinks.length).toBe(0);
  });
});
