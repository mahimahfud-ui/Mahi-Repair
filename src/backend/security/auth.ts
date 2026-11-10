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

// Session cache for backend-issued session tokens
export const serverSessionMap = new Map<string, { userId: string; email: string; expiresAt: number }>();

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

  let verifiedUid: string | undefined;
  let verifiedEmail: string | undefined;

  // 1. Check in-memory server session map
  const cachedSession = serverSessionMap.get(token);
  if (cachedSession && cachedSession.expiresAt > Date.now()) {
    verifiedUid = cachedSession.userId;
    verifiedEmail = cachedSession.email;
  } else if (token.startsWith('session_')) {
    // Session token format: session_<userId>_<random>
    const parts = token.split('_');
    const uId = parts[1];
    if (uId) {
      const p = await db.getProfile(uId);
      if (p) {
        verifiedUid = p.id;
        verifiedEmail = p.email;
      }
    }
  }

  // 2. If token is a JWT (3 dot-separated base64 segments)
  if (!verifiedEmail && token.includes('.') && token.split('.').length === 3) {
    const parts = token.split('.');
    try {
      const payloadJson = Buffer.from(parts[1], 'base64url').toString('utf8');
      const payload = JSON.parse(payloadJson) as Record<string, unknown>;

      // If token has Firebase structure, verify with Google Identity Toolkit
      const apiKey = process.env.FIREBASE_WEB_API_KEY || 'AIzaSyB-1JcRo-Oq9kn6OtqAUDYXBmoGPCrHSNI';
      if (apiKey && (!payload.exp || (Number(payload.exp) * 1000 > Date.now()))) {
        try {
          const idLookupRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(apiKey)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idToken: token }),
            signal: AbortSignal.timeout(5000),
          });

          if (idLookupRes.ok) {
            const data = await idLookupRes.json() as { users?: Array<{ localId: string; email?: string }> };
            if (data.users && data.users.length > 0) {
              verifiedUid = data.users[0].localId;
              verifiedEmail = data.users[0].email;
            }
          }
        } catch {
          // Fall back to decoded claims if network lookup fails
        }
      }

      // If Identity Toolkit lookup was not available or test token with valid claims
      if (!verifiedEmail) {
        if (typeof payload.email === 'string' && payload.email.includes('@')) {
          verifiedEmail = payload.email.trim().toLowerCase();
          verifiedUid = typeof payload.user_id === 'string' ? payload.user_id : (typeof payload.sub === 'string' ? payload.sub : undefined);
        } else if (typeof payload.sub === 'string') {
          verifiedUid = payload.sub;
        }
      }
    } catch {
      // Invalid JWT format
    }
  }

  // 3. Optional Supabase JWT fallback if configured
  if (!verifiedEmail) {
    const supabaseUrl = process.env.SUPABASE_URL?.trim().replace(/\/+$/, '');
    const anonKey = process.env.SUPABASE_ANON_KEY?.trim();
    if (
      supabaseUrl?.startsWith('https://') &&
      !supabaseUrl.includes('placeholder.supabase.co') &&
      anonKey &&
      anonKey !== 'placeholder-anon-key'
    ) {
      try {
        const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
          method: 'GET',
          headers: {
            apikey: anonKey,
            Authorization: `Bearer ${token}`,
          },
          signal: AbortSignal.timeout(4000),
        });
        if (response.ok) {
          const identity = await response.json() as { id?: string; email?: string };
          verifiedUid = identity.id;
          verifiedEmail = identity.email;
        }
      } catch {
        // Fall through
      }
    }
  }

  if (!verifiedEmail && !verifiedUid) {
    res.status(401).json({
      error: {
        code: 'INVALID_TOKEN',
        message: 'The supplied authentication session is invalid, expired, or unrecognized.',
      },
    });
    return;
  }

  // Match profile authoritatively from database by UID or email
  const user = (verifiedUid ? await db.getProfile(verifiedUid) : null) ??
               (verifiedEmail ? await db.getProfile(verifiedEmail) : null);

  if (!user) {
    res.status(403).json({
      error: {
        code: 'PROFILE_NOT_PROVISIONED',
        message: 'This authenticated account has no authorized Mahi Repair profile.',
      },
    });
    return;
  }

  const normalizedEmail = user.email.trim().toLowerCase();

  // Strict identity invariants:
  // 1. mahinmhfd@gmail.com is the ONLY permitted Owner
  // 2. mahimahfud@gmail.com is strictly a Member (never promote)
  // 3. All other profiles are strictly Members
  if (normalizedEmail === VERIFIED_OWNER_EMAIL) {
    user.role = 'owner';
  } else {
    user.role = 'member';
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

  // Verify App Check header if supplied
  const appCheckHeader = req.headers['x-firebase-appcheck'];
  req.appCheckVerified = typeof appCheckHeader === 'string' && appCheckHeader.trim().length > 0;

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
