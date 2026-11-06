import { describe, expect, it } from 'vitest';
import { isSupabaseEmailRateLimitError, resolveAuthCallbackOrigin } from '../src/lib/auth/callback-origin';

describe('auth callback origin', () => {
  it('requires an explicit configured URL in production', () => {
    expect(resolveAuthCallbackOrigin(undefined, 'https://admin.example.com', true)).toEqual({
      ok: false,
      reason: 'missing',
    });
  });

  it('accepts a configured HTTPS production origin', () => {
    expect(resolveAuthCallbackOrigin('https://admin.example.com/', null, true)).toEqual({
      ok: true,
      origin: 'https://admin.example.com',
    });
  });

  it('rejects localhost in production', () => {
    expect(resolveAuthCallbackOrigin('http://localhost:3000', null, true).ok).toBe(false);
    expect(resolveAuthCallbackOrigin('https://localhost:3000', null, true).ok).toBe(false);
    expect(resolveAuthCallbackOrigin('https://127.0.0.1', null, true).ok).toBe(false);
    expect(resolveAuthCallbackOrigin('https://127.10.20.30', null, true).ok).toBe(false);
  });

  it('rejects callback URLs containing a path, query, or credentials', () => {
    expect(resolveAuthCallbackOrigin('https://admin.example.com/auth/callback', null, true).ok).toBe(false);
    expect(resolveAuthCallbackOrigin('https://admin.example.com/?next=/admin', null, true).ok).toBe(false);
    expect(resolveAuthCallbackOrigin('https://user:pass@admin.example.com', null, true).ok).toBe(false);
  });

  it('allows the request origin as a development-only fallback', () => {
    expect(resolveAuthCallbackOrigin(undefined, 'http://localhost:3000', false)).toEqual({
      ok: true,
      origin: 'http://localhost:3000',
    });
  });
});

describe('Supabase email rate-limit detection', () => {
  it('detects HTTP 429 errors', () => {
    expect(isSupabaseEmailRateLimitError({ status: 429, message: 'Request failed' })).toBe(true);
  });

  it('detects Supabase rate-limit codes and messages', () => {
    expect(isSupabaseEmailRateLimitError({ code: 'over_email_send_rate_limit' })).toBe(true);
    expect(isSupabaseEmailRateLimitError({ message: 'Email rate limit exceeded' })).toBe(true);
    expect(isSupabaseEmailRateLimitError({ message: 'Too many requests' })).toBe(true);
  });

  it('does not classify unrelated errors as rate limited', () => {
    expect(isSupabaseEmailRateLimitError({ status: 400, message: 'Invalid email' })).toBe(false);
  });
});
