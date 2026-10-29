import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CashExpenseModalComponent } from './cash-expense-modal.component';
import { FinancialCategoryService } from '../../../../core/finance/financial-category.service';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';
import { CashService } from '../../../../core/cash/cash.service';

describe('CashExpenseModalComponent', () => {
  let component: CashExpenseModalComponent;
  let fixture: ComponentFixture<CashExpenseModalComponent>;
  let mockTransactionService: any;
  let mockCategoryService: any;
  let mockCashService: any;

  beforeEach(async () => {
    mockTransactionService = {
      createCashExpense: vi.fn().mockReturnValue(of({ id: 'tx-exp-1', amount: 35.5 }))
    };
    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of([
        { id: 'aj', name: 'Ajuste', type: 'GASTO', manualEntryAllowed: false },
        { id: 'cat-1', name: 'Comida', type: 'GASTO', manualEntryAllowed: true },
        { id: 'ped', name: 'Pedido', type: 'GASTO', manualEntryAllowed: false }
      ]))
    };
    mockCashService = {
      currentSession: signal<any>({ id: 'session-1', expectedCash: 100 }),
      getCurrentSession: vi.fn().mockReturnValue(of({ id: 'session-1', expectedCash: 100 }))
    };

    await TestBed.configureTestingModule({
      imports: [CashExpenseModalComponent],
      providers: [
        { provide: FinancialTransactionService, useValue: mockTransactionService },
        { provide: FinancialCategoryService, useValue: mockCategoryService },
        { provide: CashService, useValue: mockCashService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CashExpenseModalComponent);
    component = fixture.componentInstance;
    component.isOpen = true;
    fixture.detectChanges();
  });

  it('ofrece solo las categorías que admiten gastos manuales y no elige ninguna de antemano', () => {
    expect(mockCategoryService.getCategories).toHaveBeenCalledWith('GASTO');
    expect(component.categories().map((c) => c.id)).toEqual(['cat-1']);
    expect(component.form.controls.categoryId.value).toBe('');
  });

  it('registra el gasto y emite el mensaje', () => {
    const saved = vi.fn();
    component.saved.subscribe(saved);
    component.form.patchValue({ categoryId: 'cat-1', amount: 35.5, description: ' Compra de hielo ', notes: 'Factura #123' });

    component.submit();

    expect(mockTransactionService.createCashExpense).toHaveBeenCalledWith({
      categoryId: 'cat-1',
      amount: 35.5,
      description: 'Compra de hielo',
      notes: 'Factura #123'
    });
    expect(saved).toHaveBeenCalledWith('Gasto de caja de Bs 35,50 registrado exitosamente.');
  });

  it('marca los campos y no envía si el formulario es inválido', () => {
    component.submit();

    expect(component.form.controls.categoryId.touched).toBe(true);
    expect(component.form.controls.amount.touched).toBe(true);
    expect(component.form.controls.description.touched).toBe(true);
    expect(mockTransactionService.createCashExpense).not.toHaveBeenCalled();
  });

  it('muestra el error del servidor', () => {
    mockTransactionService.createCashExpense.mockReturnValue(throwError(() => ({ error: { message: 'Saldo' } })));
    component.form.patchValue({ categoryId: 'cat-1', amount: 10, description: 'Taxi' });

    component.submit();

    expect(component.error()).toBe('Saldo');
  });

  it('avisa (sin bloquear) si el monto supera el efectivo esperado', () => {
    component.form.controls.amount.setValue(150);
    fixture.detectChanges();
    const warning = fixture.debugElement.query(By.css('.cash-limit-warning'));
    expect(warning).toBeTruthy();
    expect(warning.nativeElement.textContent).toContain('100.00');

    component.form.controls.amount.setValue(80);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.cash-limit-warning'))).toBeNull();
  });
});
