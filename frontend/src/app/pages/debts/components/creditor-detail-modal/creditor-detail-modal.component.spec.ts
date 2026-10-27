import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CreditorDetailModalComponent } from './creditor-detail-modal.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CreditorHistory, CreditorHistoryEntry } from '../../../../core/debt/models/debt.models';

const repayment: CreditorHistoryEntry = {
  type: 'REPAYMENT', id: 'rep-1', date: '2026-10-25T14:00:00Z', amount: 150, purchaseId: null, purchaseNumber: null,
  supplierName: null, saleId: null, saleNumber: null, accountId: 'acc-1', accountName: 'Bisa', comment: null,
  concept: 'PURCHASE', voided: false, voidedBy: null,
  voidedAt: null, voidReason: null, balanceAfter: 150
};
const history: CreditorHistory = {
  creditor: {
    id: 'cre-1', name: 'Mami', phone: null, description: null, status: 'ACTIVO', debt: 150, createdBy: 'operator',
    createdAt: '2026-10-25T12:00:00Z', updatedBy: null, updatedAt: null, deletedBy: null, deletedAt: null
  },
  entries: [
    { ...repayment, type: 'PAYMENT', id: 'pay-1', amount: 300, purchaseId: 'pur-1', purchaseNumber: 'PED-000001',
      supplierName: 'Granel', accountId: null, accountName: null, balanceAfter: 300 },
    repayment
  ]
};

describe('CreditorDetailModalComponent', () => {
  let component: CreditorDetailModalComponent;
  let fixture: ComponentFixture<CreditorDetailModalComponent>;
  let creditorService: any;

  beforeEach(async () => {
    creditorService = {
      getHistory: vi.fn().mockReturnValue(of(history)),
      registerRepayment: vi.fn().mockReturnValue(of({ ...history, creditor: { ...history.creditor, debt: 0 } })),
      voidRepayment: vi.fn().mockReturnValue(of(history))
    };
    await TestBed.configureTestingModule({
      imports: [CreditorDetailModalComponent],
      providers: [{ provide: CreditorService, useValue: creditorService }]
    }).compileComponents();

    fixture = TestBed.createComponent(CreditorDetailModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('creditorId', 'cre-1');
    fixture.detectChanges();
  });

  it('DEU-04: carga el historial y propone la deuda como monto a devolver', () => {
    expect(creditorService.getHistory).toHaveBeenCalledWith('cre-1');
    expect(component.debt()).toBe(150);
    expect(component.repaymentAmount()).toBe(150);
    expect(component.canRepay()).toBe(true);
  });

  it('DEU-04: vuelve a cargar cuando cambia la clave de refresco', () => {
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    expect(creditorService.getHistory).toHaveBeenCalledTimes(2);
  });

  it('DEU-03: exige cuenta y registra la devolución', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);
    component.registerRepayment();
    expect(component.repaymentError()).toBe('Elija la cuenta desde la que se devuelve.');

    component.repaymentAccountId.set('acc-1');
    component.repaymentComment.set('  en mano ');
    component.registerRepayment();
    expect(creditorService.registerRepayment).toHaveBeenCalledWith('cre-1', { accountId: 'acc-1', amount: 150, comment: 'en mano',
      concept: 'PURCHASE' });
    expect(component.canRepay()).toBe(false);
    expect(changed).toHaveBeenCalled();
  });

  it('DEU-03: muestra el rechazo del servidor', () => {
    creditorService.registerRepayment.mockReturnValue(throwError(() => ({ error: { message: 'La devolución supera la deuda con Mami (Bs 150.00).' } })));
    component.repaymentAccountId.set('acc-1');
    component.repaymentAmount.set(200);
    component.registerRepayment();
    expect(component.repaymentError()).toBe('La devolución supera la deuda con Mami (Bs 150.00).');
  });

  it('DEU-03: anula una devolución con motivo', () => {
    component.openVoid(repayment);
    expect(component.voidMessage()).toContain('desde Bisa');
    component.confirmVoid('Monto errado');
    expect(creditorService.voidRepayment).toHaveBeenCalledWith('cre-1', 'rep-1', { voidReason: 'Monto errado' });
    expect(component.voidTarget()).toBeNull();
  });

  it('DEU-03: si la caja está cerrada muestra el error y no cierra', () => {
    creditorService.voidRepayment.mockReturnValue(throwError(() => ({ error: { message: 'La devolución se hizo en una caja cerrada; no se puede anular.' } })));
    component.openVoid(repayment);
    component.confirmVoid('Error');
    expect(component.voidError()).toBe('La devolución se hizo en una caja cerrada; no se puede anular.');
    expect(component.voidTarget()).not.toBeNull();
  });
});
