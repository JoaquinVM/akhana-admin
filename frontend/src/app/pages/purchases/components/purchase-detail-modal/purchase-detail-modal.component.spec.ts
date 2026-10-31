import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PurchaseDetailModalComponent } from './purchase-detail-modal.component';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { Purchase, PurchasePayment } from '../../../../core/purchase/models/purchase.models';

const basePurchase: Purchase = {
  id: 'pur-1', purchaseNumber: 'PED-000001', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-25',
  status: 'REGISTRADO', itemsTotal: 300, shippingCost: 25, totalAmount: 325, paidAmount: 0, balance: 300,
  paymentStatus: 'POR_PAGAR', consignment: false, comment: null, shippingAccountId: null, updatedBy: null, updatedAt: null, revisions: [], shippingAccountName: 'Bisa',
  deliveryType: 'EN_TIENDA', deliveryStatus: 'PENDIENTE', deliveryDate: null, createdBy: 'operator',
  createdAt: '2026-10-25T12:00:00Z', voidedBy: null, voidedAt: null, voidReason: null,
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
      registerPayment: vi.fn().mockReturnValue(of({ ...basePurchase, paidAmount: 100, balance: 200, paymentStatus: 'A_CUENTA' })),
      updateDelivery: vi.fn().mockReturnValue(of({ ...basePurchase, deliveryType: 'TERMINAL', deliveryStatus: 'ENTREGADO', deliveryDate: '2026-10-25' })),
      voidPurchase: vi.fn().mockReturnValue(of({ ...basePurchase, status: 'ANULADO', voidReason: 'Error de carga' })),
      voidPayment: vi.fn().mockReturnValue(of(basePurchase))
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
    expect(component.paymentError()).toBe('Elija la cuenta o la persona que pagó.');
    expect(purchaseService.registerPayment).not.toHaveBeenCalled();
  });

  it('COM-05: registra el pago y emite el pedido actualizado', () => {
    const paid = vi.fn();
    component.updated.subscribe(paid);
    component.paymentSource.set('account:acc-1');
    component.paymentAmount.set(100);
    component.registerPayment();

    expect(purchaseService.registerPayment).toHaveBeenCalledWith('pur-1', { accountId: 'acc-1', creditorId: null, amount: 100, notes: null });
    expect(component.purchase()?.paymentStatus).toBe('A_CUENTA');
    expect(component.paymentAmount()).toBe(200);
    expect(paid).toHaveBeenCalled();
  });

  it('DEU-02: registra un pago hecho por una persona, sin nota', () => {
    component.paymentSource.set('creditor:cre-1');
    component.paymentAmount.set(200);
    component.paymentNotes.set('no se envía');
    component.registerPayment();

    expect(purchaseService.registerPayment).toHaveBeenCalledWith('pur-1', { accountId: null, creditorId: 'cre-1', amount: 200, notes: null });
  });

  it('DEU-02: muestra el origen persona y explica su anulación', () => {
    const payment: PurchasePayment = {
      id: 'pay-1', accountId: null, accountName: null, cashAccount: false, creditorId: 'cre-1', creditorName: 'Mami',
      amount: 200, paymentDate: '2026-10-25T12:00:00Z', financialTransactionId: null, status: 'ACTIVO',
      createdBy: 'operator', createdAt: '2026-10-25T12:00:00Z', voidedBy: null, voidedAt: null, voidReason: null
    };
    expect(component.sourceName(payment)).toBe('Mami (persona)');
    component.openVoid(payment);
    expect(component.voidMessage()).toContain('hecho por Mami');
  });

  it('CAJ-11: un pago desde Caja cargado pasada la medianoche se marca con el día de su caja', () => {
    expect(component.dayMark('2026-10-31', '2026-11-01T04:30:00Z')).toBe('Día 31/10 · cargada el 01/11 00:30');
    expect(component.dayMark('2026-10-25', '2026-10-25T12:00:00Z')).toBeNull();
  });

  it('COM-05: muestra el error del servidor', () => {
    purchaseService.registerPayment.mockReturnValue(throwError(() => ({ error: { message: 'El pago supera el saldo pendiente del pedido (Bs 300.00).' } })));
    component.paymentSource.set('account:acc-1');
    component.paymentAmount.set(500);
    component.registerPayment();
    expect(component.paymentError()).toBe('El pago supera el saldo pendiente del pedido (Bs 300.00).');
  });

  it('COM-04: un pedido pagado no ofrece registrar pagos', () => {
    component.purchase.set({ ...basePurchase, paidAmount: 300, balance: 0, paymentStatus: 'PAGADO' });
    expect(component.canPay()).toBe(false);
  });

  it('COM-07: cambia la entrega y emite el pedido actualizado', () => {
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.startDeliveryEdit();
    expect(component.deliveryType()).toBe('EN_TIENDA');
    component.deliveryType.set('TERMINAL');
    component.deliveryStatus.set('ENTREGADO');
    component.saveDelivery();

    expect(purchaseService.updateDelivery).toHaveBeenCalledWith('pur-1',
      { deliveryType: 'TERMINAL', deliveryStatus: 'ENTREGADO', deliveryDate: null });
    expect(component.purchase()?.deliveryStatus).toBe('ENTREGADO');
    expect(component.isEditingDelivery()).toBe(false);
    expect(updated).toHaveBeenCalled();
  });

  it('COM-09: anula el pedido con motivo', () => {
    component.openVoid('PURCHASE');
    expect(component.voidTitle()).toBe('Anular pedido PED-000001');
    component.confirmVoid('Error de carga');

    expect(purchaseService.voidPurchase).toHaveBeenCalledWith('pur-1', { voidReason: 'Error de carga' });
    expect(component.purchase()?.status).toBe('ANULADO');
    expect(component.voidTarget()).toBeNull();
    expect(component.isActive()).toBe(false);
    expect(component.canPay()).toBe(false);
  });

  it('COM-09: anula un pago y muestra el error del servidor si falla', () => {
    const payment = { id: 'pay-1', amount: 100, accountName: 'Caja', status: 'ACTIVO' } as PurchasePayment;
    purchaseService.voidPayment.mockReturnValue(throwError(() => ({ error: { message: 'El pago se hizo en una caja cerrada; no se puede anular.' } })));
    component.openVoid(payment);
    expect(component.voidMessage()).toContain('Bs 100,00 desde Caja');
    component.confirmVoid('Duplicado');

    expect(purchaseService.voidPayment).toHaveBeenCalledWith('pur-1', 'pay-1', { voidReason: 'Duplicado' });
    expect(component.voidError()).toBe('El pago se hizo en una caja cerrada; no se puede anular.');
    expect(component.voidTarget()).toBe(payment);
  });
  it('COM-11 y COM-12: «Editar pedido» emite el pedido y el historial muestra los cambios', () => {
    purchaseService.getPurchaseById.mockReturnValue(of({ ...basePurchase, revisions: [{
      id: 'rev-1', reason: 'Faltó la miel', changes: 'Línea quitada: Miel · Total: Bs 320,00 → Bs 300,00',
      revisedBy: 'operator', revisedAt: '2026-10-26T15:00:00Z' }] }));
    fixture.componentRef.setInput('purchaseId', 'pur-2');
    fixture.detectChanges();
    const edited = vi.fn();
    component.edit.subscribe(edited);

    const text = document.body.textContent ?? '';
    expect(text).toContain('Historial de cambios');
    expect(text).toContain('Línea quitada: Miel');
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(b => b.textContent?.includes('Editar pedido')) as HTMLButtonElement;
    button.click();
    expect(edited).toHaveBeenCalledWith(expect.objectContaining({ id: 'pur-1' }));
  });
});
