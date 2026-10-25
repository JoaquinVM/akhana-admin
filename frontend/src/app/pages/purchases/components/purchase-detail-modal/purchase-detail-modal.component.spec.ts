import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PurchaseDetailModalComponent } from './purchase-detail-modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { Purchase } from '../../../../core/purchase/models/purchase.models';

const basePurchase: Purchase = {
  id: 'pur-1', purchaseNumber: 'PED-000001', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-25',
  status: 'REGISTRADO', itemsTotal: 300, shippingCost: 25, totalAmount: 325, paidAmount: 0, balance: 300,
  paymentStatus: 'POR_PAGAR', consignment: false, comment: null, shippingAccountName: 'Bisa', createdBy: 'operator',
  createdAt: '2026-10-25T12:00:00Z',
  items: [{
    id: 'it-1', productId: 'p-1', productName: 'Té verde', productCode: 'TE-1', saleUnit: 'UNIT', quantity: 30,
    unitCost: 10, subtotal: 300, previousUnitCost: 9, costUpdated: true
  }],
  payments: []
};

describe('PurchaseDetailModalComponent', () => {
  let component: PurchaseDetailModalComponent;
  let fixture: ComponentFixture<PurchaseDetailModalComponent>;
  let purchaseService: any;

  beforeEach(async () => {
    purchaseService = {
      getPurchaseById: vi.fn().mockReturnValue(of(basePurchase)),
      registerPayment: vi.fn().mockReturnValue(of({ ...basePurchase, paidAmount: 100, balance: 200, paymentStatus: 'A_CUENTA' }))
    };
    await TestBed.configureTestingModule({
      imports: [PurchaseDetailModalComponent],
      providers: [{ provide: PurchaseService, useValue: purchaseService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PurchaseDetailModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('purchaseId', 'pur-1');
    fixture.detectChanges();
  });

  it('carga el detalle y propone el saldo como monto del pago', () => {
    expect(purchaseService.getPurchaseById).toHaveBeenCalledWith('pur-1');
    expect(component.purchase()?.purchaseNumber).toBe('PED-000001');
    expect(component.paymentAmount()).toBe(300);
    expect(component.canPay()).toBe(true);
    expect(component.costChanged(basePurchase.items[0])).toBe(true);
  });

  it('COM-05: exige cuenta antes de pagar', () => {
    component.registerPayment();
    expect(component.paymentError()).toBe('Elija la cuenta de origen del pago.');
    expect(purchaseService.registerPayment).not.toHaveBeenCalled();
  });

  it('COM-05: registra el pago y emite el pedido actualizado', () => {
    const paid = vi.fn();
    component.paid.subscribe(paid);
    component.paymentAccountId.set('acc-1');
    component.paymentAmount.set(100);
    component.registerPayment();

    expect(purchaseService.registerPayment).toHaveBeenCalledWith('pur-1', { accountId: 'acc-1', amount: 100, notes: null });
    expect(component.purchase()?.paymentStatus).toBe('A_CUENTA');
    expect(component.paymentAmount()).toBe(200);
    expect(paid).toHaveBeenCalled();
  });

  it('COM-05: muestra el error del servidor', () => {
    purchaseService.registerPayment.mockReturnValue(throwError(() => ({ error: { message: 'El pago supera el saldo pendiente del pedido (Bs 300.00).' } })));
    component.paymentAccountId.set('acc-1');
    component.paymentAmount.set(500);
    component.registerPayment();
    expect(component.paymentError()).toBe('El pago supera el saldo pendiente del pedido (Bs 300.00).');
  });

  it('COM-04: un pedido pagado no ofrece registrar pagos', () => {
    component.purchase.set({ ...basePurchase, paidAmount: 300, balance: 0, paymentStatus: 'PAGADO' });
    expect(component.canPay()).toBe(false);
  });
});
