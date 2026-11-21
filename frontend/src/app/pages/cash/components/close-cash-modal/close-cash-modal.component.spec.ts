import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CloseCashModalComponent } from './close-cash-modal.component';

describe('CloseCashModalComponent', () => {
  let component: CloseCashModalComponent;
  let fixture: ComponentFixture<CloseCashModalComponent>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [CloseCashModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CloseCashModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('expectedCash', 100);
    fixture.detectChanges();
  });

  it('debe inicializarse con el formulario y lista de denominaciones', () => {
    expect(component).toBeTruthy();
    expect(component.denominations.length).toBe(11);
    expect(component.form.get('closingAmount')?.value).toBeNull();
    expect(component.hasAmount()).toBe(false);
  });

  // Closes, going through the review step when the difference asks for it
  const confirmClose = () => {
    component.onSubmit();
    if (component.step() === 'review') {
      component.onSubmit();
    }
  };

  const submitButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[type="submit"]');

  it('CAJ-04: sin contar nada el monto está vacío, el botón desactivado y no se cierra', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Falta el monto contado');
    expect(fixture.nativeElement.textContent).not.toContain('Faltante');
    component.onSubmit();
    component.onSubmit();
    expect(emitted).toBeNull();

    component.setMode('DIRECTO');
    fixture.detectChanges();
    expect(component.form.get('closingAmount')?.value).toBeNull();
    expect(submitButton().disabled).toBe(true);
    component.onSubmit();
    expect(emitted).toBeNull();
  });

  it('CAJ-04: al contar un billete hay monto, y al borrar todo vuelve a quedar vacío', () => {
    setCut(50, 2);
    fixture.detectChanges();
    expect(component.form.get('closingAmount')?.value).toBe(100);
    expect(submitButton().disabled).toBe(false);

    component.clearAllCuts();
    fixture.detectChanges();
    expect(component.form.get('closingAmount')?.value).toBeNull();
    expect(submitButton().disabled).toBe(true);
  });

  it('CAJ-04: un 0 escrito a mano en el conteo directo sí es un monto', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    fixture.componentRef.setInput('expectedCash', 0);
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 0 });

    component.onSubmit();
    expect(emitted.closingAmount).toBe(0);
  });

  it('CAJ-05: una diferencia de hasta Bs 20 cierra sin el paso de revisión', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 80 });

    component.onSubmit();
    expect(component.step()).toBe('count');
    expect(emitted.closingAmount).toBe(80);
  });

  it('CAJ-05: una diferencia de más de Bs 20 pasa por «Revisar diferencia» con el ajuste que se registrará', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    fixture.componentRef.setInput('sessionNumber', 7);
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 79.5 });

    component.onSubmit();
    fixture.detectChanges();
    expect(emitted).toBeNull();
    expect(component.step()).toBe('review');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Revisar diferencia');
    expect(text).toContain('«Faltante al cierre de caja #7»');
    expect(text).toContain('en la cuenta Caja');
    expect(submitButton().textContent).toContain('Cerrar con faltante');

    component.backToCount();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Revisar diferencia');

    component.onSubmit();
    component.onSubmit();
    expect(emitted.closingAmount).toBe(79.5);
  });

  it('CAJ-05: el sobrante grande se revisa como sobrante', () => {
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 150 });
    component.onSubmit();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('«Sobrante al cierre de caja»');
    expect(submitButton().textContent).toContain('Cerrar con sobrante');
  });

  it('CAJ-05: al volver a abrir el modal empieza en el conteo, no en la revisión', () => {
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 10 });
    component.onSubmit();
    expect(component.step()).toBe('review');

    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
    expect(component.step()).toBe('count');
  });

  it('debe separar correctamente billetes (5) y monedas (6) para la vista en 2 columnas', () => {
    expect(component.billCuts().length).toBe(5);
    expect(component.coinCuts().length).toBe(6);
    expect(component.activeMode()).toBe('ARQUEO');
  });

  it('cada casilla de billetes y monedas dice su corte y si es caja o reserva', () => {
    const labels = Array.from(fixture.nativeElement.querySelectorAll('.cut-input:not(.reserve-input)'))
      .map((input: any) => input.getAttribute('aria-label'));
    expect(labels).toContain('Billetes de Bs 200 en caja');
    expect(labels).toContain('Billetes de Bs 200 en reserva');
    expect(labels.some((label: string) => label.startsWith('Monedas de Bs') && label.endsWith('en reserva'))).toBe(true);
    expect(labels.every((label: string | null) => !!label)).toBe(true);
  });

  it('debe calcular correctamente subtotales de cortes y total acumulado', () => {
    // Simular 10 en caja y 40 en reserva para Bs 10 -> (10 + 40) * 10 = 500
    component.cutsData.update((rows) =>
      rows.map((r) =>
        r.denomination === 10
          ? { ...r, cashQuantity: 10, reserveQuantity: 40 }
          : r
      )
    );

    expect(component.totalCuts()).toBe(500);

    // Aplicar total al monto de cierre
    component.applyCutsToClosingAmount();
    expect(component.form.get('closingAmount')?.value).toBe(500);
    expect(component.difference()).toBe(400); // 500 - 100 = +400 (sobrante)
  });

  it('debe emitir confirmed con los datos ingresados al hacer submit', () => {
    let emittedResult: any = null;
    component.confirmed.subscribe((res) => {
      emittedResult = res;
    });

    component.form.patchValue({
      closingAmount: 150,
      closingComment: 'Cierre correcto'
    });

    confirmClose();

    expect(emittedResult).not.toBeNull();
    expect(emittedResult.closingAmount).toBe(150);
    expect(emittedResult.closingComment).toBe('Cierre correcto');
  });

  it('CAJ-04: con conteo directo no se envían los cortes cargados antes', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    component.cutsData.update((rows) => rows.map((r) => (r.denomination === 100 ? { ...r, cashQuantity: 1 } : r)));
    component.applyCutsToClosingAmount();

    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 3600 });
    confirmClose();

    expect(emitted.closingAmount).toBe(3600);
    expect(emitted.cuts).toBeUndefined();
  });

  it('CAJ-04: en arqueo se envían los cortes con su total como monto', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    component.cutsData.update((rows) => rows.map((r) => (r.denomination === 0.5 ? { ...r, cashQuantity: 3, reserveQuantity: 1 } : r)));
    component.applyCutsToClosingAmount();
    confirmClose();

    expect(emitted.closingAmount).toBe(2);
    expect(emitted.cuts).toEqual([{ denomination: 0.5, cashQuantity: 3, reserveQuantity: 1, subtotal: 2 }]);
  });

  it('CAJ-05: la diferencia se redondea al centavo y el faltante se muestra sin doble signo', () => {
    fixture.componentRef.setInput('expectedCash', 0.3);
    component.form.patchValue({ closingAmount: 0.1 + 0.2 });
    expect(component.difference()).toBe(0);

    component.form.patchValue({ closingAmount: 0.2 });
    fixture.detectChanges();
    expect(component.difference()).toBe(-0.1);
    expect(fixture.nativeElement.textContent).toContain('Faltante: Bs 0.10');
  });

  const setCut = (denomination: number, cashQuantity: number, reserveQuantity = 0) => {
    component.updateCutQuantity(denomination, 'cashQuantity', { target: { value: String(cashQuantity) } } as any);
    component.updateCutQuantity(denomination, 'reserveQuantity', { target: { value: String(reserveQuantity) } } as any);
  };

  const reopen = (sessionId: string | null) => {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('sessionId', sessionId);
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  };

  it('CAJ-04: las reservas de billetes y monedas se suman al conteo: 1.000 + 150 + 200 = 1.350', () => {
    setCut(200, 4, 1);
    component.form.patchValue({ reserveBills: 150, reserveCoins: 200 });

    expect(component.subtotalBills()).toBe(1150);
    expect(component.subtotalCoins()).toBe(200);
    expect(component.totalCuts()).toBe(1350);
    expect(component.form.get('closingAmount')?.value).toBe(1350);
    expect(component.difference()).toBe(1250);
  });

  it('CAJ-04: lo escrito en los campos de reserva llega al total', () => {
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#reserveCoins')!;
    input.value = '0.50';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(component.totalCuts()).toBe(0.5);
    expect(fixture.nativeElement.textContent).toContain('Reserva billetes (Bs)');
    expect(fixture.nativeElement.textContent).toContain('Reserva monedas (Bs)');
  });

  it('CAJ-04: en arqueo se envían las reservas junto a los cortes; una reserva en 0 no se envía', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    setCut(200, 5);
    component.form.patchValue({ reserveBills: 150 });
    confirmClose();

    expect(emitted.closingAmount).toBe(1150);
    expect(emitted.cuts).toEqual([{ denomination: 200, cashQuantity: 5, reserveQuantity: 0, subtotal: 1000 }]);
    expect(emitted.reserveBills).toBe(150);
    expect(emitted.reserveCoins).toBeUndefined();
  });

  it('CAJ-04: con conteo directo no se envían las reservas escritas antes', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    component.form.patchValue({ reserveBills: 150, reserveCoins: 200 });

    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 500 });
    confirmClose();

    expect(emitted.closingAmount).toBe(500);
    expect(emitted.cuts).toBeUndefined();
    expect(emitted.reserveBills).toBeUndefined();
    expect(emitted.reserveCoins).toBeUndefined();
  });

  it('CAJ-04: una reserva negativa no se suma y no deja cerrar', () => {
    let emitted: any = null;
    component.confirmed.subscribe((res) => (emitted = res));
    component.form.patchValue({ reserveBills: -10 });
    fixture.detectChanges();

    expect(component.totalCuts()).toBe(0);
    expect(component.form.invalid).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('La reserva no puede ser negativa.');
    component.onSubmit();
    expect(emitted).toBeNull();
  });

  it('CAJ-15: lo escrito sigue al cerrar y volver a abrir el modal', () => {
    reopen('cs-1');
    setCut(100, 2);
    component.form.patchValue({ reserveBills: 150, closingComment: 'Turno tarde' });
    component.onCancel();

    reopen('cs-1');

    expect(component.cutsData().find((r) => r.denomination === 100)?.cashQuantity).toBe(2);
    expect(component.reserveBills()).toBe(150);
    expect(component.form.get('reserveBills')?.value).toBe(150);
    expect(component.form.get('closingComment')?.value).toBe('Turno tarde');
    expect(component.form.get('closingAmount')?.value).toBe(350);
    expect(component.activeMode()).toBe('ARQUEO');
  });

  it('CAJ-15: el conteo directo y su monto también se guardan', () => {
    reopen('cs-1');
    component.setMode('DIRECTO');
    component.form.patchValue({ closingAmount: 780.5 });

    reopen('cs-1');

    expect(component.activeMode()).toBe('DIRECTO');
    expect(component.form.get('closingAmount')?.value).toBe(780.5);
  });

  it('CAJ-15: lo guardado es de esa caja: otra caja empieza vacía y lo viejo se descarta', () => {
    reopen('cs-1');
    setCut(100, 2);
    component.form.patchValue({ reserveCoins: 20 });

    reopen('cs-2');

    expect(component.totalCuts()).toBe(0);
    expect(component.reserveCoins()).toBe(0);
    expect(localStorage.getItem('akhana.closeCashDraft')).toBeNull();
  });

  it('CAJ-15: sin caja conocida no se guarda nada', () => {
    reopen(null);
    setCut(100, 2);
    expect(localStorage.getItem('akhana.closeCashDraft')).toBeNull();
  });
});
