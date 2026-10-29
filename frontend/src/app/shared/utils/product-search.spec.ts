import { productForEnter, stopImplicitSubmit } from './product-search';

describe('productForEnter', () => {
  const a = { code: 'TAB-100', name: 'Tab 100' };
  const b = { code: 'TAB-50', name: 'Tab 50' };

  it('elige el código exacto, o el único resultado; si no está claro, ninguno', () => {
    expect(productForEnter('tab-50', [a, b], [a, b])).toBe(b);
    expect(productForEnter('tab', [a, b], [a, b])).toBeNull();
    expect(productForEnter('100', [a, b], [a])).toBe(a);
    expect(productForEnter('  ', [a, b], [a])).toBeNull();
  });
});

describe('stopImplicitSubmit', () => {
  it('Enter en un campo no envía el formulario; en un textarea o un botón sí sigue su curso', () => {
    const input = document.createElement('input');
    const inInput = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    Object.defineProperty(inInput, 'target', { value: input });
    expect(stopImplicitSubmit(inInput)).toBe(true);
    expect(inInput.defaultPrevented).toBe(true);

    const area = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    Object.defineProperty(area, 'target', { value: document.createElement('textarea') });
    expect(stopImplicitSubmit(area)).toBe(false);
    expect(area.defaultPrevented).toBe(false);
  });
});
