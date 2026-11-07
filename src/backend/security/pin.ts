import crypto from 'crypto';
import { AdminPinConfig } from '../types';

const DEFAULT_INITIAL_PIN = '6993';
const PBKDF2_ITERATIONS = 100000;
const KEY_LENGTH = 64;
const DIGEST = 'sha512';

// In-memory rate limiter for PIN verification attempts
const pinRateLimitMap = new Map<string, { attempts: number; resetAt: number }>();

export function checkPinRateLimit(identifier: string): { allowed: boolean; waitSeconds?: number } {
  const now = Date.now();
  const entry = pinRateLimitMap.get(identifier);

  if (!entry || now > entry.resetAt) {
    pinRateLimitMap.set(identifier, { attempts: 1, resetAt: now + 15 * 60 * 1000 });
    return { allowed: true };
  }

  if (entry.attempts >= 5) {
    const waitSeconds = Math.ceil((entry.resetAt - now) / 1000);
    return { allowed: false, waitSeconds };
  }

  entry.attempts += 1;
  return { allowed: true };
}

export function resetPinRateLimit(identifier: string): void {
  pinRateLimitMap.delete(identifier);
}

export function hashPin(pin: string, salt?: string): { pinHash: string; pinSalt: string; iterations: number } {
  const actualSalt = salt || crypto.randomBytes(32).toString('hex');
  const hashBuffer = crypto.pbkdf2Sync(pin, actualSalt, PBKDF2_ITERATIONS, KEY_LENGTH, DIGEST);
  return {
    pinHash: hashBuffer.toString('hex'),
    pinSalt: actualSalt,
    iterations: PBKDF2_ITERATIONS,
  };
}

export function verifyPinConstantTime(candidatePin: string, storedConfig: AdminPinConfig): boolean {
  try {
    const candidateBuffer = crypto.pbkdf2Sync(
      candidatePin,
      storedConfig.pinSalt,
      storedConfig.iterations,
      KEY_LENGTH,
      DIGEST
    );
    const storedBuffer = Buffer.from(storedConfig.pinHash, 'hex');

    if (candidateBuffer.length !== storedBuffer.length) {
      return false;
    }

    return crypto.timingSafeEqual(candidateBuffer, storedBuffer);
  } catch {
    return false;
  }
}

export function createInitialPinConfig(updatedBy: string = 'system_bootstrap'): AdminPinConfig {
  const { pinHash, pinSalt, iterations } = hashPin(DEFAULT_INITIAL_PIN);
  return {
    id: 'pin_config',
    pinHash,
    pinSalt,
    iterations,
    updatedBy,
    updatedAt: new Date().toISOString(),
  };
}
