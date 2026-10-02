import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PosComponent } from './pos.component';
import { CashService } from '../../core/cash/cash.service';
import { CashSessionSummary, CashSessionDetail } from '../../core/cash/models/cash.models';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('PosComponent', () => {
  let component: PosComponent;
  let fixture: ComponentFixture<PosComponent>;
  let mockCashService: any;

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

  const mockDetail: CashSessionDetail = {
    ...mockSession,
    cuts: [],
    sales: [
      {
        id: 'sale-1',
        saleNumber: 'VTA-0001-001',
        cashSessionId: 'session-1',
        totalAmount: 50.0,
        paymentMethod: 'EFECTIVO',
        description: 'Té verde',
        createdBy: 'admin',
        createdAt: '2026-10-01T10:30:00Z'
      }
    ]
  };

  beforeEach(async () => {
    mockCashService = {
      currentSession: signal<CashSessionSummary | null>(mockSession),
      isLoadingCurrent: signal<boolean>(false),
      getCurrentSession: vi.fn().mockReturnValue(of(mockSession)),
      getSessionDetail: vi.fn().mockReturnValue(of(mockDetail)),
      openSession: vi.fn().mockReturnValue(of(mockSession)),
      registerSale: vi.fn().mockReturnValue(of(mockDetail.sales[0])),
      closeSession: vi.fn().mockReturnValue(of({ ...mockSession, status: 'CERRADA' }))
    };

    await TestBed.configureTestingModule({
      imports: [PosComponent],
      providers: [
        provideRouter([]),
        { provide: CashService, useValue: mockCashService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe inicializarse y cargar la sesión de caja activa en el terminal POS', () => {
    expect(component).toBeTruthy();
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
    expect(mockCashService.getSessionDetail).toHaveBeenCalledWith('session-1');
    expect(component.recentSales().length).toBe(1);
  });

  it('debe mostrar el estado de caja cerrada y no permitir venta si no hay sesión abierta', () => {
    mockCashService.currentSession.set(null);
    fixture.detectChanges();

    // Intentar abrir modal de venta con caja cerrada
    component.openSaleModal();
    expect(component.isSaleModalVisible()).toBe(false);
    expect(component.feedbackMessage()?.type).toBe('error');

    // Debe permitir abrir modal de apertura
    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
  });

  it('debe abrir y cerrar los modales correspondientes', () => {
    component.openCashModal();
    expect(component.isOpenModalVisible()).toBe(true);
    component.closeOpenModal();
    expect(component.isOpenModalVisible()).toBe(false);

    component.openSaleModal();
    expect(component.isSaleModalVisible()).toBe(true);
    component.closeSaleModal();
    expect(component.isSaleModalVisible()).toBe(false);

    component.openCloseModal();
    expect(component.isCloseModalVisible()).toBe(true);
    component.closeCloseModal();
    expect(component.isCloseModalVisible()).toBe(false);

    component.openInfoModal();
    expect(component.isInfoModalVisible()).toBe(true);
    component.closeInfoModal();
    expect(component.isInfoModalVisible()).toBe(false);
  });

  it('debe contener el botón Información en cabecera y abrir el modal al pulsarlo', () => {
    const infoButton = fixture.debugElement.query(By.css('.header-actions button.btn-secondary'));
    expect(infoButton).toBeTruthy();
    expect(infoButton.nativeElement.textContent).toContain('Información');

    infoButton.nativeElement.click();
    expect(component.isInfoModalVisible()).toBe(true);
  });

  it('no debe mostrar la sección de información ni comentario directamente en el POS y debe existir un único botón Cerrar Caja', () => {
    // La sección independiente del turno y el comentario ya no deben estar en el workspace
    expect(fixture.debugElement.query(By.css('.turn-status-banner'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.opening-comment-strip'))).toBeNull();

    // Solo debe haber un botón "Cerrar Caja" en todo el POS (en la cabecera)
    const closeButtons = fixture.debugElement.queryAll(By.css('button')).filter(b =>
      (b.nativeElement.textContent || '').toLowerCase().includes('cerrar caja')
    );
    expect(closeButtons.length).toBe(1);
  });

  it('no debe mostrar el botón Registrar Venta en la cabecera junto a Cerrar Caja, pero sí en la sección de transacciones', () => {
    // En la cabecera (.header-actions) solo deben existir Información y Cerrar Caja
    const headerSaleBtn = fixture.debugElement.query(By.css('.header-actions button.btn-primary'));
    expect(headerSaleBtn).toBeNull();

    const headerButtons = fixture.debugElement.queryAll(By.css('.header-actions button'));
    expect(headerButtons.length).toBe(2);
    expect(headerButtons[0].nativeElement.textContent).toContain('Información');
    expect(headerButtons[1].nativeElement.textContent).toContain('Cerrar Caja');

    // El flujo de ventas sigue operativo desde la tabla de transacciones
    const txSaleBtn = fixture.debugElement.query(By.css('.transactions-header button.btn-primary'));
    expect(txSaleBtn).toBeTruthy();
    expect(txSaleBtn.nativeElement.textContent).toContain('Registrar Venta');

    txSaleBtn.nativeElement.click();
    expect(component.isSaleModalVisible()).toBe(true);
  });

  it('no debe utilizar emojis como iconos en la interfaz del POS y debe emplear SVGs vectoriales', () => {
    const posText = fixture.nativeElement.textContent || '';
    const emojiRegex = /[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;
    expect(emojiRegex.test(posText)).toBe(false);

    // Debe contener iconos SVG en KPIs y botones
    const svgIcons = fixture.debugElement.queryAll(By.css('svg'));
    expect(svgIcons.length).toBeGreaterThan(5);
  });

  it('debe confirmar la apertura de caja y recargar la sesión', () => {
    component.handleConfirmOpen({ openingAmount: 100, openingComment: 'Inicio' });

    expect(mockCashService.openSession).toHaveBeenCalledWith({ openingAmount: 100, openingComment: 'Inicio' });
    expect(component.isOpenModalVisible()).toBe(false);
  });

  it('debe confirmar el registro de venta y actualizar lista', () => {
    component.handleConfirmSale({ totalAmount: 50, paymentMethod: 'EFECTIVO' });

    expect(mockCashService.registerSale).toHaveBeenCalledWith({ totalAmount: 50, paymentMethod: 'EFECTIVO' });
    expect(component.isSaleModalVisible()).toBe(false);
  });

  it('debe confirmar el cierre de caja y reiniciar estado', () => {
    component.handleConfirmClose({ closingAmount: 150 });

    expect(mockCashService.closeSession).toHaveBeenCalledWith({ closingAmount: 150 });
    expect(component.isCloseModalVisible()).toBe(false);
    expect(component.recentSales().length).toBe(0);
  });

  it('restricción estricta: no debe contener enlaces ni botones hacia el historial de cajas en el DOM', () => {
    const historyLinks = fixture.debugElement.queryAll(By.css('a[href*="cash/history"], a[routerLink*="cash/history"]'));
    expect(historyLinks.length).toBe(0);

    const allButtonsAndLinks = fixture.debugElement.queryAll(By.css('button, a'));
    for (const el of allButtonsAndLinks) {
      const text = el.nativeElement.textContent || '';
      expect(text.toLowerCase()).not.toContain('historial de cajas');
      expect(text.toLowerCase()).not.toContain('registro de cajas');
    }
  });
});
