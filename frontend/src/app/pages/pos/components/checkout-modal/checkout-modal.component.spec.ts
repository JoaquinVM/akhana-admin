import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CheckoutModalComponent } from './checkout-modal.component';
import { SaleDetailRequest } from '../../../../core/sale/models/sale.models';
import { SimpleChange } from '@angular/core';

describe('CheckoutModalComponent', () => {
  let component: CheckoutModalComponent;
  let fixture: ComponentFixture<CheckoutModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutModalComponent);
    component = fixture.componentInstance;
    component.isOpen = true;
    component.totalAmount = 85.0;
    component.items = [{ productId: 'p1', quantity: 2 }];
    component.globalDiscountAmount = 0;

    component.ngOnChanges({
      isOpen: new SimpleChange(false, true, true),
      totalAmount: new SimpleChange(0, 85.0, true)
    });
    fixture.detectChanges();
  });

  it('debe crearse e inicializarse en modo EFECTIVO con monto exacto recibido', () => {
    expect(component).toBeTruthy();
    expect(component.paymentMethod()).toBe('EFECTIVO');
    expect(component.amountReceived()).toBe(85.0);
    expect(component.changeGiven()).toBe(0);
    expect(component.isAmountValid()).toBe(true);
  });

  it('debe calcular cambio correctamente en EFECTIVO cuando el cliente paga con más dinero', () => {
    component.amountReceived.set(100.0);
    expect(component.changeGiven()).toBe(15.0);
    expect(component.isAmountValid()).toBe(true);
  });

  it('debe invalidar confirmación en EFECTIVO si el monto recibido es insuficiente', () => {
    component.amountReceived.set(70.0);
    expect(component.isAmountValid()).toBe(false);
  });

  it('debe configurar correctamente el modo QR sin cambio y con monto total fijo', () => {
    component.setMethod('QR');
    expect(component.paymentMethod()).toBe('QR');
    expect(component.amountQr()).toBe(85.0);
    expect(component.changeGiven()).toBe(0);
    expect(component.isAmountValid()).toBe(true);
  });

  it('debe calcular remanente QR y cambio sobre efectivo en modo MIXTO', () => {
    component.setMethod('MIXTO');
    component.totalAmount = 100.0;
    component.amountCash.set(40.0);
    component.amountReceived.set(50.0); // Entrega billete de 50 para cubrir los 40

    expect(component.amountQr()).toBe(60.0); // 100 - 40
    expect(component.changeGiven()).toBe(10.0); // 50 - 40
    expect(component.isAmountValid()).toBe(true);
  });

  it('debe emitir confirmed con el payload de venta al invocar handleConfirm', () => {
    let emittedPayload: SaleDetailRequest | null = null;
    component.confirmed.subscribe((p) => (emittedPayload = p));

    component.amountReceived.set(100.0);
    component.handleConfirm();

    expect(emittedPayload).toBeTruthy();
    expect(emittedPayload!.paymentMethod).toBe('EFECTIVO');
    expect(emittedPayload!.amountCash).toBe(85.0);
    expect(emittedPayload!.amountReceived).toBe(100.0);
    expect(emittedPayload!.items.length).toBe(1);
  });

  it('debe emitir evento close al cancelar o cerrar el modal', () => {
    let closed = false;
    component.close.subscribe(() => (closed = true));

    component.handleClose();
    expect(closed).toBe(true);
  });
});
