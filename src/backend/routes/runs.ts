import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireActiveMember } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { RepairRunRecord } from '../types';

const router = Router();

// POST /api/v1/repair-runs - Submit repair run telemetry
const SubmitRunSchema = z.object({
  run_id: z.string().max(128).optional(),
  device_id: z.string().min(1).max(128),
  tool_id: z.string().min(1).max(128),
  tool_version: z.number().int().positive(),
  started_at: z.string(),
  completed_at: z.string().optional(),
  result: z.enum(['SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED']),
  step_results: z.array(z.object({
    step_id: z.string().max(80),
    action_name: z.string().max(80).optional(),
    status: z.string().max(40),
    duration_ms: z.number().int().min(0).optional(),
    error: z.string().max(500).optional(),
  })).default([]),
  error_code: z.string().max(100).optional(),
  app_version: z.string().max(32).default('1.0.0'),
}).passthrough();

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

  // Idempotency: if run_id was provided and already recorded, return the original acceptance
  if (parsed.data.run_id) {
    const existing = await db.getRepairRun(parsed.data.run_id);
    if (existing) {
      res.json({
        data: {
          run_id: existing.id,
          accepted: true,
        },
      });
      return;
    }
  }

  const now = new Date().toISOString();
  const runId = parsed.data.run_id || `run_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
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
      status: (s.status === 'FAILED' ? 'FAILED' : s.status === 'SKIPPED' ? 'SKIPPED' : 'COMPLETED') as 'COMPLETED' | 'FAILED' | 'SKIPPED',
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
      accepted: true,
    },
  });
});

// GET /api/v1/repair-runs - Retrieve repair history
router.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  let runs: RepairRunRecord[] = [];
  if (user.role === 'owner') {
    runs = await db.getRepairRuns();
  } else {
    runs = await db.getRepairRuns(user.id);
  }

  // Query filters
  const { device_id, tool_id, member_id, result, from, to, limit } = req.query;
  if (typeof device_id === 'string' && device_id) {
    runs = runs.filter((r) => r.deviceId === device_id);
  }
  if (typeof tool_id === 'string' && tool_id) {
    runs = runs.filter((r) => r.toolId === tool_id);
  }
  if (user.role === 'owner' && typeof member_id === 'string' && member_id) {
    runs = runs.filter((r) => r.userId === member_id);
  }
  if (typeof result === 'string' && result) {
    runs = runs.filter((r) => r.result.toUpperCase() === result.toUpperCase());
  }
  if (typeof from === 'string' && from) {
    const fromTime = new Date(from).getTime();
    runs = runs.filter((r) => new Date(r.startedAt).getTime() >= fromTime);
  }
  if (typeof to === 'string' && to) {
    const toTime = new Date(to).getTime();
    runs = runs.filter((r) => new Date(r.startedAt).getTime() <= toTime);
  }

  const limitNum = typeof limit === 'string' ? Math.min(200, parseInt(limit, 10) || 50) : 50;
  runs = runs.slice(0, limitNum);

  res.json({ data: runs });
});

export const runsRouter = router;
