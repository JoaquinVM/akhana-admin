// CAT-10: UPC-A is the same code as EAN-13 with a leading 0, which is how the server stores it. The check digit is only
// validated by the server: an invalid code simply matches nothing
export function canonicalBarcode(code: string): string {
  const digits = code.trim();
  return /^[0-9]{12}$/.test(digits) ? `0${digits}` : digits;
}

export function looksLikeBarcode(term: string): boolean {
  return /^([0-9]{8}|[0-9]{12}|[0-9]{13})$/.test(term.trim());
}
