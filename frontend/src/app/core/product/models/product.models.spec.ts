import { describe, expect, it } from 'vitest';
import { ProductPresentation, presentationPrice } from './product.models';

describe('presentationPrice (CAT-11)', () => {
  // The server computes both: 100 g at Bs 40,15/kg costs Bs 4,02 (HALF_UP), not the 4,01 a float product gives
  const presentation: ProductPresentation = {
    id: 'p-1', name: '100 g', weightKg: 0.1, fixedPrice: null, price: 6.5, cost: 4.02, active: true
  };

  it('al costo usa el costo que calculó el servidor', () => {
    expect(presentationPrice(presentation, true)).toBe(4.02);
  });

  it('a precio normal usa el precio que calculó el servidor', () => {
    expect(presentationPrice(presentation, false)).toBe(6.5);
  });
});
