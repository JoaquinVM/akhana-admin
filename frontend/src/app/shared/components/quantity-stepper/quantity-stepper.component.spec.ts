import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuantityStepperComponent } from './quantity-stepper.component';

describe('QuantityStepperComponent', () => {
  let fixture: ComponentFixture<QuantityStepperComponent>;
  let component: QuantityStepperComponent;
  let calls: unknown[];

  const buttons = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('.qty-btn'));
  const field = (): HTMLInputElement => fixture.nativeElement.querySelector('.qty-input');

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [QuantityStepperComponent] }).compileComponents();
    fixture = TestBed.createComponent(QuantityStepperComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('quantity', 2);
    calls = [];
    component.increment.subscribe(() => calls.push('+'));
    component.decrement.subscribe(() => calls.push('-'));
    component.quantityChange.subscribe(value => calls.push(value));
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('muestra la cantidad y avisa al padre al sumar, restar y escribir, sin cambiarla', async () => {
    expect(field().value).toBe('2');

    buttons()[0].click();
    buttons()[1].click();
    field().value = '24';
    field().dispatchEvent(new Event('input'));

    expect(calls).toEqual(['-', '+', 24]);
    expect(component.quantity()).toBe(2);
  });

  it('los botones no envían el formulario en el que están', () => {
    expect(buttons().map(button => button.type)).toEqual(['button', 'button']);
  });

  it('con el − deshabilitado no avisa, y el + sigue funcionando', () => {
    fixture.componentRef.setInput('decrementDisabled', true);
    fixture.detectChanges();

    buttons()[0].click();
    buttons()[1].click();

    expect(buttons()[0].disabled).toBe(true);
    expect(calls).toEqual(['+']);
  });

  it('por peso acepta 3 decimales; por unidad, enteros', () => {
    expect(field().step).toBe('1');
    expect(field().classList.contains('qty-input-weight')).toBe(false);

    fixture.componentRef.setInput('weight', true);
    fixture.detectChanges();

    expect(field().step).toBe('0.001');
    expect(field().classList.contains('qty-input-weight')).toBe(true);
  });

  describe('POS-14: al salir del campo', () => {
    const leaveWith = async (value: string): Promise<void> => {
      field().value = value;
      field().dispatchEvent(new Event('input'));
      field().dispatchEvent(new Event('blur'));
      await fixture.whenStable();
    };

    it('con restoreOnBlur, vacío o en 0 vuelve a la cantidad del padre', async () => {
      fixture.componentRef.setInput('restoreOnBlur', true);
      fixture.detectChanges();

      await leaveWith('');
      expect(field().value).toBe('2');
      await leaveWith('0');
      expect(field().value).toBe('2');
    });

    it('con restoreOnBlur, lo que el padre no tomó, como 0.0004, vuelve a su cantidad', async () => {
      fixture.componentRef.setInput('restoreOnBlur', true);
      fixture.detectChanges();

      await leaveWith('0.0004');
      expect(field().value).toBe('2');
    });

    it('con restoreOnBlur, la cantidad que el padre tomó se queda', async () => {
      fixture.componentRef.setInput('restoreOnBlur', true);
      fixture.componentRef.setInput('quantity', 5);
      fixture.detectChanges();
      await fixture.whenStable();
      await leaveWith('5');
      expect(field().value).toBe('5');
    });

    it('sin restoreOnBlur, como en pedidos, el campo queda como se escribió', async () => {
      await leaveWith('');
      expect(field().value).toBe('');
    });
  });
});
