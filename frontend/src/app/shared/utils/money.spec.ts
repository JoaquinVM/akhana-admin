import { formatBs } from './money';

describe('formatBs', () => {
  // The same cases as StoreFormatTest.money (backend): a message reads the same on both sides
  const cases: [string, number, string][] = [
    ['con miles y decimales', 1234.5, 'Bs 1.234,50'],
    ['cero', 0, 'Bs 0,00'],
    ['un decimal', 7.1, 'Bs 7,10'],
    ['medio centavo sube: 0,005', 0.005, 'Bs 0,01'],
    ['medio centavo por debajo de cero baja: -0,005', -0.005, 'Bs -0,01'],
    ['negativo', -50, 'Bs -50,00'],
    ['negativo con miles', -1234.5, 'Bs -1.234,50'],
    ['millones', 1234567.89, 'Bs 1.234.567,89'],
    ['millones al redondear: 999.999,995', 999999.995, 'Bs 1.000.000,00'],
    ['sin monto', null as unknown as number, 'Bs 0,00'],
  ];

  it.each(cases)('%s', (_name, amount, expected) => {
    expect(formatBs(amount)).toBe(expected);
  });
});
