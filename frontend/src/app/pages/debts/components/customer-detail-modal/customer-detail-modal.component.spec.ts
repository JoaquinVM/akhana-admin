import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CustomerDetailModalComponent } from './customer-detail-modal.component';
import { CustomerService } from '../../../../core/debt/customer.service';
import { CustomerHistory, CustomerHistoryEntry, DebtAccount } from '../../../../core/debt/models/debt.models';

const collection: CustomerHistoryEntry = {
  type: 'COLLECTION', id: 'col-1', date: '2026-10-23T14:00:00Z', amount: 500, saleId: null, saleNumber: null,
  saleTotal: null, accountId: 'acc-caja', accountName: 'Caja', comment: null, voided: false, voidedBy: null,
  voidedAt: null, voidReason: null, balanceAfter: 79
};
const history: CustomerHistory = {
  customer: {
    id: 'cus-1', name: 'Tía Neida', phone: null, description: null, status: 'ACTIVO', balance: 79, createdBy: 'operator',
    createdAt: '2026-10-22T12:00:00Z', updatedBy: null, updatedAt: null, deletedBy: null, deletedAt: null
  },
  entries: [
    { ...collection, type: 'SALE', id: 'sale-1', amount: 579, saleId: 'sale-1', saleNumber: 'VTA-000100', saleTotal: 579,
      accountId: null, accountName: null, balanceAfter: 579 },
    collection
  ]
};
const accounts: DebtAccount[] = [
  { id: 'acc-caja', name: 'Caja', color: '#000', cashAccount: true, qrAccount: false },
  { id: 'acc-bisa', name: 'Bisa', color: '#000', cashAccount: false, qrAccount: true },
  { id: 'acc-efe', name: 'Efectivo', color: '#000', cashAccount: false, qrAccount: false }
];

describe('CustomerDetailModalComponent', () => {
  let component: CustomerDetailModalComponent;
  let fixture: ComponentFixture<CustomerDetailModalComponent>;
  let customerService: any;

  beforeEach(async () => {
    customerService = {
      getHistory: vi.fn().mockReturnValue(of(history)),
      registerCollection: vi.fn().mockReturnValue(of({ ...history, customer: { ...history.customer, balance: 0 } })),
      voidCollection: vi.fn().mockReturnValue(of(history))
    };
    await TestBed.configureTestingModule({
      imports: [CustomerDetailModalComponent],
      providers: [{ provide: CustomerService, useValue: customerService }]
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerDetailModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('accounts', accounts);
    fixture.componentRef.setInput('customerId', 'cus-1');
    fixture.detectChanges();
  });

  it('DEU-07: carga el historial y propone el saldo como monto a cobrar', () => {
    expect(customerService.getHistory).toHaveBeenCalledWith('cus-1');
    expect(component.balance()).toBe(79);
    expect(component.collectionAmount()).toBe(79);
    expect(component.canCollect()).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Venta al costo VTA-000100');
    expect(text).toContain('Cobro a Caja');
  });

  it('DEU-06: solo ofrece Caja y la cuenta QR para cobrar', () => {
    expect(component.collectionAccounts().map(a => a.id)).toEqual(['acc-caja', 'acc-bisa']);
  });

  it('DEU-06: exige cuenta y registra el cobro', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);

    component.registerCollection();
    expect(component.collectionError()).toBe('Elija la cuenta a la que entra el cobro.');

    component.collectionAccountId.set('acc-bisa');
    component.collectionComment.set('  QR ');
    component.registerCollection();
    expect(customerService.registerCollection).toHaveBeenCalledWith('cus-1', { accountId: 'acc-bisa', amount: 79, comment: 'QR' });
    expect(component.balance()).toBe(0);
    expect(changed).toHaveBeenCalled();
  });

  it('DEU-06: muestra el rechazo del servidor (cobro mayor al saldo)', () => {
    customerService.registerCollection.mockReturnValue(throwError(() => ({ error: { message: 'El cobro supera el saldo por cobrar de Tía Neida (Bs 79.00).' } })));
    component.collectionAccountId.set('acc-caja');
    component.collectionAmount.set(100);
    component.registerCollection();
    expect(component.collectionError()).toBe('El cobro supera el saldo por cobrar de Tía Neida (Bs 79.00).');
  });

  it('DEU-06: anula un cobro con motivo y avisa el cambio', () => {
    const changed = vi.fn();
    component.changed.subscribe(changed);
    component.openVoid(collection);
    expect(component.voidMessage()).toContain('el saldo por cobrar vuelve a subir');

    component.confirmVoid('Error de monto');
    expect(customerService.voidCollection).toHaveBeenCalledWith('cus-1', 'col-1', { voidReason: 'Error de monto' });
    expect(component.voidTarget()).toBeNull();
    expect(changed).toHaveBeenCalled();
  });

  it('P-49: la venta al costo pagada en el momento se ve como pagada', () => {
    customerService.getHistory.mockReturnValue(of({
      ...history,
      entries: [{ ...history.entries[0], id: 'sale-2', amount: 0, saleId: 'sale-2', saleNumber: 'VTA-000135', saleTotal: 25,
        balanceAfter: 0 }]
    }));
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Venta al costo VTA-000135');
    expect(text).toContain('Total Bs 25.00 · pagada');
    expect(text).not.toContain('a crédito');
  });

  it('DEU-07: «Ver venta» emite el id de la venta', () => {
    const opened = vi.fn();
    component.openSale.subscribe(opened);
    const button = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find(b => b.textContent?.includes('Ver venta')) as HTMLButtonElement;
    button.click();
    expect(opened).toHaveBeenCalledWith('sale-1');
  });
});
