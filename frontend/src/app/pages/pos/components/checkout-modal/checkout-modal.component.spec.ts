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
    expect(emitted).toHaveBeenCalledWith({
      paymentMethod: 'MIXTO', amountCash: 8, amountQr: 12, amountReceived: 8, customerId: null, creditAmount: 0, comment: ''
    });

    emitted.mockClear();
    component.onAmountReceivedChange(5);
    component.handleConfirm();
    component.isSubmitting = true;
    component.onAmountReceivedChange(8);
    component.handleConfirm();
    expect(emitted).not.toHaveBeenCalled();
  });

  describe('CON-02 / DEU-05: venta al costo con cliente y crédito', () => {
    const neida = { id: 'cus-1', name: 'Tía Neida', balance: 0 } as any;

    function openAtCost(total: number, customers: any[] = [neida]): void {
      component.atCostInput = true;
      component.customers = customers;
      open(total);
    }

    it('propone el único cliente activo y empieza sin crédito', () => {
      openAtCost(120);
      expect(component.customerId()).toBe('cus-1');
      expect(component.creditAmount()).toBe(0);
      expect(component.amountDue()).toBe(120);
    });

    it('parte por QR y parte a crédito: el pago cubre solo lo que se paga ahora', () => {
      openAtCost(120);
      component.setPaymentMethod('QR');
      component.onCreditChange(80);

      expect(component.amountDue()).toBe(40);
      expect(component.amountQr()).toBe(40);
      expect(component.isPaymentValid()).toBe(true);

      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith({
        paymentMethod: 'QR', amountCash: 0, amountQr: 40, amountReceived: 0, customerId: 'cus-1', creditAmount: 80, comment: ''
      });
    });

    it('toda a crédito se envía con método CREDITO y sin montos', () => {
      openAtCost(579);
      component.setFullCredit();
      expect(component.isFullCredit()).toBe(true);

      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith({
        paymentMethod: 'CREDITO', amountCash: 0, amountQr: 0, amountReceived: 0, customerId: 'cus-1', creditAmount: 579, comment: ''
      });
    });

    it('el crédito exige cliente y se limita al total', () => {
      openAtCost(50, []);
      component.onCreditChange(70);
      expect(component.creditAmount()).toBe(50);
      expect(component.isPaymentValid()).toBe(false);

      component.onCustomerChange('cus-1');
      expect(component.isPaymentValid()).toBe(true);
    });

    it('en efectivo con crédito, el monto recibido y el cambio son sobre lo que se paga ahora', () => {
      openAtCost(50);
      component.setPaymentMethod('EFECTIVO');
      component.onCreditChange(30);
      expect(component.amountReceived()).toBe(20);

      component.onAmountReceivedChange(50);
      expect(component.changeGiven()).toBe(30);
    });

    it('P-39: envía el comentario de la venta al costo sin espacios', () => {
      openAtCost(25, []);
      component.setPaymentMethod('EFECTIVO');
      component.comment.set('  Mami precio costo ');

      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ customerId: null, comment: 'Mami precio costo' }));
    });

    it('una venta normal ignora cliente, crédito y comentario', () => {
      openAtCost(50);
      component.onCreditChange(20);
      component.comment.set('No va');
      component.atCostInput = false;

      expect(component.amountDue()).toBe(50);
      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.setPaymentMethod('EFECTIVO');
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ customerId: null, creditAmount: 0, amountCash: 50, comment: '' }));
    });
  });
});
