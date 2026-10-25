import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PayablesViewComponent } from './payables-view.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { Payables, Purchase } from '../../../../core/purchase/models/purchase.models';

const purchase = {
  id: 'pur-1', purchaseNumber: 'PED-000001', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-20',
  status: 'REGISTRADO', itemsTotal: 300, paidAmount: 100, balance: 200, paymentStatus: 'A_CUENTA'
} as Purchase;

const payables: Payables = {
  totalBalance: 250,
  suppliers: [{ supplierId: 'sup-1', supplierName: 'Granel', balance: 250, purchases: [purchase, { ...purchase, id: 'pur-2', purchaseNumber: 'PED-000002', balance: 50 }] }]
};

describe('PayablesViewComponent', () => {
  let fixture: ComponentFixture<PayablesViewComponent>;
  let component: PayablesViewComponent;
  let purchaseService: any;

  beforeEach(async () => {
    purchaseService = { getPayables: vi.fn().mockReturnValue(of(payables)) };
    await TestBed.configureTestingModule({
      imports: [PayablesViewComponent],
      providers: [{ provide: PurchaseService, useValue: purchaseService }]
    }).compileComponents();
    fixture = TestBed.createComponent(PayablesViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('COM-08: muestra el saldo por proveedor, por pedido y el total', () => {
    const text = fixture.nativeElement.textContent;
    expect(purchaseService.getPayables).toHaveBeenCalledWith(null);
    expect(text).toContain('Granel');
    expect(text).toContain('Saldo Bs 250.00');
    expect(text).toContain('PED-000002');
    expect(text).toContain('Total por pagar');
  });

  it('COM-08: filtra por proveedor y recarga al cambiar la clave', () => {
    fixture.componentRef.setInput('supplierId', 'sup-1');
    fixture.detectChanges();
    expect(purchaseService.getPayables).toHaveBeenLastCalledWith('sup-1');
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    expect(purchaseService.getPayables).toHaveBeenCalledTimes(3);
  });

  it('emite el pedido para abrir su detalle', () => {
    const open = vi.fn();
    component.open.subscribe(open);
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(purchase);
  });
});
