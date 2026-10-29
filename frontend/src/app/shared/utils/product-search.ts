// The product an Enter in a search box adds: the one whose code is the text typed (without case), or the only
// match; null when it is not clear which one, so nothing is added by mistake (POS-01)
export function productForEnter<T extends { code: string }>(term: string, products: T[], matches: T[]): T | null {
  const typed = term.trim().toLowerCase();
  if (!typed) {
    return null;
  }
  return products.find((p) => p.code.toLowerCase() === typed) ?? (matches.length === 1 ? matches[0] : null);
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
