// POS-18, COM-27: id a screen makes for one new sale or purchase and keeps on its retries, so the server records it
// once even if an answer is lost. crypto.randomUUID only exists on HTTPS or localhost; getRandomValues works anywhere
export function newClientRequestId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

// A lost answer (no connection, 502/504) or a server error: the record may exist, so retrying with the same id is safe
export function isRetryableError(err: { status?: number } | null | undefined): boolean {
  const status = err?.status;
  return typeof status === 'number' && (status === 0 || status >= 500);
}
