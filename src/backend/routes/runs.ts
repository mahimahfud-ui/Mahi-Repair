import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireActiveMember } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { RepairRunRecord } from '../types';

const router = Router();

// POST /api/v1/repair-runs - Submit repair run telemetry
const SubmitRunSchema = z.object({
  device_id: z.string().min(1).max(128),
  tool_id: z.string().min(1).max(128),
  tool_version: z.number().int().positive(),
  started_at: z.string().datetime(),
  completed_at: z.string().datetime().optional(),
  result: z.enum(['SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED']),
  step_results: z.array(z.object({
    step_id: z.string().max(80),
    action_name: z.string().max(80).optional(),
    status: z.enum(['COMPLETED', 'FAILED', 'SKIPPED']),
    duration_ms: z.number().int().min(0).optional(),
    error: z.string().max(500).optional(),
  })).default([]),
  error_code: z.string().max(100).optional(),
  app_version: z.string().max(32).default('1.0.0'),
}).strict();

router.post('/', authenticateToken, requireActiveMember, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = SubmitRunSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_RUN_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const user = req.user!;
  const device = await db.getDevice(parsed.data.device_id);
  if (!device || device.userId !== user.id) {
    res.status(403).json({ error: { code: 'UNAUTHORIZED_DEVICE', message: 'Device not registered to caller.' } });
    return;
  }

  if (device.revokedAt) {
    res.status(403).json({ error: { code: 'DEVICE_REVOKED', message: 'Device has been revoked.' } });
    return;
  }

  const now = new Date().toISOString();
  const runId = `run_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const runRecord: RepairRunRecord = {
    id: runId,
    userId: user.id,
    deviceId: device.id,
    toolId: parsed.data.tool_id,
    toolVersion: parsed.data.tool_version,
    startedAt: parsed.data.started_at,
    completedAt: parsed.data.completed_at || now,
    result: parsed.data.result,
    stepResults: parsed.data.step_results.map((s) => ({
      stepId: s.step_id,
      actionName: s.action_name,
      status: s.status,
      durationMs: s.duration_ms,
      error: s.error,
    })),
    errorCode: parsed.data.error_code || null,
    appVersion: parsed.data.app_version,
    createdAt: now,
  };

  await db.saveRepairRun(runRecord);

  res.status(201).json({
    data: {
      run_id: runRecord.id,
      recorded: true,
      timestamp: runRecord.createdAt,
    },
  });
});

// GET /api/v1/repair-runs - Retrieve repair history
router.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  if (user.role === 'owner') {
    const all = await db.getRepairRuns();
    res.json({ data: all });
  } else {
    const mine = await db.getRepairRuns(user.id);
    res.json({ data: mine });
  }
});

export const runsRouter = router;
