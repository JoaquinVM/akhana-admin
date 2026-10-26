import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { vi } from 'vitest';
import { CheckoutModalComponent } from './checkout-modal.component';

describe('CheckoutModalComponent', () => {
  let component: CheckoutModalComponent;
  let fixture: ComponentFixture<CheckoutModalComponent>;

  function open(total: number): void {
    component.totalInput = total;
    component.isOpen = true;
    component.ngOnChanges({ isOpen: new SimpleChange(false, true, false) });
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutModalComponent);
    component = fixture.componentInstance;
    open(20);
  });

  it('debe procesar el método de pago EFECTIVO calculando el cambio', () => {
    component.setPaymentMethod('EFECTIVO');

    component.amountReceived.set(50);
    expect(component.changeGiven()).toBe(30);
    expect(component.isPaymentValid()).toBe(true);

    component.amountReceived.set(15);
    expect(component.isPaymentValid()).toBe(false); // insuficiente
  });

  it('debe procesar el método de pago QR fijando el monto y sin cambio', () => {
    component.setPaymentMethod('QR');

    expect(component.amountQr()).toBe(20);
    expect(component.changeGiven()).toBe(0);
    expect(component.isPaymentValid()).toBe(true);
  });

  it('debe procesar el método de pago MIXTO calculando el remanente QR y cambio en efectivo', () => {
    component.setPaymentMethod('MIXTO');

    component.amountCash.set(10);
    component.amountReceived.set(15); // entrega billete de 15 para pagar 10

    expect(component.amountQr()).toBe(10); // 20 - 10
    expect(component.changeGiven()).toBe(5); // 15 - 10
    expect(component.isPaymentValid()).toBe(true);
  });

  it('debe sincronizar monto recibido al cambiar la porción en efectivo con onCashPortionChange', () => {
    component.setPaymentMethod('MIXTO'); // por defecto 10 efe, 10 qr, 10 recibido

    expect(component.amountCash()).toBe(10);
    expect(component.amountReceived()).toBe(10);

    component.onCashPortionChange(15);
    expect(component.amountCash()).toBe(15);
    expect(component.amountQr()).toBe(5);
    expect(component.amountReceived()).toBe(15); // se sincronizó automáticamente

    component.addReceived(5);
    expect(component.amountReceived()).toBe(20);
    expect(component.changeGiven()).toBe(5);

    // Botón Exacto restablece monto recibido a la porción de efectivo
    component.setExactAmount();
    expect(component.amountReceived()).toBe(15);
    expect(component.changeGiven()).toBe(0);
  });

  it('debe actualizar el monto recibido con onAmountReceivedChange y validar cuando es menor al total en EFECTIVO', () => {
    component.setPaymentMethod('EFECTIVO');
    expect(component.amountReceived()).toBe(20);
    expect(component.isPaymentValid()).toBe(true);

    component.onAmountReceivedChange(15);
    expect(component.isPaymentValid()).toBe(false);

    component.onAmountReceivedChange(null);
    expect(component.amountReceived()).toBe(0);
    expect(component.isPaymentValid()).toBe(false);

    component.onAmountReceivedChange(25);
    expect(component.changeGiven()).toBe(5);
    expect(component.isPaymentValid()).toBe(true);
  });

  it('al reabrir conserva el método y reinicia los montos con el nuevo total', () => {
    component.setPaymentMethod('MIXTO');
    component.amountCash.set(3);

    open(30);

    expect(component.paymentMethod()).toBe('MIXTO');
    expect(component.amountCash()).toBe(15);
    expect(component.amountReceived()).toBe(15);
  });

  it('confirmar emite el pago y no emite si el pago no es válido o se está enviando', () => {
    const emitted = vi.fn();
    component.confirmed.subscribe(emitted);

    component.setPaymentMethod('MIXTO');
    component.onCashPortionChange(8);
    component.handleConfirm();
    expect(emitted).toHaveBeenCalledWith({ paymentMethod: 'MIXTO', amountCash: 8, amountQr: 12, amountReceived: 8 });

    emitted.mockClear();
    component.onAmountReceivedChange(5);
    component.handleConfirm();
    component.isSubmitting = true;
    component.onAmountReceivedChange(8);
    component.handleConfirm();
    expect(emitted).not.toHaveBeenCalled();
  });
});
