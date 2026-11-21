import { searchProducts, SearchableProduct } from './product-search';
import table from '../../../../../contracts/product-search-cases.json';

// The backend's ProductTextMatcher (COM-21) replicates this search on purpose: both run the table of
// contracts/product-search-cases.json (ProductTextMatcherContractTest), so the two cannot drift apart
describe('searchProducts con la tabla compartida con el servidor', () => {
  const products: SearchableProduct[] = table.products.map((p) => ({ ...p, tags: [] }));

  it.each(table.cases.map((c) => [c.about, c.term, c.expected] as const))('%s: «%s»', (_about, term, expected) => {
    expect(searchProducts(term, products).map((p) => p.code)).toEqual(expected);
  });
});
