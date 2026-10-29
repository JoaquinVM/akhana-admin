import { formatBs } from './money';

describe('formatBs', () => {
  it('formatea como el servidor: Bs 1.234,50', () => {
    expect(formatBs(1234.5)).toBe('Bs 1.234,50');
    expect(formatBs(0)).toBe('Bs 0,00');
    expect(formatBs(7.1)).toBe('Bs 7,10');
  });
});
