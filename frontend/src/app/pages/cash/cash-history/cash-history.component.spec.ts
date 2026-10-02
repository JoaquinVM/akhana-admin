import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CashHistoryComponent } from './cash-history.component';
import { CashService } from '../../../core/cash/cash.service';
import { CashSessionSummary, CashSessionDetail } from '../../../core/cash/models/cash.models';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';

describe('CashHistoryComponent', () => {
  let component: CashHistoryComponent;
  let fixture: ComponentFixture<CashHistoryComponent>;
  let mockCashService: any;

  const mockSessions: CashSessionSummary[] = [
    {
      id: 'session-1',
      sessionNumber: 1,
      status: 'ABIERTA',
      openingAmount: 100.0,
      openingComment: 'Inicio turno mañana',
      openedBy: 'admin',
      openedAt: '2026-10-01T08:00:00Z',
      closingAmount: null,
      closingComment: null,
      closedBy: null,
      closedAt: null,
      totalSalesCash: 50.0,
      totalSalesQr: 25.0,
      totalSales: 75.0,
      expectedCash: 150.0,
      difference: null,
      salesCount: 2
    },
    {
      id: 'session-2',
      sessionNumber: 2,
      status: 'CERRADA',
      openingAmount: 150.0,
      openingComment: 'Turno tarde',
      openedBy: 'cajero',
      openedAt: '2026-09-30T14:00:00Z',
      closingAmount: 300.0,
      closingComment: 'Cierre con cuadre',
      closedBy: 'cajero',
      closedAt: '2026-09-30T22:00:00Z',
      totalSalesCash: 150.0,
      totalSalesQr: 50.0,
      totalSales: 200.0,
      expectedCash: 300.0,
      difference: 0.0,
      salesCount: 5
    }
  ];

  const mockDetail: CashSessionDetail = {
    ...mockSessions[1],
    cuts: [],
    sales: []
  };

  beforeEach(async () => {
    mockCashService = {
      getAllSessions: vi.fn().mockReturnValue(of(mockSessions)),
      getSessionDetail: vi.fn().mockReturnValue(of(mockDetail))
    };

    await TestBed.configureTestingModule({
      imports: [CashHistoryComponent],
      providers: [
        { provide: CashService, useValue: mockCashService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CashHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse e inicializar el listado histórico de sesiones', () => {
    expect(component).toBeTruthy();
    expect(mockCashService.getAllSessions).toHaveBeenCalled();
    expect(component.sessions().length).toBe(2);
    expect(component.filteredSessions().length).toBe(2);
  });

  it('no debe contener el botón "Ir a caja actual" ni enlaces directos hacia caja actual', () => {
    const allLinksAndButtons = fixture.debugElement.queryAll(By.css('a, button'));
    for (const el of allLinksAndButtons) {
      const text = (el.nativeElement.textContent || '').trim().toLowerCase();
      expect(text).not.toContain('ir a caja actual');
      expect(text).not.toContain('caja actual');
    }

    const currentLinks = fixture.debugElement.queryAll(By.css('a[href*="current"], a[routerLink*="current"]'));
    expect(currentLinks.length).toBe(0);
  });

  it('debe mostrar el indicador visual y etiqueta correspondiente para sesiones abiertas y cerradas', () => {
    const activeBadge = fixture.debugElement.query(By.css('.status-badge.badge-active'));
    expect(activeBadge).toBeTruthy();
    expect(activeBadge.nativeElement.textContent.trim()).toBe('Abierta');

    const closedBadge = fixture.debugElement.query(By.css('.status-badge.badge-closed'));
    expect(closedBadge).toBeTruthy();
    expect(closedBadge.nativeElement.textContent.trim()).toBe('Cerrada');

    // Ambos deben compartir la clase base status-badge que define el indicador dot ::before
    expect(activeBadge.nativeElement.classList.contains('status-badge')).toBe(true);
    expect(closedBadge.nativeElement.classList.contains('status-badge')).toBe(true);
  });

  it('debe filtrar las sesiones por estado correctamente', () => {
    component.setStatusFilter('ABIERTA');
    fixture.detectChanges();
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].status).toBe('ABIERTA');

    component.setStatusFilter('CERRADA');
    fixture.detectChanges();
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].status).toBe('CERRADA');

    component.setStatusFilter('ALL');
    fixture.detectChanges();
    expect(component.filteredSessions().length).toBe(2);
  });

  it('debe abrir y cerrar el modal de detalle de auditoría', () => {
    component.openDetail(mockSessions[0]);
    expect(mockCashService.getSessionDetail).toHaveBeenCalledWith('session-1');
    expect(component.isDetailModalVisible()).toBe(true);
    expect(component.selectedDetail()).toEqual(mockDetail);

    component.closeDetail();
    expect(component.isDetailModalVisible()).toBe(false);
    expect(component.selectedDetail()).toBeNull();
  });
});
