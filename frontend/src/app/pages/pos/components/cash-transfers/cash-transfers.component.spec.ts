import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CashTransfersComponent } from './cash-transfers.component';
import { FinancialTransactionService } from '../../../../core/finance/financial-transaction.service';

describe('CashTransfersComponent', () => {
  let component: CashTransfersComponent;
  let fixture: ComponentFixture<CashTransfersComponent>;
  let mockTransactionService: any;

  const caja = { id: 'acc-caja', name: 'Caja', color: '#2E5B27', cashAccount: true };
  const efectivo = { id: 'acc-efe', name: 'Efectivo', color: '#F5B800', cashAccount: false };

  const withdrawal: any = {
    id: 'tx-1', type: 'TRANSFERENCIA', amount: 300, status: 'ACTIVO',
    sourceAccount: caja, destinationAccount: efectivo, description: 'Retiro de caja a Efectivo', notes: 'Depósito'
  };
  const income: any = {
    id: 'tx-2', type: 'TRANSFERENCIA', amount: 50, status: 'ACTIVO',
    sourceAccount: efectivo, destinationAccount: caja, description: 'Ingreso a caja desde Efectivo', notes: 'Cambio'
  };

  beforeEach(async () => {
    mockTransactionService = {
      getCashTransferAccounts: vi.fn().mockReturnValue(of([{ ...efectivo, qrAccount: false }])),
      createCashTransfer: vi.fn().mockReturnValue(of({ ...withdrawal, amount: 120 })),
      voidCashTransfer: vi.fn().mockReturnValue(of({ ...withdrawal, status: 'ANULADO' }))
    };

    await TestBed.configureTestingModule({
      imports: [CashTransfersComponent],
      providers: [{ provide: FinancialTransactionService, useValue: mockTransactionService }]
    }).compileComponents();

    fixture = TestBed.createComponent(CashTransfersComponent);
    component = fixture.componentInstance;
    component.transfers = [withdrawal, income, { ...income, id: 'tx-3', status: 'ANULADO' }];
    component.canOperate = true;
    fixture.detectChanges();
  });

  it('distingue ingresos y retiros por la cuenta Caja y suma solo los activos', () => {
    expect(component.directionOf(withdrawal)).toBe('RETIRO');
    expect(component.directionOf(income)).toBe('INGRESO');
    expect(component.otherAccountOf(withdrawal)).toBe('Efectivo');
    expect(component.otherAccountOf(income)).toBe('Efectivo');
    expect(component.totalWithdrawals()).toBe(300);
    expect(component.totalIncome()).toBe(50);
  });

  it('registra un retiro con cuenta, monto y comentario y avisa al POS', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);

    component.openForm('RETIRO');
    expect(component.isFormVisible()).toBe(true);
    expect(component.form.value.accountId).toBe('acc-efe');

    component.form.patchValue({ amount: 120, comment: '  Depósito al banco ' });
    component.submit();

    expect(mockTransactionService.createCashTransfer).toHaveBeenCalledWith({
      direction: 'RETIRO', accountId: 'acc-efe', amount: 120, comment: 'Depósito al banco'
    });
    expect(component.isFormVisible()).toBe(false);
    expect(changed).toHaveBeenCalledWith('Retiro de caja de Bs. 120.00 registrado exitosamente.');
  });

  it('no envía el formulario sin comentario', () => {
    component.openForm('INGRESO');
    component.form.patchValue({ amount: 10, comment: '' });
    component.submit();

    expect(mockTransactionService.createCashTransfer).not.toHaveBeenCalled();
  });

  it('muestra el error del servidor sin cerrar el formulario', () => {
    mockTransactionService.createCashTransfer.mockReturnValue(
      throwError(() => ({ error: { message: 'Saldo insuficiente en la cuenta origen (Caja).' } }))
    );
    component.openForm('RETIRO');
    component.form.patchValue({ amount: 9999, comment: 'Retiro grande' });
    component.submit();

    expect(component.formError()).toBe('Saldo insuficiente en la cuenta origen (Caja).');
    expect(component.isFormVisible()).toBe(true);
  });

  it('anula un movimiento con motivo', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);

    component.openVoid(withdrawal);
    component.voidReason.set('Monto equivocado');
    component.confirmVoid();

    expect(mockTransactionService.voidCashTransfer).toHaveBeenCalledWith('tx-1', { voidReason: 'Monto equivocado' });
    expect(component.transferToVoid()).toBeNull();
    expect(changed).toHaveBeenCalled();
  });

  it('no abre el formulario si la caja no está abierta', () => {
    component.canOperate = false;
    component.openForm('RETIRO');

    expect(mockTransactionService.getCashTransferAccounts).not.toHaveBeenCalled();
    expect(component.isFormVisible()).toBe(false);
  });
});
