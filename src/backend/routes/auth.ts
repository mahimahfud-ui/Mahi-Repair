import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';

const router = Router();

// POST /api/v1/auth/verify - Verify identity and role
router.post('/verify', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  res.json({
    data: {
      userId: user.id,
      email: user.email,
      role: user.role,
      status: user.status,
      displayName: user.displayName,
      isOwner: user.role === 'owner' && user.status === 'active',
      appCheckVerified: !!req.appCheckVerified,
    },
  });
});

// GET /api/v1/auth/me - Current user profile and registered devices
router.get('/me', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const devices = await db.getUserDevices(user.id);
  res.json({
    data: {
      profile: user,
      devices,
    },
  });
});

export const authRouter = router;
