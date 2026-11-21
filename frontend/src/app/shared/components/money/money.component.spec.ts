import { TestBed } from '@angular/core/testing';
import { MoneyComponent } from './money.component';

describe('MoneyComponent', () => {
  const render = (value: number) => {
    const fixture = TestBed.createComponent(MoneyComponent);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('escribe el monto igual que el pipe number, con el Bs y los centavos aparte', () => {
    const host = render(1240.5);
    expect(host.textContent).toBe('Bs 1,240.50');
    expect(host.querySelector('.money-currency')?.textContent).toBe('Bs');
    expect(host.querySelector('.money-cents')?.textContent).toBe('.50');
  });

  it('también con montos negativos y cero', () => {
    expect(render(-20).textContent).toBe('Bs -20.00');
    expect(render(0).textContent).toBe('Bs 0.00');
  });
});
