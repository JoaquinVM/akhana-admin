import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { vi } from 'vitest';
import {
  SessionListState,
  SessionTransactionsComponent,
  SessionTransactionsTab
} from './session-transactions.component';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';
import { Sale } from '../../../../core/cash/models/cash.models';

describe('SessionTransactionsComponent', () => {
  let fixture: ComponentFixture<SessionTransactionsComponent>;
  let component: SessionTransactionsComponent;

  const sale: Sale = {
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
  const expense: any = { id: 'tx-1', type: 'GASTO_MANUAL', amount: 45, status: 'ACTIVO', description: 'Bolsas' };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionTransactionsComponent],
      providers: [{ provide: FinancialTransactionService, useValue: { voidCashExpense: vi.fn().mockReturnValue(of({})) } }]
    }).compileComponents();
    fixture = TestBed.createComponent(SessionTransactionsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('sales', [sale]);
    fixture.componentRef.setInput('expenses', [expense, { ...expense, id: 'tx-2', status: 'ANULADO' }]);
    fixture.componentRef.setInput('transfers', []);
    fixture.componentRef.setInput('canOperate', true);
    fixture.detectChanges();
  });

  const counter = (): string => fixture.nativeElement.querySelector('.transactions-counter').textContent.trim();
  const headerButton = (): HTMLButtonElement | null =>
    fixture.nativeElement.querySelector('.transactions-header-action button');

  it('arranca en Ventas y cuenta las ventas recibidas', () => {
    expect(component.activeTab()).toBe('VENTAS');
    expect(counter()).toBe('1 ventas registradas');
    expect(fixture.nativeElement.querySelector('app-sales-table')).toBeTruthy();
  });

  it('en Compras cuenta solo los gastos activos y aparte los anulados', () => {
    component.setActiveTab('COMPRAS');
    fixture.detectChanges();
    expect(counter()).toBe('1 gastos activos (1 anulados)');
    expect(headerButton()?.textContent).toContain('Registrar gasto');
    expect(fixture.nativeElement.querySelector('app-cash-expenses')).toBeTruthy();
  });

  it('en Ingresos / Retiros no hay botón de cabecera', () => {
    fixture.debugElement.queryAll(By.css('.view-pill'))[2].nativeElement.click();
    fixture.detectChanges();
    expect(component.activeTab()).toBe('MOVIMIENTOS');
    expect(counter()).toBe('0 ingresos y retiros');
    expect(headerButton()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-cash-transfers')).toBeTruthy();
  });

  it('emite registerSale y registerExpense desde los botones de cabecera', () => {
    const registerSale = vi.fn();
    const registerExpense = vi.fn();
    component.registerSale.subscribe(registerSale);
    component.registerExpense.subscribe(registerExpense);

    headerButton()!.click();
    component.setActiveTab('COMPRAS');
    fixture.detectChanges();
    headerButton()!.click();

    expect(registerSale).toHaveBeenCalledTimes(1);
    expect(registerExpense).toHaveBeenCalledTimes(1);
  });

  it('sin ventas muestra el estado vacío con su botón de registrar', () => {
    fixture.componentRef.setInput('sales', []);
    fixture.detectChanges();
    const registerSale = vi.fn();
    component.registerSale.subscribe(registerSale);
    fixture.nativeElement.querySelector('.empty-actions button').click();
    expect(registerSale).toHaveBeenCalled();
  });

  describe('POS-17: una lista vacía sin respuesta no dice que no hay', () => {
    const page = (): string => fixture.nativeElement.textContent;
    const emptyLists = (state: SessionListState) => {
      fixture.componentRef.setInput('sales', []);
      fixture.componentRef.setInput('expenses', []);
      fixture.componentRef.setInput('transfers', []);
      fixture.componentRef.setInput('salesState', state);
      fixture.componentRef.setInput('expensesState', state);
    };
    const openTab = (tab: SessionTransactionsTab) => {
      component.setActiveTab(tab);
      fixture.detectChanges();
    };

    it('mientras cargan, cada pestaña dice que carga', () => {
      emptyLists('loading');
      openTab('VENTAS');
      expect(page()).toContain('Cargando las ventas');
      expect(counter()).toBe('Cargando…');
      expect(page()).not.toContain('No hay ventas');

      openTab('COMPRAS');
      expect(page()).toContain('Cargando los gastos');
      expect(counter()).toBe('Cargando…');
      expect(page()).not.toContain('No hay compras');

      openTab('MOVIMIENTOS');
      expect(page()).toContain('Cargando los ingresos y retiros');
      expect(counter()).toBe('Cargando…');
      expect(page()).not.toContain('No hay ingresos');
    });

    it('si fallan, cada pestaña dice que no pudo, sin cero ni «no hay»', () => {
      emptyLists('failed');
      openTab('VENTAS');
      expect(page()).toContain('No se pudieron cargar las ventas');
      expect(counter()).toBe('Sin datos');

      openTab('COMPRAS');
      expect(page()).toContain('No se pudieron cargar los gastos');
      expect(counter()).toBe('Sin datos');
      expect(page()).not.toContain('No hay compras');
      expect(page()).not.toContain('0 gastos activos');

      openTab('MOVIMIENTOS');
      expect(page()).toContain('No se pudieron cargar los ingresos y retiros');
      expect(counter()).toBe('Sin datos');
      expect(page()).not.toContain('No hay ingresos');
      expect(page()).not.toContain('Bs 0.00');
    });

    it('si solo fallan los gastos, Ventas muestra sus ventas o dice que no hay', () => {
      fixture.componentRef.setInput('expensesState', 'failed');
      fixture.detectChanges();
      expect(page()).not.toContain('No se pudieron cargar las ventas');
      expect(fixture.nativeElement.querySelector('app-sales-table')).toBeTruthy();

      fixture.componentRef.setInput('sales', []);
      fixture.detectChanges();
      expect(page()).toContain('No hay ventas registradas');
    });

    it('una lista con filas las sigue mostrando aunque el refresco falle', () => {
      fixture.componentRef.setInput('salesState', 'failed');
      fixture.componentRef.setInput('expensesState', 'failed');
      openTab('VENTAS');
      expect(fixture.nativeElement.querySelector('app-sales-table')).toBeTruthy();
      openTab('COMPRAS');
      expect(fixture.nativeElement.querySelector('app-cash-expenses')).toBeTruthy();
      expect(counter()).toBe('1 gastos activos (1 anulados)');
    });
  });

  it('reenvía ver detalle y anular de la tabla de ventas', () => {
    const viewDetail = vi.fn();
    const voidSale = vi.fn();
    component.viewDetail.subscribe(viewDetail);
    component.voidSale.subscribe(voidSale);
    fixture.debugElement.query(By.css('.btn-audit')).nativeElement.click();
    fixture.debugElement.query(By.css('.btn-delete')).nativeElement.click();
    expect(viewDetail).toHaveBeenCalledWith(sale);
    expect(voidSale).toHaveBeenCalledWith(sale);
  });

  it('con la caja no abierta bloquea anular ventas', () => {
    fixture.componentRef.setInput('canOperate', false);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.btn-delete')).nativeElement.disabled).toBe(true);
  });
});
