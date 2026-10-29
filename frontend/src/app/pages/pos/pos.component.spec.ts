import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { PosComponent } from './pos.component';
import { CashService } from '../../core/cash/cash.service';
import { SaleService } from '../../core/sale/sale.service';
import { CashSessionSummary, CashSessionDetail, Sale } from '../../core/cash/models/cash.models';
import { FinancialTransactionService } from '../../core/finance/financial-transaction.service';
import { FinancialCategoryService } from '../../core/finance/financial-category.service';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('PosComponent', () => {
  let component: PosComponent;
  let fixture: ComponentFixture<PosComponent>;
  let router: Router;
  let mockCashService: any;
  let mockSaleService: any;

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

  let mockTransactionService: any;
  let mockCategoryService: any;

  beforeEach(async () => {
    mockCashService = {
      currentSession: signal<CashSessionSummary | null>(mockSession),
      isLoadingCurrent: signal<boolean>(false),
      getCurrentSession: vi.fn().mockReturnValue(of(mockSession)),
      getSessionDetail: vi.fn().mockReturnValue(of(mockDetail)),
      openSession: vi.fn().mockReturnValue(of(mockSession)),
      closeSession: vi.fn().mockReturnValue(of({ ...mockSession, status: 'CERRADA' }))
    };

    mockSaleService = {
      sales: signal<Sale[]>([mockSale1]),
      isLoading: signal<boolean>(false),
      voidSale: vi.fn().mockReturnValue(of({ ...mockSale1, status: 'ANULADA', voidReason: 'Error en digitación' })),
      getSalesBySession: vi.fn().mockReturnValue(of([mockSale1])),
      getSaleById: vi.fn().mockReturnValue(of(mockSale1))
    };

    mockTransactionService = {
      createCashExpense: vi.fn().mockReturnValue(of({ id: 'tx-exp-1', amount: 35.5 })),
      getExpensesByCashSession: vi.fn().mockReturnValue(of([])),
      voidCashExpense: vi.fn().mockReturnValue(of({ id: 'tx-exp-1', amount: 35.5, status: 'ANULADO' }))
    };

    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of([{ id: 'cat-1', name: 'Insumos', type: 'GASTO' }]))
    };

    await TestBed.configureTestingModule({
      imports: [PosComponent],
      providers: [
        provideRouter([]),
        { provide: CashService, useValue: mockCashService },
        { provide: SaleService, useValue: mockSaleService },
        { provide: FinancialTransactionService, useValue: mockTransactionService },
        { provide: FinancialCategoryService, useValue: mockCategoryService }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate');

    fixture = TestBed.createComponent(PosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe inicializarse y cargar la sesión de caja activa y transacciones', () => {
    expect(component).toBeTruthy();
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
    expect(mockSaleService.getSalesBySession).toHaveBeenCalledWith('session-1');
    expect(component.recentSales().length).toBe(1);
  });

  it('debe mostrar el estado de caja cerrada y no permitir navegar a registrar venta si no hay sesión abierta', () => {
    mockCashService.currentSession.set(null);
    fixture.detectChanges();

    component.navigateToRegisterSale();
    expect(router.navigate).not.toHaveBeenCalledWith(['/pos/sale']);
    expect(component.feedbackMessage()?.type).toBe('error');

    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
  });

  it('debe navegar hacia /pos/sale al hacer clic en el botón Registrar venta teniendo caja abierta', () => {
    component.navigateToRegisterSale();
    expect(router.navigate).toHaveBeenCalledWith(['/pos/sale']);
  });

  it('debe contener el botón Registrar venta en la cabecera de transacciones y llamar a navigateToRegisterSale', () => {
    const registerBtn = fixture.debugElement.query(By.css('.transactions-header button.btn-primary'));
    expect(registerBtn).toBeTruthy();
    expect(registerBtn.nativeElement.textContent).toContain('Registrar venta');

    registerBtn.nativeElement.click();
    expect(router.navigate).toHaveBeenCalledWith(['/pos/sale']);
  });

  it('debe abrir y cerrar los modales de apertura, información y cierre de caja', () => {
    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
    component.closeOpenModal();
    expect(component.isOpenModalVisible()).toBe(false);

    component.openCloseModal();
    expect(component.isCloseModalVisible()).toBe(true);
    component.closeCloseModal();
    expect(component.isCloseModalVisible()).toBe(false);

    component.openInfoModal();
    expect(component.isInfoModalVisible()).toBe(true);
    component.closeInfoModal();
    expect(component.isInfoModalVisible()).toBe(false);
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

  it('debe confirmar la apertura de caja y recargar la sesión', () => {
    component.handleConfirmOpen({ openingAmount: 100, openingComment: 'Inicio' });

    expect(mockCashService.openSession).toHaveBeenCalledWith({ openingAmount: 100, openingComment: 'Inicio' });
    expect(component.isOpenModalVisible()).toBe(false);
  });

  it('debe confirmar el cierre de caja y reiniciar estado', () => {
    component.handleConfirmClose({ closingAmount: 150 });

    expect(mockCashService.closeSession).toHaveBeenCalledWith({ closingAmount: 150 });
    expect(component.isCloseModalVisible()).toBe(false);
    expect(component.recentSales().length).toBe(0);
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

  it('no debe contener el carrito ni catálogo embebidos en el POS principal', () => {
    expect(fixture.debugElement.query(By.css('.pos-sales-layout'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.pos-cart-pane'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.catalog-section-pane'))).toBeNull();
  });

  it('debe mostrar las acciones de fila ordenadas (1. Ver detalle, 2. Anular) con el patrón estándar table-actions', () => {
    const tableActions = fixture.debugElement.query(By.css('.table-actions'));
    expect(tableActions).toBeTruthy();

    const buttons = tableActions.queryAll(By.css('button.btn-icon'));
    expect(buttons.length).toBe(2);

    // Primer botón: Ver detalle (.btn-audit)
    expect(buttons[0].nativeElement.classList.contains('btn-audit')).toBe(true);
    expect(buttons[0].nativeElement.getAttribute('title')).toBe('Ver detalle');

    // Segundo botón: Anular (.btn-delete)
    expect(buttons[1].nativeElement.classList.contains('btn-delete')).toBe(true);
    expect(buttons[1].nativeElement.getAttribute('title')).toBe('Anular venta');
  });

  it('debe abrir y cerrar el modal de detalle de venta al pulsar el botón Ver detalle', () => {
    expect(component.isDetailModalVisible()).toBe(false);

    const viewBtn = fixture.debugElement.query(By.css('.table-actions button.btn-audit'));
    expect(viewBtn).toBeTruthy();
    viewBtn.nativeElement.click();

    expect(component.isDetailModalVisible()).toBe(true);
    expect(component.selectedSaleForDetailId()).toBe('sale-1');
    expect(component.selectedSaleForDetail()?.id).toBe('sale-1');

    component.closeDetailModal();
    expect(component.isDetailModalVisible()).toBe(false);
    expect(component.selectedSaleForDetailId()).toBeNull();
    expect(component.selectedSaleForDetail()).toBeNull();
  });

  it('debe deshabilitar el botón Anular si la venta está anulada o la sesión de caja no está abierta', () => {
    const voidedSale: Sale = { ...mockSale1, id: 'sale-voided', status: 'ANULADA' };
    component.recentSales.set([voidedSale]);
    fixture.detectChanges();

    const deleteBtn = fixture.debugElement.query(By.css('.table-actions button.btn-delete'));
    expect(deleteBtn).toBeTruthy();
    expect(deleteBtn.nativeElement.disabled).toBe(true);

    // Con sesión en estado CERRADA
    mockCashService.currentSession.set({ ...mockSession, status: 'CERRADA' });
    component.recentSales.set([mockSale1]);
    fixture.detectChanges();

    const deleteBtnClosed = fixture.debugElement.query(By.css('.table-actions button.btn-delete'));
    expect(deleteBtnClosed).toBeTruthy();
    expect(deleteBtnClosed.nativeElement.disabled).toBe(true);
  });

  it('debe abrir el modal de gasto de caja y, al registrarlo, cerrarlo y recargar la sesión', () => {
    component.openCashExpenseModal();
    fixture.detectChanges();
    expect(component.isCashExpenseModalVisible()).toBe(true);
    expect(mockCategoryService.getCategories).toHaveBeenCalledWith('GASTO');

    mockCashService.getCurrentSession.mockClear();
    component.onCashExpenseSaved('Gasto de caja de Bs 35,50 registrado exitosamente.');

    expect(component.isCashExpenseModalVisible()).toBe(false);
    expect(component.feedbackMessage()?.text).toContain('Gasto de caja de Bs 35,50 registrado exitosamente');
    expect(mockTransactionService.getExpensesByCashSession).toHaveBeenCalledWith('session-1');
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
  });

  it('no abre el modal de gasto con la caja cerrada', () => {
    mockCashService.currentSession.set(null);
    component.openCashExpenseModal();
    expect(component.isCashExpenseModalVisible()).toBe(false);
    expect(component.feedbackMessage()?.type).toBe('error');
  });

  it('debe tener seleccionada la opción Ventas por defecto y mostrar el selector visual de contenido', () => {
    expect(component.activeTab()).toBe('VENTAS');

    const viewPillGroup = fixture.debugElement.query(By.css('.view-pill-group'));
    expect(viewPillGroup).toBeTruthy();

    const pills = viewPillGroup.queryAll(By.css('.view-pill'));
    expect(pills.length).toBe(3);
    expect(pills[0].nativeElement.textContent).toContain('Ventas');
    expect(pills[0].nativeElement.classList.contains('active')).toBe(true);
    expect(pills[1].nativeElement.textContent).toContain('Compras');
    expect(pills[1].nativeElement.classList.contains('active')).toBe(false);
    expect(pills[2].nativeElement.textContent).toContain('Ingresos / Retiros');

    // Listado de ventas visible
    const salesTable = fixture.debugElement.query(By.css('.table'));
    expect(salesTable).toBeTruthy();
    expect(salesTable.nativeElement.textContent).toContain('# Venta');
  });

  it('debe permitir cambiar a la vista Compras y mostrar compras y gastos de la sesión actual', () => {
    const mockExpense: any = {
      id: 'tx-exp-1',
      type: 'GASTO_MANUAL',
      amount: 45.0,
      transactionDate: '2026-10-01T11:00:00Z',
      description: 'Compra de bolsas kraft',
      notes: '500 unidades',
      status: 'ACTIVO',
      cashSessionId: 'session-1',
      sourceAccount: { id: 'acc-1', name: 'Caja', color: '#1B3B18' },
      category: { id: 'cat-1', name: 'Insumos', type: 'GASTO', color: '#2E5B27' },
      createdBy: 'admin',
      createdAt: '2026-10-01T11:00:00Z'
    };

    component.recentExpenses.set([mockExpense]);
    component.setActiveTab('COMPRAS');
    fixture.detectChanges();

    expect(component.activeTab()).toBe('COMPRAS');

    const pills = fixture.debugElement.queryAll(By.css('.view-pill'));
    expect(pills[0].nativeElement.classList.contains('active')).toBe(false);
    expect(pills[1].nativeElement.classList.contains('active')).toBe(true);

    // La tabla de compras debe mostrar los datos del gasto sin la columna Cuenta
    const expensesTable = fixture.debugElement.query(By.css('.table'));
    expect(expensesTable).toBeTruthy();
    expect(expensesTable.nativeElement.textContent).toContain('Compra de bolsas kraft');
    expect(expensesTable.nativeElement.textContent).toContain('Insumos');
    expect(expensesTable.nativeElement.textContent).toContain('- Bs 45.00');
    expect(expensesTable.nativeElement.textContent).not.toContain('Cuenta');

    // Debe mostrar botón Registrar gasto en cabecera
    const headerBtn = fixture.debugElement.query(By.css('.transactions-header-action button'));
    expect(headerBtn.nativeElement.textContent).toContain('Registrar gasto');
  });

  it('no debe contabilizar gastos anulados como gastos activos en el contador', () => {
    const activeExpense: any = {
      id: 'tx-exp-1',
      type: 'GASTO_MANUAL',
      amount: 50.0,
      status: 'ACTIVO',
      cashSessionId: 'session-1',
      sourceAccount: { id: 'acc-1', name: 'Caja', color: '#1B3B18' }
    };
    const voidedExpense: any = {
      id: 'tx-exp-2',
      type: 'GASTO_MANUAL',
      amount: 30.0,
      status: 'ANULADO',
      cashSessionId: 'session-1',
      sourceAccount: { id: 'acc-1', name: 'Caja', color: '#1B3B18' }
    };

    component.recentExpenses.set([activeExpense, voidedExpense]);

    expect(component.activeExpensesCount()).toBe(1);
    expect(component.totalExpensesAmount()).toBe(50.0);
    expect(component.recentExpenses().length).toBe(2);
  });

  it('al anular un gasto desde la tabla de gastos, avisa y recarga la caja', () => {
    mockCashService.getCurrentSession.mockClear();
    component.onCashExpensesChanged('Gasto de Bs 45,00 anulado exitosamente.');

    expect(component.feedbackMessage()?.text).toBe('Gasto de Bs 45,00 anulado exitosamente.');
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
  });

  it('el aviso de cierre muestra el sobrante o faltante con el formato de la tienda', () => {
    mockCashService.closeSession.mockReturnValue(of({ ...mockSession, status: 'CERRADA', sessionNumber: 208, difference: -4.32 }));
    component.handleConfirmClose({ closingAmount: 100, closingComment: null });

    expect(component.feedbackMessage()?.text).toBe('Caja #208 cerrada exitosamente (con faltante de Bs 4,32).');
  });

  it('al refrescar la caja abierta mantiene el espacio de trabajo (sin volver a «Cargando»)', () => {
    mockCashService.isLoadingCurrent.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pos-active-workspace')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.loading-state-card')).toBeNull();

    mockCashService.currentSession.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.loading-state-card')).toBeTruthy();
  });

  it('debe alternar de regreso a Ventas mostrando el listado actual sin cambios', () => {
    component.setActiveTab('COMPRAS');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('COMPRAS');

    component.setActiveTab('VENTAS');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('VENTAS');

    const salesTable = fixture.debugElement.query(By.css('.table'));
    expect(salesTable).toBeTruthy();
    expect(salesTable.nativeElement.textContent).toContain('VTA-0001-001');
    expect(component.recentSales().length).toBe(1);
  });

  it('no debe mostrar el botón Gasto de caja en la cabecera junto a Cerrar caja', () => {
    const headerActions = fixture.debugElement.query(By.css('.header-actions'));
    expect(headerActions).toBeTruthy();
    const headerButtons = headerActions.queryAll(By.css('button'));
    const expenseBtnInHeader = headerButtons.find(b => b.nativeElement.textContent.includes('Gasto de Caja'));
    expect(expenseBtnInHeader).toBeUndefined();
    // Solo deben estar Información y Cerrar caja
    expect(headerButtons.length).toBe(2);
    expect(headerButtons[0].nativeElement.textContent).toContain('Información');
    expect(headerButtons[1].nativeElement.textContent).toContain('Cerrar caja');
  });

  it('debe mostrar el indicador Gastos de caja y el efectivo esperado en gaveta calculado por el servidor', () => {
    // Configurar sesión: Inicial 200, Ventas en efectivo 850, Ventas QR 300
    mockCashService.currentSession.set({
      ...mockSession,
      openingAmount: 200.0,
      totalSalesCash: 850.0,
      totalSalesQr: 300.0,
      expectedCash: 930.0
    });

    const expense1: any = {
      id: 'tx-1',
      type: 'GASTO_MANUAL',
      amount: 80.0,
      status: 'ACTIVO',
      cashSessionId: 'session-1',
      sourceAccount: { id: 'acc-1', name: 'Caja', color: '#1B3B18' }
    };
    const expense2: any = {
      id: 'tx-2',
      type: 'GASTO_MANUAL',
      amount: 40.0,
      status: 'ACTIVO',
      cashSessionId: 'session-1',
      sourceAccount: { id: 'acc-1', name: 'Caja', color: '#1B3B18' }
    };

    component.recentExpenses.set([expense1, expense2]);
    fixture.detectChanges();

    // Gastos activos = 80 + 40 = 120
    expect(component.totalExpensesAmount()).toBe(120.0);
    // Expected in drawer = 200 + 850 - 120 = 930 (las ventas QR 300 no se suman)
    expect(component.expectedCashInDrawer()).toBe(930.0);

    // Verificar en el DOM los 5 KPIs
    const kpiCards = fixture.debugElement.queryAll(By.css('.kpi-card'));
    expect(kpiCards.length).toBe(5);

    const expensesKpi = kpiCards.find(card => card.nativeElement.textContent.includes('Gastos de caja'));
    expect(expensesKpi).toBeTruthy();
    expect(expensesKpi!.nativeElement.textContent).toContain('Bs 120.00');

    const expectedKpi = kpiCards.find(card => card.nativeElement.textContent.includes('Efectivo esperado en gaveta'));
    expect(expectedKpi).toBeTruthy();
    expect(expectedKpi!.nativeElement.textContent).toContain('Bs 930.00');

    // Al anular el gasto de 40 el POS recarga la caja y el servidor devuelve el nuevo esperado
    expense2.status = 'ANULADO';
    component.recentExpenses.set([expense1, { ...expense2 }]);
    mockCashService.currentSession.set({ ...mockCashService.currentSession()!, expectedCash: 970.0 });
    fixture.detectChanges();

    // Gastos activos pasan a 80
    expect(component.totalExpensesAmount()).toBe(80.0);
    // Expected in drawer se actualiza a 200 + 850 - 80 = 970
    expect(component.expectedCashInDrawer()).toBe(970.0);
    expect(expensesKpi!.nativeElement.textContent).toContain('Bs 80.00');
    expect(expectedKpi!.nativeElement.textContent).toContain('Bs 970.00');
  });

  it('solo lista como gastos los GASTO_MANUAL de la sesión, no las transferencias de Caja', () => {
    const movements: any[] = [
      { id: 'tx-1', type: 'GASTO_MANUAL', amount: 50.0, status: 'ACTIVO', cashSessionId: 'session-1' },
      { id: 'tx-2', type: 'TRANSFERENCIA', amount: 300.0, status: 'ACTIVO', cashSessionId: 'session-1' }
    ];
    mockTransactionService.getExpensesByCashSession.mockReturnValue(of(movements));

    component.loadExpensesForSession('session-1');

    expect(component.recentExpenses().map((e) => e.id)).toEqual(['tx-1']);
    expect(component.totalExpensesAmount()).toBe(50.0);
    // CAJ-08: las transferencias de Caja van a la pestaña Ingresos / Retiros
    expect(component.recentTransfers().map((t) => t.id)).toEqual(['tx-2']);
  });
});
