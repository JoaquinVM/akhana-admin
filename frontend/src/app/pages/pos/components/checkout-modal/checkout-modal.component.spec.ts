import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CheckoutModalComponent } from './checkout-modal.component';

describe('CheckoutModalComponent', () => {
  let component: CheckoutModalComponent;
  let fixture: ComponentFixture<CheckoutModalComponent>;

  // Closes and opens again, so the opening effect runs with the new total
  function open(total: number): void {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('totalAmount', total);
    fixture.componentRef.setInput('isOpen', true);
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

  const billButtons = (): HTMLButtonElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('.btn-quick'))
      .filter((b: any) => b.textContent.trim().startsWith('Bs')) as HTMLButtonElement[];

  it('POS-04: el billete fija el monto recibido, no lo suma a lo que ya estaba', () => {
    open(38);
    component.setPaymentMethod('EFECTIVO');
    component.onAmountReceivedChange(40);

    component.setReceived(50);
    expect(component.amountReceived()).toBe(50);
    component.setReceived(100);
    expect(component.amountReceived()).toBe(100);
    expect(component.changeGiven()).toBe(62);
  });

  it('POS-04: solo se ofrecen los billetes que cubren el total, más «Exacto»', () => {
    open(38);
    component.setPaymentMethod('EFECTIVO');
    fixture.detectChanges();

    expect(billButtons().map((b) => b.textContent!.trim())).toEqual(['Bs 50', 'Bs 100', 'Bs 200']);
    expect(fixture.nativeElement.textContent).toContain('Exacto');

    billButtons()[1].click();
    fixture.detectChanges();
    expect(component.amountReceived()).toBe(100);
    billButtons()[0].click();
    expect(component.amountReceived()).toBe(50);
  });

  it('POS-04: un total de Bs 50 justo no ofrece Bs 50, porque eso es «Exacto»', () => {
    open(50);
    component.setPaymentMethod('EFECTIVO');
    fixture.detectChanges();
    expect(billButtons().map((b) => b.textContent!.trim())).toEqual(['Bs 100', 'Bs 200']);
  });

  it('POS-04: en el pago mixto los billetes cubren la parte en efectivo', () => {
    open(60);
    component.setPaymentMethod('MIXTO');
    component.onCashPortionChange(15);
    fixture.detectChanges();
    expect(billButtons().map((b) => b.textContent!.trim())).toEqual(['Bs 20', 'Bs 50', 'Bs 100', 'Bs 200']);
  });

  it('097: en efectivo se cobra el total redondeado a Bs 0.10 y se avisa el redondeo', () => {
    open(15.09);
    component.setPaymentMethod('EFECTIVO');
    fixture.detectChanges();

    expect(component.cashDue()).toBe(15.1);
    expect(component.amountReceived()).toBe(15.1);
    expect(component.isPaymentValid()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Bs 15.10 (redondeo +Bs 0.01)');

    component.onAmountReceivedChange(15.09);
    expect(component.isPaymentValid()).toBe(false);
    component.setReceived(20);
    expect(component.changeGiven()).toBe(4.9);

    let emitted: any = null;
    component.confirmed.subscribe((p) => (emitted = p));
    component.handleConfirm();
    expect(emitted.amountCash).toBe(15.1);
  });

  it('097: 15.04 se cobra 15.00 y sin centavos no hay aviso de redondeo', () => {
    open(15.04);
    component.setPaymentMethod('EFECTIVO');
    expect(component.cashDue()).toBe(15);
    expect(component.cashRounding()).toBe(-0.04);

    open(20);
    component.setPaymentMethod('EFECTIVO');
    fixture.detectChanges();
    expect(component.cashRounding()).toBe(0);
    expect(fixture.nativeElement.textContent).not.toContain('redondeo');
  });

  it('097: en el pago mixto el efectivo va en múltiplos de Bs 0.10', () => {
    open(15.09);
    component.setPaymentMethod('MIXTO');
    expect(component.amountCash()).toBe(7.6);
    expect(component.isPaymentValid()).toBe(true);

    component.onCashPortionChange(10.05);
    fixture.detectChanges();
    expect(component.isPaymentValid()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('múltiplos de Bs 0.10');
  });

  it('debe procesar el método de pago EFECTIVO calculando el cambio', () => {
    component.setPaymentMethod('EFECTIVO');

    component.amountReceived.set(50);
    expect(component.changeGiven()).toBe(30);
    expect(component.isPaymentValid()).toBe(true);

    component.amountReceived.set(15);
    expect(component.isPaymentValid()).toBe(false); // insuficiente
  });

  it('POS-04: el pago mixto exige efectivo y QR mayores a 0 y lo explica', () => {
    component.setPaymentMethod('MIXTO');
    expect(component.isPaymentValid()).toBe(true);
    expect(component.mixedNeedsBothParts()).toBe(false);

    component.onCashPortionChange(20); // all in cash
    fixture.detectChanges();
    expect(component.isPaymentValid()).toBe(false);
    expect(component.mixedNeedsBothParts()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('elige Efectivo o QR');

    component.onCashPortionChange(0); // all by QR
    expect(component.isPaymentValid()).toBe(false);
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

    component.setReceived(20);
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
    fixture.componentRef.setInput('isSubmitting', true);
    component.onAmountReceivedChange(8);
    component.handleConfirm();
    expect(emitted).not.toHaveBeenCalled();
  });

  describe('CON-02 / DEU-05: venta al costo con cliente y crédito', () => {
    const neida = { id: 'cus-1', name: 'Tía Neida', balance: 0 } as any;

    function openAtCost(total: number, customers: any[] = [neida]): void {
      fixture.componentRef.setInput('atCost', true);
      fixture.componentRef.setInput('customers', customers);
      open(total);
    }

    it('P-49: propone «Sin cliente» y empieza sin crédito', () => {
      openAtCost(120);
      expect(component.customerId()).toBeNull();
      expect(component.creditAmount()).toBe(0);
      expect(component.amountDue()).toBe(120);
    });

    it('P-49: al dar crédito propone el único cliente activo; con varios hay que elegir', () => {
      openAtCost(120);
      component.onCreditChange(80);
      expect(component.customerId()).toBe('cus-1');

      openAtCost(120, [neida, { id: 'cus-2', name: 'Otra', balance: 0 }]);
      component.onCreditChange(80);
      expect(component.customerId()).toBeNull();
      expect(component.isPaymentValid()).toBe(false);
    });

    it('P-49: pagada en el momento puede llevar cliente sin crédito', () => {
      openAtCost(25);
      component.setPaymentMethod('EFECTIVO');
      component.onCustomerChange('cus-1');
      expect(component.isPaymentValid()).toBe(true);

      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ customerId: 'cus-1', creditAmount: 0, amountCash: 25 }));
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
      fixture.componentRef.setInput('atCost', false);

      expect(component.amountDue()).toBe(50);
      const emitted = vi.fn();
      component.confirmed.subscribe(emitted);
      component.setPaymentMethod('EFECTIVO');
      component.handleConfirm();
      expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ customerId: null, creditAmount: 0, amountCash: 50, comment: '' }));
    });
  });

  it('reinicia los montos solo al abrir, no cuando cambia el total con el modal abierto', () => {
    component.setPaymentMethod('EFECTIVO');
    component.amountReceived.set(50);

    fixture.componentRef.setInput('totalAmount', 30);
    fixture.detectChanges();
    expect(component.amountReceived()).toBe(50);

    open(30);
    expect(component.amountReceived()).toBe(30);
  });
});
