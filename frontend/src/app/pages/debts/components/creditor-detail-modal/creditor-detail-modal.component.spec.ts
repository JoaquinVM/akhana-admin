import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CreditorDetailModalComponent } from './creditor-detail-modal.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CashService } from '../../../../core/cash/cash.service';
import { CreditorHistory, CreditorHistoryEntry } from '../../../../core/debt/models/debt.models';

const repayment: CreditorHistoryEntry = {
  type: 'REPAYMENT', id: 'rep-1', date: '2026-10-25T14:00:00Z', amount: 150, purchaseId: null, purchaseNumber: null,
  supplierName: null, saleId: null, saleNumber: null, accountId: 'acc-1', accountName: 'Bisa', comment: null,
  concept: 'PURCHASE', voided: false, voidedBy: null,
  voidedAt: null, voidReason: null, pendingAmount: null, paidOn: null, paidItems: ['PED-000001'], balanceAfter: 150
};
const history: CreditorHistory = {
  creditor: {
    id: 'cre-1', name: 'Mami', phone: null, description: null, status: 'ACTIVO', debt: 150, createdBy: 'operator',
    createdAt: '2026-10-25T12:00:00Z', updatedBy: null, updatedAt: null, deletedBy: null, deletedAt: null
  },
  entries: [
    { ...repayment, type: 'PAYMENT', id: 'pay-1', amount: 300, purchaseId: 'pur-1', purchaseNumber: 'PED-000001',
      supplierName: 'Granel', accountId: null, accountName: null, pendingAmount: 150, paidItems: null, balanceAfter: 300 },
    repayment
  ]
};

describe('CreditorDetailModalComponent', () => {
  let component: CreditorDetailModalComponent;
  let fixture: ComponentFixture<CreditorDetailModalComponent>;
  let creditorService: any;

  const cashSession = vi.fn();

  beforeEach(async () => {
    cashSession.mockReset();
    cashSession.mockReturnValue(of({ id: 's1', expectedCash: 1000 }));
    creditorService = {
      getHistory: vi.fn().mockReturnValue(of(history)),
      registerRepayment: vi.fn().mockReturnValue(of({ ...history, creditor: { ...history.creditor, debt: 0 } })),
      voidRepayment: vi.fn().mockReturnValue(of(history))
    };
    await TestBed.configureTestingModule({
      imports: [CreditorDetailModalComponent],
      providers: [
        { provide: CreditorService, useValue: creditorService },
        { provide: CashService, useValue: { currentSession: signal<any>(null), getCurrentSession: cashSession } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CreditorDetailModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('creditorId', 'cre-1');
    fixture.detectChanges();
  });

  it('DEU-04: carga el historial y, con deuda, muestra para pagar lo que falta', () => {
    expect(creditorService.getHistory).toHaveBeenCalledWith('cre-1');
    expect(component.debt()).toBe(150);
    expect(component.canRepay()).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-creditor-repayment-form')).not.toBeNull();
  });

  it('DEU-04 (062): cada deuda dice si está pagada, cuánto falta o si está pendiente, y la devolución qué pagó', () => {
    const sale = (id: string, amount: number, pendingAmount: number, paidOn: string | null = null): CreditorHistoryEntry => ({
      ...repayment, type: 'SALE', id, amount, saleId: id, saleNumber: 'VTA-' + id, accountId: null, accountName: null,
      concept: null, pendingAmount, paidOn, paidItems: null
    });
    creditorService.getHistory.mockReturnValue(of({
      creditor: { ...history.creditor, debt: 57 },
      entries: [
        sale('260', 30, 0, '2026-11-12'),
        sale('357', 30, 30),
        sale('506', 27, 12),
        { ...repayment, id: 'rep-2', amount: 30, concept: 'THIRD_PARTY', paidItems: ['VTA-260'] }
      ]
    }));
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.table-responsive > table > tbody > tr'))
      .filter(r => !r.closest('app-creditor-repayment-form'));
    expect(rows[0].querySelector('.debt-state')!.textContent).toContain('Pagada el 12/11');
    expect(rows[1].querySelector('.debt-state')!.textContent).toContain('Pendiente');
    expect(rows[2].querySelector('.debt-state')!.textContent).toContain('Falta Bs 12.00');
    expect(rows[3].querySelector('.debt-state')).toBeNull();
    expect(rows[3].textContent).toContain('Pagó: VTA-260');
  });

  it('DEU-04: sin deuda no muestra para pagar', () => {
    creditorService.getHistory.mockReturnValue(of({ ...history, creditor: { ...history.creditor, debt: 0 } }));
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-creditor-repayment-form')).toBeNull();
  });

  it('CAJ-11: una devolución desde Caja cargada pasada la medianoche se ve en el día de su caja', () => {
    creditorService.getHistory.mockReturnValue(of({
      ...history,
      entries: [{ ...history.entries[0], operationDate: '2026-10-25' },
        { ...repayment, date: '2026-11-01T04:30:00Z', operationDate: '2026-10-31' }]
    }));
    fixture.componentRef.setInput('refreshKey', 2);
    fixture.detectChanges();
    // The history rows, not those of the form to pay
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'))
      .filter(r => !r.closest('app-creditor-repayment-form'));
    expect(rows[0].querySelector('.day-mark')).toBeNull();
    expect(rows[1].querySelector('.day-mark')?.textContent).toContain('Día 31/10 · cargada el 01/11 00:30');
  });

  it('DEU-02: el envío que pagó suma a la deuda, se ve con su pedido y abre el pedido', () => {
    creditorService.getHistory.mockReturnValue(of({
      ...history,
      creditor: { ...history.creditor, debt: 25 },
      entries: [{ ...history.entries[0], type: 'SHIPPING', id: 'shp-1', amount: 25, purchaseId: 'pur-9',
        purchaseNumber: 'PED-000009', supplierName: 'Granel', balanceAfter: 25 }]
    }));
    fixture.componentRef.setInput('refreshKey', 3);
    fixture.detectChanges();
    const row = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'))
      .find(r => !r.closest('app-creditor-repayment-form'))!;
    expect(row.textContent).toContain('Pagó el envío del pedido PED-000009');
    expect(row.textContent).toContain('Granel');
    expect(row.textContent).toContain('+ Bs 25.00');

    const opened: string[] = [];
    component.openPurchase.subscribe(id => opened.push(id));
    (Array.from(row.querySelectorAll('button')).find(b => b.textContent!.includes('Ver pedido')) as HTMLButtonElement).click();
    expect(opened).toEqual(['pur-9']);
  });

  it('DEU-04: vuelve a cargar cuando cambia la clave de refresco', () => {
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    expect(creditorService.getHistory).toHaveBeenCalledTimes(2);
  });

  it('DEU-03 (062): registra lo marcado y avisa a la lista', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);
    const request = { accountId: 'acc-1', amount: 150, comment: 'en mano', items: [{ type: 'PAYMENT' as const, id: 'pay-1' }] };
    component.registerRepayment(request);
    expect(creditorService.registerRepayment).toHaveBeenCalledWith('cre-1', request);
    expect(component.canRepay()).toBe(false);
    expect(component.isRepaying()).toBe(false);
    expect(changed).toHaveBeenCalled();
  });

  it('DEU-03 (062): muestra el rechazo del servidor en el formulario', () => {
    creditorService.registerRepayment.mockReturnValue(throwError(() => ({ error: { message: 'Las deudas elegidas cambiaron; vuelva a abrir el detalle.' } })));
    component.registerRepayment({ accountId: 'acc-1', amount: 150, comment: null, items: [{ type: 'PAYMENT', id: 'pay-1' }] });
    fixture.detectChanges();
    expect(component.repaymentError()).toBe('Las deudas elegidas cambiaron; vuelva a abrir el detalle.');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-creditor-repayment-form .alert-danger')!.textContent)
      .toContain('Las deudas elegidas cambiaron');
  });

  it('DEU-03: anula una devolución con motivo', () => {
    component.openVoid(repayment);
    expect(component.voidMessage()).toContain('desde Bisa');
    expect(component.voidMessage()).toContain('Vuelven a quedar pendientes: PED-000001.');
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
