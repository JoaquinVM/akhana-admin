import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { SaleCartComponent } from './sale-cart.component';
import { CartLineComponent } from '../cart-line/cart-line.component';
import { CartItem } from '../../register-sale/cart-item';

describe('SaleCartComponent', () => {
  let fixture: ComponentFixture<SaleCartComponent>;
  let component: SaleCartComponent;

  const line: CartItem = {
    key: 'p1', product: { id: 'p1', code: 'TAB', name: 'Tab s/Pistacho', saleUnit: 'UNIT' } as any, presentation: null,
    quantity: 2, unitPrice: 32, unitDiscount: 0, unitFinalPrice: 32, subtotal: 64
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SaleCartComponent] }).compileComponents();
    fixture = TestBed.createComponent(SaleCartComponent);
    component = fixture.componentInstance;
  });

  it('sin líneas muestra el carrito vacío y no ofrece vaciar', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cart-empty-state')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.btn-clear-cart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cart-badge').textContent).toContain('0 ítems');
  });

  it('muestra una fila por línea y el conteo recibido', () => {
    fixture.componentRef.setInput('items', [line, { ...line, key: 'p2' }]);
    fixture.componentRef.setInput('cartCount', 4);
    fixture.detectChanges();
    expect(fixture.debugElement.queryAll(By.directive(CartLineComponent)).length).toBe(2);
    expect(fixture.nativeElement.querySelector('.cart-badge').textContent).toContain('4 ítems');
    expect(fixture.nativeElement.querySelector('.cart-empty-state')).toBeNull();
  });

  it('reenvía los cambios de cada línea con su clave', () => {
    fixture.componentRef.setInput('items', [line]);
    fixture.detectChanges();
    const events: unknown[] = [];
    component.increment.subscribe((k) => events.push(['+', k]));
    component.decrement.subscribe((k) => events.push(['-', k]));
    component.quantityChange.subscribe((c) => events.push(['qty', c]));
    component.discountChange.subscribe((c) => events.push(['disc', c]));
    component.remove.subscribe((k) => events.push(['x', k]));

    const row: CartLineComponent = fixture.debugElement.query(By.directive(CartLineComponent)).componentInstance;
    row.increment.emit();
    row.decrement.emit();
    row.quantityChange.emit(5);
    row.discountChange.emit(1.5);
    row.remove.emit();

    expect(events).toEqual([
      ['+', 'p1'],
      ['-', 'p1'],
      ['qty', { key: 'p1', value: 5 }],
      ['disc', { key: 'p1', value: 1.5 }],
      ['x', 'p1']
    ]);
  });

  it('emite clear al vaciar y el evento del interruptor de venta al costo', () => {
    fixture.componentRef.setInput('items', [line]);
    fixture.detectChanges();
    const clear = vi.fn();
    const toggle = vi.fn();
    component.clear.subscribe(clear);
    component.atCostToggle.subscribe(toggle);

    fixture.nativeElement.querySelector('.btn-clear-cart').click();
    fixture.nativeElement.querySelector('.at-cost-toggle input').click();

    expect(clear).toHaveBeenCalled();
    expect(toggle).toHaveBeenCalledWith(expect.any(Event));
  });

  it('marca el interruptor cuando la venta es al costo', () => {
    fixture.componentRef.setInput('atCost', true);
    fixture.detectChanges();
    const toggle: HTMLElement = fixture.nativeElement.querySelector('.at-cost-toggle');
    expect(toggle.classList.contains('active')).toBe(true);
    expect((toggle.querySelector('input') as HTMLInputElement).checked).toBe(true);
    expect(toggle.textContent).toContain('Se cobra el costo');
  });
});
