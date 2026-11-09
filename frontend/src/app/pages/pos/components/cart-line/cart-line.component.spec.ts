import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { CartLineComponent } from './cart-line.component';
import { CartItem } from '../../register-sale/cart-item';

describe('CartLineComponent', () => {
  let fixture: ComponentFixture<CartLineComponent>;
  let component: CartLineComponent;

  const unitLine: CartItem = {
    key: 'p1', product: { id: 'p1', code: 'TAB', name: 'Tab s/Pistacho', saleUnit: 'UNIT' } as any, presentation: null,
    quantity: 2, unitPrice: 32, unitDiscount: 2, unitFinalPrice: 30, subtotal: 60
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CartLineComponent] }).compileComponents();
    fixture = TestBed.createComponent(CartLineComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('item', unitLine);
    fixture.detectChanges();
  });

  it('muestra código, nombre, precio con descuento y subtotal', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('TAB');
    expect(text).toContain('Tab s/Pistacho');
    expect(fixture.debugElement.query(By.css('.old-price')).nativeElement.textContent).toContain('32.00');
    expect(fixture.debugElement.query(By.css('.item-subtotal')).nativeElement.textContent).toContain('60.00');
  });

  it('avisa al carrito cada cambio: sumar, restar, cantidad, descuento y quitar', () => {
    const calls: string[] = [];
    component.increment.subscribe(() => calls.push('+'));
    component.decrement.subscribe(() => calls.push('-'));
    component.remove.subscribe(() => calls.push('x'));
    const discount = vi.fn();
    component.discountChange.subscribe(discount);

    const buttons = fixture.debugElement.queryAll(By.css('.qty-btn'));
    buttons[0].nativeElement.click();
    buttons[1].nativeElement.click();
    fixture.debugElement.query(By.css('.btn-remove-item')).nativeElement.click();
    expect(calls).toEqual(['-', '+', 'x']);
  });

  it('CAT-11 y POS-11: una presentación muestra su peso, una línea libre su dueña, y al costo no hay descuento', () => {
    fixture.componentRef.setInput('item', {
      ...unitLine, key: 'p2:pr', presentation: { id: 'pr', name: '1/2 libra', weightKg: 0.227 } as any,
      product: { ...unitLine.product, name: 'Pistacho', saleUnit: 'KILOGRAM' }
    });
    fixture.componentRef.setInput('atCost', true);
    fixture.detectChanges();
    expect(component.name()).toBe('Pistacho · 1/2 libra');
    expect(fixture.nativeElement.textContent).toContain('0.227 kg c/u');
    expect(fixture.debugElement.query(By.css('.item-discount-group'))).toBeNull();

    fixture.componentRef.setInput('item', {
      ...unitLine, freeName: 'Sagrada Madre',
      product: { ...unitLine.product, specialType: 'THIRD_PARTY', ownerCreditorName: 'Mami' }
    });
    fixture.componentRef.setInput('atCost', false);
    fixture.detectChanges();
    expect(component.name()).toBe('Sagrada Madre');
    expect(fixture.debugElement.query(By.css('.free-line-badge')).nativeElement.textContent).toContain('De Mami');
    expect(fixture.debugElement.query(By.css('.item-discount-group'))).toBeNull();
  });

  describe('POS-10: «Otro peso»', () => {
    const weightLine: CartItem = {
      key: 'ar', product: { id: 'ar', code: 'G002', name: 'Arándanos', saleUnit: 'KILOGRAM' } as any, presentation: null,
      quantity: 0.03, unitPrice: 374.33, unitDiscount: 0, unitFinalPrice: 374.33, subtotal: 11.23
    };
    const query = (css: string) => fixture.debugElement.query(By.css(css));
    const totalField = () => query('.line-total-input').nativeElement as HTMLInputElement;
    const quantityField = () => query('.qty-input').nativeElement as HTMLInputElement;

    beforeEach(async () => {
      fixture.componentRef.setInput('item', weightLine);
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('muestra el total en lugar de Desc/u, con el monto calculado', () => {
      expect(totalField().value).toBe('11.23');
      expect(fixture.nativeElement.textContent).not.toContain('Desc/u');
      expect(fixture.nativeElement.querySelector('.old-price')).toBeNull();
    });

    it('avisa kg o g al pulsar la unidad', () => {
      const grams = vi.fn();
      component.gramsChange.subscribe(grams);
      const [kg, g] = fixture.debugElement.queryAll(By.css('.weight-unit-switch button'));

      g.nativeElement.click();
      kg.nativeElement.click();

      expect(grams.mock.calls).toEqual([[true], [false]]);
    });

    it('en gramos muestra 30 y avisa los kilos que se escriben', async () => {
      fixture.componentRef.setInput('item', { ...weightLine, inGrams: true });
      fixture.detectChanges();
      await fixture.whenStable();
      const quantity = vi.fn();
      component.quantityChange.subscribe(quantity);

      expect(quantityField().value).toBe('30');
      quantityField().value = '250';
      quantityField().dispatchEvent(new Event('input'));

      expect(quantity).toHaveBeenCalledWith(0.25);
    });

    it('en kilos muestra y avisa los kilos, como siempre', async () => {
      const quantity = vi.fn();
      component.quantityChange.subscribe(quantity);

      expect(quantityField().value).toBe('0.03');
      quantityField().value = '1.5';
      quantityField().dispatchEvent(new Event('input'));

      expect(quantity).toHaveBeenCalledWith(1.5);
    });

    it('avisa el total escrito y muestra tachado el calculado', () => {
      const total = vi.fn();
      component.totalChange.subscribe(total);

      totalField().value = '10';
      totalField().dispatchEvent(new Event('change'));
      expect(total).toHaveBeenCalledWith(10);

      fixture.componentRef.setInput('item', { ...weightLine, lineTotal: 10, subtotal: 10 });
      fixture.detectChanges();
      expect(totalField().value).toBe('10.00');
      expect(query('.old-price').nativeElement.textContent).toContain('11.23');
      expect(query('.item-subtotal').nativeElement.textContent).toContain('10.00');
    });

    it('un total que no se acepta vuelve al último válido', async () => {
      totalField().value = '';
      totalField().dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(totalField().value).toBe('11.23');
    });

    it('al costo no hay campo de total', () => {
      fixture.componentRef.setInput('atCost', true);
      fixture.detectChanges();

      expect(query('.line-total-input')).toBeNull();
      expect(query('.item-discount-group')).toBeNull();
    });

    it('una línea por unidad o por presentación conserva Desc/u y no tiene total', () => {
      fixture.componentRef.setInput('item', unitLine);
      fixture.detectChanges();
      expect(query('.line-total-input')).toBeNull();
      expect(query('.weight-unit-switch')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Desc/u');

      fixture.componentRef.setInput('item', { ...weightLine, presentation: { id: 'pr', name: '100 g', weightKg: 0.1 } as any, quantity: 1 });
      fixture.detectChanges();
      expect(query('.line-total-input')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Desc/u');
    });
  });
});
