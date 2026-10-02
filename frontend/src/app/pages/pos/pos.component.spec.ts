import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { PosComponent } from './pos.component';
import { CashService } from '../../core/cash/cash.service';
import { SaleService } from '../../core/sale/sale.service';
import { CashSessionSummary, CashSessionDetail, Sale } from '../../core/cash/models/cash.models';
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
      getSalesBySession: vi.fn().mockReturnValue(of([mockSale1]))
    };

    await TestBed.configureTestingModule({
      imports: [PosComponent],
      providers: [
        provideRouter([]),
        { provide: CashService, useValue: mockCashService },
        { provide: SaleService, useValue: mockSaleService }
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
});
