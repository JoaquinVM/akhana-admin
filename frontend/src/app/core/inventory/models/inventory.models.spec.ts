import { packageEquivalence, remainingText, stockShortages } from './inventory.models';

describe('inventory.models', () => {
  const tea: any = { id: 't', name: 'Té verde', saleUnit: 'UNIT', stockStatus: 'CONTROLLED', stock: 3 };
  const nuts: any = { id: 'n', name: 'Nuez', saleUnit: 'KILOGRAM', stockStatus: 'CONTROLLED', stock: 0.25 };
  const toCount: any = { id: 'o', name: 'Aceite', saleUnit: 'UNIT', stockStatus: 'TO_COUNT', stock: null };

  it('INV-11: «Quedan N» solo para lo que controla stock, en unidades o kilos', () => {
    expect(remainingText(tea)).toBe('Quedan 3');
    expect(remainingText(nuts)).toBe('Quedan 0.250 kg');
    expect(remainingText(toCount)).toBeNull();
    expect(remainingText({ ...tea, stockStatus: 'NOT_CONTROLLED' })).toBeNull();
  });

  it('INV-10: pregunta por cada producto que lleva más de lo que hay, sumando sus líneas', () => {
    expect(stockShortages([{ product: tea, baseQuantity: 3 }])).toEqual([]);
    expect(stockShortages([{ product: tea, baseQuantity: 2 }, { product: tea, baseQuantity: 2 }]))
      .toEqual(['No hay stock registrado de Té verde (quedan 3). ¿Vender igual?']);
    expect(stockShortages([{ product: nuts, baseQuantity: 0.454 }]))
      .toEqual(['No hay stock registrado de Nuez (quedan 0.250 kg). ¿Vender igual?']);
    // INV-18: a product still to count does not ask
    expect(stockShortages([{ product: toCount, baseQuantity: 10 }])).toEqual([]);
  });

  it('CAT-15: el stock se lee en empaques y sueltas («40 = 3 Caja ×12 + 4»)', () => {
    const packages = [{ name: 'Caja ×12', baseQuantity: 12 }];
    expect(packageEquivalence(40, 'UNIT', packages)).toBe('40 = 3 Caja ×12 + 4');
    expect(packageEquivalence(36, 'UNIT', packages)).toBe('36 = 3 Caja ×12');
    expect(packageEquivalence(5, 'UNIT', packages)).toBeNull();
    expect(packageEquivalence(2, 'KILOGRAM', [{ name: 'Saco 25 kg', baseQuantity: 25 }])).toBeNull();
  });
});
