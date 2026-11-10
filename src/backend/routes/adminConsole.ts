import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import crypto from 'crypto';
import { RepairRecipeZodSchema, RecipeTestHistoryRecord } from '../types';
import { toCanonicalJson } from '../crypto/signer';

export const adminConsoleRouter = Router();

// GET /api/v1/audit-logs - Query audit history (Owner only)
adminConsoleRouter.get('/audit-logs', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const limit = Math.min(200, Number(req.query.limit || 50));
  const logs = await db.getAuditLogs(limit);
  res.json({ data: logs });
});

// GET /api/v1/tool-versions - Query immutable signed tool versions (Owner only)
adminConsoleRouter.get('/tool-versions', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const toolId = typeof req.query.tool_id === 'string' ? req.query.tool_id : undefined;
  const limit = Math.min(200, Number(req.query.limit || 50));
  const versions = await db.getToolVersions(toolId, limit);
  res.json({ data: versions });
});

// GET /api/v1/recipe-tests - Query recipe test validation history (Owner only)
adminConsoleRouter.get('/recipe-tests', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const limit = Math.min(200, Number(req.query.limit || 50));
  const tests = await db.getRecipeTests(limit);
  res.json({ data: tests });
});

// POST /api/v1/recipe-tests - Validate recipe and append test history (Owner only)
const RecipeTestInputSchema = z.object({
  tool_id: z.string().optional(),
  draft_recipe: z.record(z.string(), z.unknown()).optional(),
  validation_status: z.enum(['VALID', 'INVALID']).optional(),
  issues: z.array(z.unknown()).optional(),
  simulated_step_count: z.number().int().nonnegative().optional(),
}).passthrough();

adminConsoleRouter.post('/recipe-tests', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = RecipeTestInputSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_INPUT', issues: parsed.error.issues } });
    return;
  }

  const recipe = parsed.data.draft_recipe || req.body?.recipe;
  let validationStatus: 'VALID' | 'INVALID' = parsed.data.validation_status || 'VALID';
  const issues: unknown[] = Array.isArray(parsed.data.issues) ? [...parsed.data.issues] : [];
  let stepCount = parsed.data.simulated_step_count || 0;

  if (recipe && typeof recipe === 'object') {
    const checked = RepairRecipeZodSchema.safeParse(recipe);
    if (!checked.success) {
      validationStatus = 'INVALID';
      issues.push(...checked.error.issues);
    } else {
      stepCount = checked.data.steps.length;
    }
  }

  const canonical = recipe ? toCanonicalJson(recipe) : JSON.stringify(req.body);
  const recipeHash = crypto.createHash('sha256').update(canonical).digest('hex');

  const now = new Date().toISOString();
  const testRecord: RecipeTestHistoryRecord = {
    id: `test_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    ownerId: req.user!.id,
    toolId: parsed.data.tool_id || (recipe as { toolId?: string })?.toolId,
    recipeHash,
    validationStatus,
    issues,
    simulatedStepCount: stepCount,
    testedAt: now,
  };

  await db.saveRecipeTest(testRecord);

  res.status(201).json({ data: testRecord });
});

// GET /api/v1/tool-assignments - Query tool assignments (Owner only)
adminConsoleRouter.get('/tool-assignments', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const toolId = typeof req.query.tool_id === 'string' ? req.query.tool_id : undefined;
  const assignments = await db.getAllToolAssignments(toolId);
  res.json({ data: assignments });
});
