export type AuthCallbackOriginResult =
  | { ok: true; origin: string }
  | { ok: false; reason: 'missing' | 'invalid' };

/**
 * Resolve the canonical origin used by Supabase email callbacks.
 * Production never falls back to an incoming Origin header: Cloud Run must be
 * explicitly configured with its stable HTTPS service URL or custom domain.
 */
export function resolveAuthCallbackOrigin(
  configuredUrl: string | undefined,
  requestOrigin: string | null,
  isProduction: boolean
): AuthCallbackOriginResult {
  const candidate = isProduction ? configuredUrl?.trim() : configuredUrl?.trim() || requestOrigin?.trim();
  if (!candidate) return { ok: false, reason: 'missing' };

  try {
    const parsed = new URL(candidate);
    const hostname = parsed.hostname.toLowerCase();
    const isLoopback =
      hostname === 'localhost' ||
      hostname.endsWith('.localhost') ||
      hostname === '::1' ||
      hostname === '[::1]' ||
      hostname === '0.0.0.0' ||
      /^127(?:\.\d{1,3}){1,3}$/.test(hostname);

    if (
      !['http:', 'https:'].includes(parsed.protocol) ||
      parsed.username ||
      parsed.password ||
      (parsed.pathname !== '/' && parsed.pathname !== '') ||
      parsed.search ||
      parsed.hash ||
      (isProduction && (parsed.protocol !== 'https:' || isLoopback))
    ) {
      return { ok: false, reason: 'invalid' };
    }

    return { ok: true, origin: parsed.origin };
  } catch {
    return { ok: false, reason: 'invalid' };
  }
}

export function isSupabaseEmailRateLimitError(error: {
  status?: number;
  code?: string;
  message?: string;
}): boolean {
  const code = error.code?.toLowerCase() ?? '';
  const message = error.message?.toLowerCase() ?? '';
  return (
    error.status === 429 ||
    code.includes('rate_limit') ||
    code.includes('rate-limit') ||
    message.includes('rate limit') ||
    message.includes('too many requests')
  );
}
