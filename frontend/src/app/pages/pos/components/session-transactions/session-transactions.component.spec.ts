import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { SessionTransactionsComponent } from './session-transactions.component';
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
