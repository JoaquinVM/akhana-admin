import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CashExpensesComponent } from './cash-expenses.component';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';

describe('CashExpensesComponent', () => {
  let fixture: ComponentFixture<CashExpensesComponent>;
  let component: CashExpensesComponent;
  let mockTransactionService: any;

  const expense: any = {
    id: 'tx-exp-1',
    type: 'GASTO_MANUAL',
    amount: 45.0,
    transactionDate: '2026-10-01T11:00:00Z',
    description: 'Compra de bolsas kraft',
    notes: '500 unidades',
    status: 'ACTIVO',
    category: { id: 'cat-1', name: 'Insumos', type: 'GASTO', color: '#2E5B27' },
    createdBy: 'admin'
  };

  beforeEach(async () => {
    mockTransactionService = {
      voidCashExpense: vi.fn().mockReturnValue(of({ ...expense, status: 'ANULADO' }))
    };
    await TestBed.configureTestingModule({
      imports: [CashExpensesComponent],
      providers: [{ provide: FinancialTransactionService, useValue: mockTransactionService }]
    }).compileComponents();
    fixture = TestBed.createComponent(CashExpensesComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('expenses', [expense, { ...expense, id: 'tx-exp-2', status: 'ANULADO' }]);
    fixture.componentRef.setInput('canOperate', true);
    fixture.detectChanges();
  });

  it('lista los gastos con categoría, notas y monto; el anulado no se puede anular otra vez', () => {
    const table = fixture.debugElement.query(By.css('.table')).nativeElement;
    expect(table.textContent).toContain('Compra de bolsas kraft');
    expect(table.textContent).toContain('Insumos');
    expect(table.textContent).toContain('500 unidades');
    const buttons = fixture.debugElement.queryAll(By.css('.btn-delete'));
    expect(buttons[0].nativeElement.disabled).toBe(false);
    expect(buttons[1].nativeElement.disabled).toBe(true);
  });

  it('sin gastos ofrece registrar uno y lo pide al POS', () => {
    const register = vi.fn();
    component.registerExpense.subscribe(register);
    fixture.componentRef.setInput('expenses', []);
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.empty-actions button')).nativeElement.click();
    expect(register).toHaveBeenCalled();
  });

  it('RG-03: anula con motivo y avisa al POS; sin motivo no confirma', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);
    component.openVoidModal(expense);
    fixture.detectChanges();
    expect(component.voidTarget()?.id).toBe('tx-exp-1');

    component.confirmVoid();
    expect(mockTransactionService.voidCashExpense).toHaveBeenCalledWith('tx-exp-1', { voidReason: '' });
    // The confirm button stays disabled while the reason is blank; the server rejects it anyway
    mockTransactionService.voidCashExpense.mockClear();

    component.openVoidModal(expense);
    component.voidReason.set('Error en comprobante');
    component.confirmVoid();

    expect(mockTransactionService.voidCashExpense).toHaveBeenCalledWith('tx-exp-1', { voidReason: 'Error en comprobante' });
    expect(changed).toHaveBeenCalledWith('Gasto de Bs 45,00 anulado exitosamente.');
    expect(component.voidTarget()).toBeNull();
  });

  it('muestra el error del servidor y deja el modal abierto', () => {
    mockTransactionService.voidCashExpense.mockReturnValue(throwError(() => ({ error: { message: 'No se pueden anular gastos de una caja cerrada.' } })));
    component.openVoidModal(expense);
    component.voidReason.set('Error');
    component.confirmVoid();

    expect(component.voidError()).toBe('No se pueden anular gastos de una caja cerrada.');
    expect(component.voidTarget()?.id).toBe('tx-exp-1');
  });

  it('con la caja cerrada no abre la anulación', () => {
    fixture.componentRef.setInput('canOperate', false);
    component.openVoidModal(expense);
    expect(component.voidTarget()).toBeNull();
  });
});
