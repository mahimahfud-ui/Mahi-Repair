export const OWNER_PIN_MIN_LENGTH = 8;
export const OWNER_PIN_MAX_LENGTH = 12;

/**
 * PINs are stored and verified by Supabase Auth as the existing Owner
 * account's password. Require at least eight numeric digits; leading zeroes
 * are valid and must be preserved.
 */
export function isValidOwnerPin(value: unknown): value is string {
  return typeof value === 'string' && /^\d{8,12}$/.test(value);
}

export function isValidOwnerAuthEmail(value: unknown): value is string {
  return typeof value === 'string' &&
    value.length <= 254 &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}
