import { productForEnter, searchProducts, stopImplicitSubmit } from './product-search';

describe('searchProducts', () => {
  const kefir = { code: 'LAC-KEF-1', name: 'Kéfir de agua 500ml', supplierName: 'Lácteos del Valle', categoryName: 'Bebidas', tags: [] };
  const pistacho = { code: 'FSE-PIS', name: 'Pistacho', supplierName: 'Frutos Andinos', categoryName: 'Frutos secos', tags: [{ name: 'Granel' }] };
  const tab = { code: 'PAT-TAB-PIS-100G', name: 'Tab s/Pistacho 100g', supplierName: 'Patty', categoryName: 'Snacks', tags: [] };
  const mani = { code: 'FSE-MAN', name: 'Maní salado', supplierName: 'Frutos Andinos', categoryName: 'Frutos secos', tags: [] };
  const all = [kefir, pistacho, tab, mani];

  it('sin mayúsculas ni tildes, con las palabras en cualquier orden', () => {
    expect(searchProducts('KEFIR', all)).toEqual([kefir]);
    expect(searchProducts('mani', all)).toEqual([mani]);
    expect(searchProducts('100g pistacho', all)).toEqual([tab]);
    expect(searchProducts('   ', all)).toEqual([]);
  });

  it('busca por proveedor, categoría y etiqueta', () => {
    expect(searchProducts('frutos andinos', all)).toEqual([mani, pistacho]);
    expect(searchProducts('lacteos', all)).toEqual([kefir]);
    expect(searchProducts('granel', all)).toEqual([pistacho]);
    expect(searchProducts('andinos pista', all)).toEqual([pistacho]);
  });

  it('ordena por relevancia: código exacto, nombre exacto y luego coincidencias en el nombre antes que en el proveedor', () => {
    expect(searchProducts('pistacho', all)).toEqual([pistacho, tab]);
    expect(searchProducts('fse-pis', all)[0]).toBe(pistacho);
    const supplierNamedLikeProduct = { code: 'X-1', name: 'Almendra', supplierName: 'Pistachos SRL', tags: [] };
    expect(searchProducts('pistacho', [supplierNamedLikeProduct, tab])).toEqual([tab, supplierNamedLikeProduct]);
  });

  it('si ninguno tiene todas las palabras, acepta errores de tipeo', () => {
    expect(searchProducts('pitsacho', all)).toEqual([pistacho, tab]);
    expect(searchProducts('kefri', all)).toEqual([kefir]);
    expect(searchProducts('mani slado', all)).toEqual([mani]);
    expect(searchProducts('zzz', all)).toEqual([]);
    // With exact matches, similar ones are left out
    expect(searchProducts('pista', [pistacho, { code: 'Z', name: 'Pasta', tags: [] }])).toEqual([pistacho]);
  });

  it('devuelve todos los resultados, sin tope', () => {
    const many = Array.from({ length: 120 }, (_, i) => ({ code: `K-${i}`, name: `Kéfir ${i}` }));
    expect(searchProducts('kefir', many).length).toBe(120);
  });
});

describe('productForEnter', () => {
  const a = { code: 'TAB-100', name: 'Tab 100' };
  const b = { code: 'TAB-50', name: 'Tab 50' };

  it('elige el código exacto, o el único resultado; si no está claro, ninguno', () => {
    expect(productForEnter('tab-50', [a, b], [a, b])).toBe(b);
    expect(productForEnter('tab', [a, b], [a, b])).toBeNull();
    expect(productForEnter('100', [a, b], [a])).toBe(a);
    expect(productForEnter('  ', [a, b], [a])).toBeNull();
    expect(productForEnter(' Tab-50 ', [a, b], [a, b])).toBe(b);
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
