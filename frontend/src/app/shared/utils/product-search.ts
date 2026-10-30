// Fields a product search looks at; only code and name are required so lighter product shapes can be searched
export interface SearchableProduct {
  code: string;
  name: string;
  supplierName?: string | null;
  categoryName?: string | null;
  tags?: { name: string }[] | null;
}

// Lowercase without accents, so "Kéfir" and "kefir" are the same
export function normalizeSearchText(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

interface SearchFields {
  code: string;
  name: string;
  nameWords: string[];
  // Supplier, category and tags
  other: string;
  otherWords: string[];
}

const fieldsCache = new WeakMap<object, SearchFields>();

function searchFields(product: SearchableProduct): SearchFields {
  let fields = fieldsCache.get(product);
  if (!fields) {
    const name = normalizeSearchText(product.name);
    const other = [product.supplierName, product.categoryName, ...(product.tags ?? []).map((t) => t.name)]
      .map(normalizeSearchText)
      .join(' ');
    fields = { code: normalizeSearchText(product.code), name, nameWords: words(name), other, otherWords: words(other) };
    fieldsCache.set(product, fields);
  }
  return fields;
}

function words(text: string): string[] {
  return text.split(/[^a-z0-9ñ]+/).filter((w) => w.length > 0);
}

// Typos allowed for a word of the search: none for short words, one from 4 letters, two from 8
function allowedTypos(token: string): number {
  return token.length >= 8 ? 2 : token.length >= 4 ? 1 : 0;
}

// Edit distance with swapped neighbours counted as one typo, stopping early once over the limit
function editDistance(a: string, b: string, limit: number): number {
  if (Math.abs(a.length - b.length) > limit) {
    return limit + 1;
  }
  let before = new Array<number>(b.length + 1).fill(0);
  let previous = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    let rowMin = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      let value = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        value = Math.min(value, before[j - 2] + 1);
      }
      current.push(value);
      rowMin = Math.min(rowMin, value);
    }
    if (rowMin > limit) {
      return limit + 1;
    }
    before = previous;
    previous = current;
  }
  return previous[b.length];
}

// A word of the search is similar to a word of the product when it is close to it or to its start ("pistcho")
function similarWord(token: string, candidates: string[]): boolean {
  const limit = allowedTypos(token);
  return limit > 0 && candidates.some((word) =>
    editDistance(token, word, limit) <= limit
    || (word.length > token.length && editDistance(token, word.slice(0, token.length), limit) <= limit));
}

// Score of one word of the search in a product; 0 when it does not match
function tokenScore(token: string, fields: SearchFields, similar: boolean): number {
  if (fields.code.startsWith(token)) return 60;
  if (fields.nameWords.some((w) => w.startsWith(token))) return 50;
  if (fields.code.includes(token)) return 40;
  if (fields.name.includes(token)) return 30;
  if (fields.otherWords.some((w) => w.startsWith(token))) return 20;
  if (fields.other.includes(token)) return 15;
  if (!similar) return 0;
  if (similarWord(token, fields.nameWords)) return 10;
  if (similarWord(token, fields.otherWords)) return 5;
  return 0;
}

function productScore(term: string, tokens: string[], product: SearchableProduct, similar: boolean): number {
  const fields = searchFields(product);
  let score = 0;
  for (const token of tokens) {
    const value = tokenScore(token, fields, similar);
    if (value === 0) {
      return 0;
    }
    score += value;
  }
  if (fields.code === term) score += 1000;
  else if (fields.name === term) score += 500;
  else if (fields.name.startsWith(term)) score += 100;
  return score;
}

/**
 * Every product matching the search, best first (POS-01): code, name, supplier, category and tags; without case or
 * accents and with the words in any order. When no product has every word, similar words (typos) are accepted.
 * No cap: the caller shows all of them.
 */
export function searchProducts<T extends SearchableProduct>(term: string, products: T[]): T[] {
  const normalized = normalizeSearchText(term);
  const tokens = words(normalized);
  if (tokens.length === 0) {
    return [];
  }
  const rank = (similar: boolean) => products
    .map((product) => ({ product, score: productScore(normalized, tokens, product, similar) }))
    .filter((match) => match.score > 0)
    .sort((a, b) => b.score - a.score || a.product.name.localeCompare(b.product.name, 'es', { sensitivity: 'base' }))
    .map((match) => match.product);
  const exact = rank(false);
  return exact.length > 0 ? exact : rank(true);
}

// The product an Enter in a search box adds: the one whose code is the text typed (without case or accents), or the
// only match; null when it is not clear which one, so nothing is added by mistake (POS-01)
export function productForEnter<T extends { code: string }>(term: string, products: T[], matches: T[]): T | null {
  const typed = normalizeSearchText(term);
  if (!typed) {
    return null;
  }
  return products.find((p) => normalizeSearchText(p.code) === typed) ?? (matches.length === 1 ? matches[0] : null);
}

/**
 * Enter inside a form's text field must not submit it: a purchase or a consumption is registered only with its
 * button. Returns true when the event was an Enter on a field (not a button or a textarea) and was stopped.
 */
export function stopImplicitSubmit(event: Event): boolean {
  const target = event.target as HTMLElement | null;
  if (!target || target.tagName === 'TEXTAREA' || target.tagName === 'BUTTON') {
    return false;
  }
  event.preventDefault();
  return true;
}
