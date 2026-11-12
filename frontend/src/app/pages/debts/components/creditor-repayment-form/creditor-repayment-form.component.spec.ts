import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CreditorRepaymentFormComponent } from './creditor-repayment-form.component';
import { CashService } from '../../../../core/cash/cash.service';
import { CreditorHistoryEntry, CreditorRepaymentRequest } from '../../../../core/debt/models/debt.models';

const base: CreditorHistoryEntry = {
  type: 'PAYMENT', id: 'pay-1', date: '2026-11-03T02:08:00Z', operationDate: '2026-11-02', amount: 122,
  purchaseId: 'pur-1', purchaseNumber: 'PED-H00487', supplierName: 'Savia', saleId: null, saleNumber: null,
  accountId: null, accountName: null, comment: null, concept: null, voided: false, voidedBy: null, voidedAt: null,
  voidReason: null, pendingAmount: 122, paidOn: null, paidItems: null, balanceAfter: 122
};
const sale = (id: string, number: string, amount: number, pending = amount): CreditorHistoryEntry => ({
  ...base, type: 'SALE', id, saleId: id, saleNumber: number, purchaseId: null, purchaseNumber: null, supplierName: null,
  amount, pendingAmount: pending
});
const entries: CreditorHistoryEntry[] = [
  base,
  { ...base, type: 'SHIPPING', id: 'shp-1', purchaseNumber: 'PED-000020', amount: 31, pendingAmount: 31 },
  sale('sal-paid', 'VTA-000126', 20, 0),
  { ...sale('sal-void', 'VTA-000130', 25), voided: true, pendingAmount: null },
  sale('sal-1', 'VTA-000260', 30),
  sale('sal-2', 'VTA-000506', 27, 12.1),
  { ...base, type: 'REPAYMENT', id: 'rep-1', amount: 20, accountName: 'Bisa', pendingAmount: null,
    paidItems: ['VTA-000126'] }
];

describe('CreditorRepaymentFormComponent', () => {
  let component: CreditorRepaymentFormComponent;
  let fixture: ComponentFixture<CreditorRepaymentFormComponent>;
  let sent: CreditorRepaymentRequest[];
  const cashSession = vi.fn();

  const el = (): HTMLElement => fixture.nativeElement;
  const rows = (): HTMLTableRowElement[] => Array.from(el().querySelectorAll('tbody tr'));
  const saveButton = (): HTMLButtonElement => el().querySelector('button[type=submit]')!;

  beforeEach(async () => {
    cashSession.mockReset();
    cashSession.mockReturnValue(of({ id: 's1', expectedCash: 1000 }));
    await TestBed.configureTestingModule({
      imports: [CreditorRepaymentFormComponent],
      providers: [{ provide: CashService, useValue: { currentSession: signal<any>(null), getCurrentSession: cashSession } }]
    }).compileComponents();

    fixture = TestBed.createComponent(CreditorRepaymentFormComponent);
    component = fixture.componentInstance;
    sent = [];
    component.submitted.subscribe(r => sent.push(r));
    fixture.componentRef.setInput('entries', entries);
    fixture.componentRef.setInput('accounts', [
      { id: 'acc-caja', name: 'Caja', color: '#000', cashAccount: true, qrAccount: false },
      { id: 'acc-bisa', name: 'Bisa', color: '#000', cashAccount: false, qrAccount: false }
    ]);
    fixture.detectChanges();
  });

  it('DEU-03 (062): lista solo lo pendiente, sin lo pagado, lo anulado ni las devoluciones, con lo que falta', () => {
    expect(rows().map(r => r.querySelector('.entry-title')!.textContent!.trim())).toEqual([
      'Pedido PED-H00487', 'Envío del pedido PED-000020', 'Venta VTA-000260', 'Venta VTA-000506'
    ]);
    const partial = rows()[3].textContent!;
    expect(partial).toContain('Bs 12.10');
    expect(partial).toContain('de Bs 27.00');
    expect(saveButton().disabled).toBe(true);
  });

  it('DEU-03 (062): el total es la suma de lo marcado, también al marcar todas, al centavo', () => {
    component.toggle(entries[4]);
    component.toggle(entries[5]);
    fixture.detectChanges();
    expect(component.total()).toBe(42.1);
    expect(el().querySelector('.selection-total')!.textContent).toContain('2 de 4');
    expect(saveButton().textContent).toContain('Pagar Bs 42.10');

    // A click on the row ticks it too, and again unticks it
    rows()[0].click();
    fixture.detectChanges();
    expect(component.total()).toBe(164.1);
    rows()[0].click();
    expect(component.total()).toBe(42.1);

    component.toggleAll();
    expect(component.total()).toBe(195.1);
    expect(component.allSelected()).toBe(true);
    component.toggleAll();
    expect(component.total()).toBe(0);
  });

  it('DEU-03 (062): con ventas y pedidos marcados avisa que se registran Pago a tercero y Devolución', () => {
    component.toggle(entries[4]);
    fixture.detectChanges();
    expect(el().querySelector('.concept-note')).toBeNull();
    component.toggle(entries[0]);
    component.toggle(entries[1]);
    fixture.detectChanges();
    const note = el().querySelector('.concept-note')!.textContent!.replace(/\s+/g, ' ');
    expect(note).toContain('Pago a tercero por Bs 30.00');
    expect(note).toContain('Devolución por Bs 153.00');
  });

  it('DEU-03 (062): exige algo marcado y la cuenta, y envía lo marcado con el total que mostró', () => {
    component.submit();
    expect(component.localError()).toBe('Marque las deudas que se pagan.');
    component.toggle(entries[1]);
    component.toggle(entries[5]);
    component.submit();
    expect(component.localError()).toBe('Elija la cuenta desde la que se paga.');
    expect(sent).toEqual([]);

    component.accountId.set('acc-bisa');
    component.comment.set('  en mano ');
    component.submit();
    expect(sent).toEqual([{
      accountId: 'acc-bisa', amount: 43.1, comment: 'en mano',
      items: [{ type: 'SHIPPING', id: 'shp-1' }, { type: 'SALE', id: 'sal-2' }]
    }]);
  });

  it('DEU-03 (062): un historial nuevo empieza sin nada marcado; muestra el rechazo del servidor', () => {
    component.toggle(entries[0]);
    component.accountId.set('acc-bisa');
    fixture.componentRef.setInput('entries', [...entries]);
    fixture.detectChanges();
    expect(component.chosen()).toEqual([]);
    expect(component.accountId()).toBe('');

    fixture.componentRef.setInput('errorMessage', 'Las deudas elegidas cambiaron; vuelva a abrir el detalle.');
    fixture.detectChanges();
    expect(el().querySelector('.alert-danger')!.textContent).toContain('Las deudas elegidas cambiaron');
  });

  it('sin deudas pendientes lo dice y no muestra el botón', () => {
    fixture.componentRef.setInput('entries', [entries[2], entries[6]]);
    fixture.detectChanges();
    expect(el().textContent).toContain('No tiene deudas pendientes.');
    expect(el().querySelector('button[type=submit]')).toBeNull();
  });

  describe('CAJ-03: Caja sin «(caja abierta)»', () => {
    const note = (): HTMLElement | null => el().querySelector('.cash-closed-note');
    const choose = (value: string): void => {
      const select: HTMLSelectElement = el().querySelector('#repayment-account')!;
      select.value = value;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };

    beforeEach(async () => {
      component.toggle(entries[0]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('la opción no dice «(caja abierta)»', () => {
      const options = Array.from<HTMLOptionElement>(el().querySelectorAll('#repayment-account option')).map(o => o.textContent!.trim());
      expect(options).toEqual(['Seleccione…', 'Caja', 'Bisa']);
    });

    it('con la caja cerrada muestra la nota al elegir Caja y no deja pagar', () => {
      cashSession.mockReturnValue(of(null));
      choose('acc-caja');
      expect(note()!.textContent).toContain('La caja está cerrada. Ábrala y vuelva a elegir Caja.');
      expect(saveButton().disabled).toBe(true);

      choose('acc-bisa');
      expect(saveButton().disabled).toBe(false);
    });

    it('al volver a elegir Caja consulta otra vez y, con la caja ya abierta, activa el botón', () => {
      cashSession.mockReturnValue(of(null));
      choose('acc-caja');
      expect(saveButton().disabled).toBe(true);

      choose('');
      cashSession.mockReturnValue(of({ id: 's1', expectedCash: 1000 }));
      choose('acc-caja');
      expect(note()).toBeNull();
      expect(saveButton().disabled).toBe(false);
    });

    it('si falla la consulta no bloquea', () => {
      cashSession.mockReturnValue(throwError(() => ({ status: 0 })));
      choose('acc-caja');
      expect(note()).toBeNull();
      expect(saveButton().disabled).toBe(false);
    });
  });
});
