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
});
