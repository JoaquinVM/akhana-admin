import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CartSummaryComponent } from './cart-summary.component';

describe('CartSummaryComponent', () => {
  let fixture: ComponentFixture<CartSummaryComponent>;
  let component: CartSummaryComponent;

  const set = (values: Record<string, unknown>): void => {
    Object.entries(values).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
  };
  const text = (): string => fixture.nativeElement.textContent;
  const discountInput = (): HTMLInputElement | null => fixture.nativeElement.querySelector('#globalDiscount');
  const chargeButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('.btn-proceed-checkout');

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CartSummaryComponent] }).compileComponents();
    fixture = TestBed.createComponent(CartSummaryComponent);
    component = fixture.componentInstance;
  });

  it('muestra subtotales, descuentos y el total recibidos', () => {
    set({
      subtotalGross: 64, discountItemsTotal: 4, subtotalPostItems: 60, globalDiscount: 5,
      maxGlobalDiscount: 60, discountTotal: 9, totalAmount: 55, canCheckout: true
    });
    expect(text()).toContain('Descuentos por ítem:');
    expect(text()).toContain('- Bs 4.00');
    expect(text()).toContain('Total de descuentos:');
    expect(text()).toContain('- Bs 9.00');
    expect(text()).toContain('Total a cobrar');
    expect(chargeButton().textContent).toContain('Cobrar (Bs 55.00)');
    expect(discountInput()?.getAttribute('max')).toBe('60');
  });

  it('sin descuentos oculta sus líneas', () => {
    set({ subtotalGross: 10, subtotalPostItems: 10, totalAmount: 10, canCheckout: true });
    expect(text()).not.toContain('Descuentos por ítem:');
    expect(text()).not.toContain('Total de descuentos:');
  });

  it('al costo o con solo líneas libres no ofrece descuento general', () => {
    set({ atCost: true, totalAmount: 10 });
    expect(discountInput()).toBeNull();
    expect(text()).toContain('Total al costo');

    set({ atCost: false, onlyFreeLines: true });
    expect(discountInput()).toBeNull();
  });

  it('emite el descuento general tecleado', async () => {
    set({ subtotalGross: 20, subtotalPostItems: 20, maxGlobalDiscount: 20, totalAmount: 20, canCheckout: true });
    await fixture.whenStable();
    const discount = vi.fn();
    component.globalDiscountChange.subscribe(discount);
    const input = discountInput()!;
    input.value = '3';
    input.dispatchEvent(new Event('input'));
    expect(discount).toHaveBeenCalledWith(3);
  });

  it('el botón Cobrar emite checkout solo si se puede cobrar', () => {
    const checkout = vi.fn();
    component.checkout.subscribe(checkout);
    set({ canCheckout: false });
    expect(chargeButton().disabled).toBe(true);

    set({ canCheckout: true, totalAmount: 10 });
    chargeButton().click();
    expect(checkout).toHaveBeenCalledTimes(1);
  });
});
