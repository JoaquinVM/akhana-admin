import { plural } from './plural.pipe';

describe('plural (H20)', () => {
  it('usa el singular solo con 1', () => {
    expect(plural(1, 'venta', 'ventas')).toBe('1 venta');
    expect(plural(0, 'venta', 'ventas')).toBe('0 ventas');
    expect(plural(3, 'sesión encontrada', 'sesiones encontradas')).toBe('3 sesiones encontradas');
    expect(plural(null, 'ítem', 'ítems')).toBe('0 ítems');
  });
});
