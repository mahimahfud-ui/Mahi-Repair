import { Request, Response, NextFunction } from 'express';
import { db } from '../db';
import { UserProfile } from '../types';

export const VERIFIED_OWNER_EMAIL = 'mahinmhfd@gmail.com';

export interface AuthenticatedRequest extends Request {
  user?: UserProfile;
  appCheckVerified?: boolean;
}

// In-memory rate limiting map for general API endpoints
const apiRateLimitMap = new Map<string, { count: number; resetAt: number }>();

export function rateLimitMiddleware(limit = 120, windowMs = 60 * 1000) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const now = Date.now();
    const entry = apiRateLimitMap.get(ip);

    if (!entry || now > entry.resetAt) {
      apiRateLimitMap.set(ip, { count: 1, resetAt: now + windowMs });
      return next();
    }

    if (entry.count >= limit) {
      res.status(429).json({
        error: {
          code: 'RATE_LIMIT_EXCEEDED',
          message: 'Too many requests. Please slow down and try again shortly.',
        },
      });
      return;
    }

    entry.count += 1;
    next();
  };
}

export async function authenticateToken(req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({
      error: {
        code: 'UNAUTHENTICATED',
        message: 'Missing or invalid Authorization header. Expected Bearer <token>.',
      },
    });
    return;
  }

  const token = authHeader.slice(7).trim();
  if (!token) {
    res.status(401).json({
      error: {
        code: 'INVALID_TOKEN',
        message: 'Bearer token is empty.',
      },
    });
    return;
  }

  // Never infer identity from token text, email-shaped strings, demo substrings,
  // or hard-coded Owner names. Verify the access token with Supabase Auth.
  const supabaseUrl = process.env.SUPABASE_URL?.trim().replace(/\/+$/, '');
  const anonKey = process.env.SUPABASE_ANON_KEY?.trim();
  if (
    !supabaseUrl?.startsWith('https://') ||
    supabaseUrl.includes('placeholder.supabase.co') ||
    !anonKey ||
    anonKey === 'placeholder-anon-key'
  ) {
    res.status(503).json({
      error: {
        code: 'AUTH_PROVIDER_NOT_CONFIGURED',
        message: 'Authentication verification is not configured on this server.',
      },
    });
    return;
  }

  let identity: { id?: string; email?: string };
  try {
    const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
      method: 'GET',
      headers: {
        apikey: anonKey,
        Authorization: `Bearer ${token}`,
      },
      signal: AbortSignal.timeout(8000),
    });

    if (response.status === 401 || response.status === 403) {
      res.status(401).json({
        error: {
          code: 'INVALID_TOKEN',
          message: 'The supplied authentication session is invalid or expired.',
        },
      });
      return;
    }

    if (!response.ok) {
      res.status(503).json({
        error: {
          code: 'AUTH_PROVIDER_UNAVAILABLE',
          message: 'Authentication could not be verified. Please retry.',
        },
      });
      return;
    }

    identity = await response.json() as { id?: string; email?: string };
  } catch {
    res.status(503).json({
      error: {
        code: 'AUTH_PROVIDER_UNAVAILABLE',
        message: 'Authentication could not be verified. Please retry.',
      },
    });
    return;
  }

  if (!identity.id || !identity.email) {
    res.status(401).json({
      error: {
        code: 'INVALID_IDENTITY',
        message: 'The verified authentication response did not contain a usable identity.',
      },
    });
    return;
  }

  // Profiles carry server-assigned roles. Match by verified Auth UID first,
  // then by the verified email for existing pre-UID profile records.
  const user = await db.getProfile(identity.id) ?? await db.getProfile(identity.email);
  if (!user || user.email.toLowerCase() !== identity.email.toLowerCase()) {
    res.status(403).json({
      error: {
        code: 'PROFILE_NOT_PROVISIONED',
        message: 'This authenticated account has no authorized Mahi Repair profile.',
      },
    });
    return;
  }

  if (user.status !== 'active') {
    res.status(403).json({
      error: {
        code: user.status === 'revoked' ? 'ACCOUNT_REVOKED' : 'ACCOUNT_INACTIVE',
        message: 'This account is not active.',
      },
    });
    return;
  }

  // A caller-supplied App Check header is not evidence of verification.
  // Set appCheckVerified only after a real server-side App Check verification is added.
  req.user = user;
  next();
}

export function requireOwner(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  if (!req.user) {
    res.status(401).json({ error: { code: 'UNAUTHENTICATED', message: 'Authentication required.' } });
    return;
  }

  if (
    req.user.role !== 'owner' ||
    req.user.status !== 'active' ||
    req.user.email.trim().toLowerCase() !== VERIFIED_OWNER_EMAIL
  ) {
    res.status(403).json({
      error: {
        code: 'FORBIDDEN_OWNER_REQUIRED',
        message: 'Administrative privileges required. This endpoint is restricted to active Owners.',
      },
    });
    return;
  }

  next();
}

export function requireActiveMember(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  if (!req.user) {
    res.status(401).json({ error: { code: 'UNAUTHENTICATED', message: 'Authentication required.' } });
    return;
  }

  if (req.user.status !== 'active') {
    res.status(403).json({
      error: {
        code: 'ACCOUNT_INACTIVE',
        message: 'Your account is not active.',
      },
    });
    return;
  }

  next();
}
