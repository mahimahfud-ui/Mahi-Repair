import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';

const router = Router();

// GET /api/v1/analytics/dashboard - Aggregate analytics
router.get('/dashboard', authenticateToken, requireOwner, async (_req: AuthenticatedRequest, res: Response): Promise<void> => {
  const analytics = await db.getAnalytics();
  res.json({ data: analytics });
});

// GET /api/v1/audit-logs - Query audit history
router.get('/audit-logs', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const limit = Math.min(200, Number(req.query.limit || 50));
  const logs = await db.getAuditLogs(limit);
  res.json({ data: logs });
});

export const analyticsRouter = router;
