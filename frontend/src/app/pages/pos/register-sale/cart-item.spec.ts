import { Product } from '../../../core/product/models/product.models';
import { CartItem, lineAmount, lineGross } from './cart-item';

// The same cases as SaleServiceTest (backend): the POS shows what the server books, HALF_UP to the cent
describe('lineAmount', () => {
  const cases: [string, number, number, number][] = [
    ['medio centavo exacto sube: 0,5 x 0,01 = 0,005', 0.5, 0.01, 0.01],
    ['medio centavo por gramo: 0,001 kg x 5,00 = 0,005', 0.001, 5, 0.01],
    ['medio centavo con 2 decimales: 0,1 kg x 0,05 = 0,005', 0.1, 0.05, 0.01],
    ['CAT-08: 0,375 kg x 19,95 = 7,48125 baja a 7,48', 0.375, 19.95, 7.48],
    ['POS-10: 1,5 kg x 6,67 = 10,005 sube a 10,01', 1.5, 6.67, 10.01],
    ['POS-10: 1,5 kg x 374,33 = 561,495 sube a 561,50', 1.5, 374.33, 561.5],
    ['POS-10: 0,030 kg x 374,33 = 11,2299 queda en 11,23', 0.03, 374.33, 11.23],
    ['precio entero: 3 x 4 = 12', 3, 4, 12],
    ['precio entero por peso: 0,030 kg x 400 = 12', 0.03, 400, 12],
    ['cantidad 0 vale 0', 0, 40.15, 0],
    ['100 g a 40,15 Bs/kg = 4,015 sube a 4,02', 0.1, 40.15, 4.02],
  ];

  it.each(cases)('%s', (_name, quantity, price, expected) => {
    expect(lineAmount(quantity, price)).toBe(expected);
  });
});

describe('lineGross', () => {
  const kilo = { id: 'p', name: 'Nueces', saleUnit: 'KILOGRAM' } as unknown as Product;
  const item = (quantity: number, unitPrice: number, lineTotal: number | null): CartItem => ({
    key: 'k', product: kilo, presentation: null, quantity, unitPrice, unitDiscount: 0, unitFinalPrice: unitPrice,
    subtotal: 0, lineTotal,
  });

  const cases: [string, CartItem, number][] = [
    ['sin total escrito es peso x precio', item(0.03, 374.33, null), 11.23],
    ['POS-10: un total escrito menor queda como descuento; el bruto es peso x precio', item(0.03, 374.33, 10), 11.23],
    ['POS-10: sobre 1 kg el bruto sigue siendo peso x precio', item(1.5, 374.33, 10), 561.5],
    ['POS-10: un total escrito igual no cambia nada', item(0.03, 400, 12), 12],
    ['POS-10: un total subido reemplaza peso x precio', item(0.03, 374.33, 12), 12],
    ['cantidad 0 sin total vale 0', item(0, 40.15, null), 0],
  ];

  it.each(cases)('%s', (_name, line, expected) => {
    expect(lineGross(line)).toBe(expected);
  });
});
