import { describe, expect, it } from 'vitest';
import { isRetryableError, newClientRequestId } from './client-request-id';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newClientRequestId', () => {
  it('da un UUID distinto cada vez', () => {
    const first = newClientRequestId();
    expect(first).toMatch(UUID_V4);
    expect(newClientRequestId()).not.toBe(first);
  });

  it('sin crypto.randomUUID (página sin HTTPS) también da un UUID v4', () => {
    const original = crypto.randomUUID;
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      expect(newClientRequestId()).toMatch(UUID_V4);
    } finally {
      Object.defineProperty(crypto, 'randomUUID', { value: original, configurable: true });
    }
  });
});

describe('isRetryableError', () => {
  it('sin respuesta o con 5xx se puede reintentar; un 4xx no', () => {
    expect(isRetryableError({ status: 0 })).toBe(true);
    expect(isRetryableError({ status: 502 })).toBe(true);
    expect(isRetryableError({ status: 504 })).toBe(true);
    expect(isRetryableError({ status: 409 })).toBe(false);
    expect(isRetryableError({ status: 400 })).toBe(false);
  });
});
