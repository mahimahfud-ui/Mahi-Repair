import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { RepairRecipeZodSchema, RepairToolRecord, RepairToolVersionRecord, CompatibilityEvaluationResult } from '../types';
import { toCanonicalJson, signCanonicalPayload } from '../crypto/signer';
import { evaluateDeviceCompatibility } from '../engine/compatibility';

const router = Router();

// GET /api/v1/tools - List tools (optionally evaluated against a specific device)
router.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const allTools = await db.getAllTools();
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : undefined;

  let device = null;
  if (deviceId) {
    device = await db.getDevice(deviceId);
  }

  let visibleTools: RepairToolRecord[] = [];
  if (user.role === 'owner') {
    visibleTools = allTools;
  } else {
    for (const tool of allTools) {
      if (tool.status === 'PUBLISHED') {
        if (tool.audience === 'all_active_members') {
          visibleTools.push(tool);
        } else {
          const accessList = await db.getToolAccess(tool.id);
          if (accessList.some((a) => a.memberId === user.id)) {
            visibleTools.push(tool);
          }
        }
      }
    }
  }

  // If evaluated against a device, attach deterministic compatibility results
  if (device) {
    const evaluations: Record<string, CompatibilityEvaluationResult> = {};
    for (const tool of visibleTools) {
      evaluations[tool.id] = evaluateDeviceCompatibility(device, tool);
    }
    res.json({
      data: visibleTools,
      device_id: device.id,
      evaluations,
    });
    return;
  }

  res.json({ data: visibleTools });
});

const getId = (param: string | string[] | undefined): string =>
  Array.isArray(param) ? param[0] : param || '';

// GET /api/v1/tools/:id - Get tool details
router.get('/:id', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  const user = req.user!;
  if (user.role !== 'owner' && tool.status !== 'PUBLISHED') {
    res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Access denied to unreleased tool.' } });
    return;
  }

  res.json({ data: tool });
});

// POST /api/v1/tools - Create a new repair tool (Owner only)
const CreateToolSchema = z.object({
  name: z.string().min(1).max(100),
  slug: z.string().min(3).max(80).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  category: z.string().min(1).max(50),
  description: z.string().max(1000).default(''),
  icon: z.string().max(50).default('wrench'),
  draft_recipe: z.record(z.string(), z.unknown()),
}).strict();

router.post('/', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = CreateToolSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_TOOL_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const existingSlug = await db.getTool(parsed.data.slug);
  if (existingSlug) {
    res.status(409).json({ error: { code: 'SLUG_CONFLICT', message: 'A tool with this slug already exists.' } });
    return;
  }

  // Validate recipe schema
  const recipeChecked = RepairRecipeZodSchema.safeParse(parsed.data.draft_recipe);
  if (!recipeChecked.success) {
    res.status(422).json({ error: { code: 'INVALID_RECIPE', issues: recipeChecked.error.issues } });
    return;
  }

  if (recipeChecked.data.toolId !== parsed.data.slug) {
    res.status(422).json({
      error: { code: 'SLUG_MISMATCH', message: `Recipe toolId "${recipeChecked.data.toolId}" must match tool slug "${parsed.data.slug}".` },
    });
    return;
  }

  const now = new Date().toISOString();
  const newToolId = `tool_${parsed.data.slug.replace(/[^a-z0-9]/g, '_')}`;
  const newTool: RepairToolRecord = {
    id: newToolId,
    name: parsed.data.name.trim(),
    slug: parsed.data.slug,
    description: parsed.data.description.trim(),
    category: parsed.data.category,
    icon: parsed.data.icon,
    status: 'DRAFT',
    audience: 'all_active_members',
    version: 0,
    draftRecipe: recipeChecked.data,
    androidCompatibility: { minApi: recipeChecked.data.minAndroidApi || 23 },
    minimumAppVersion: recipeChecked.data.minAppVersion || '1.0.0',
    estimatedSeconds: recipeChecked.data.estimatedSeconds || 60,
    riskClassification: 'guided',
    createdBy: req.user!.id,
    createdAt: now,
    updatedAt: now,
  };

  await db.saveTool(newTool);
  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'tool.created',
    targetType: 'repair_tool',
    targetId: newTool.id,
    details: { slug: newTool.slug, name: newTool.name },
  });

  res.status(201).json({ data: newTool });
});

// PUT /api/v1/tools/:id - Update draft tool (Owner only)
const UpdateToolSchema = z.object({
  name: z.string().min(1).max(100).optional(),
  description: z.string().max(1000).optional(),
  category: z.string().min(1).max(50).optional(),
  icon: z.string().max(50).optional(),
  draft_recipe: z.record(z.string(), z.unknown()).optional(),
}).strict();

router.put('/:id', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = UpdateToolSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  if (tool.status === 'ARCHIVED') {
    res.status(409).json({ error: { code: 'TOOL_ARCHIVED', message: 'Archived tools cannot be modified.' } });
    return;
  }

  if (parsed.data.name) tool.name = parsed.data.name.trim();
  if (parsed.data.description !== undefined) tool.description = parsed.data.description.trim();
  if (parsed.data.category) tool.category = parsed.data.category;
  if (parsed.data.icon) tool.icon = parsed.data.icon;

  if (parsed.data.draft_recipe) {
    const checked = RepairRecipeZodSchema.safeParse(parsed.data.draft_recipe);
    if (!checked.success) {
      res.status(422).json({ error: { code: 'INVALID_RECIPE', issues: checked.error.issues } });
      return;
    }
    tool.draftRecipe = checked.data;
  }

  tool.updatedAt = new Date().toISOString();
  await db.saveTool(tool);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'tool.updated',
    targetType: 'repair_tool',
    targetId: tool.id,
    details: { name: tool.name },
  });

  res.json({ data: tool });
});

// POST /api/v1/tools/:id/publish - Commit version with RSA-3072 signature
const PublishSchema = z.object({
  changelog: z.string().max(2000).optional(),
}).strict();

router.post('/:id/publish', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = PublishSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  if (tool.status === 'ARCHIVED' || tool.status === 'DISABLED') {
    res.status(409).json({
      error: { code: 'TOOL_NOT_PUBLISHABLE', message: 'Archived or disabled tools cannot be published. Re-enable first.' },
    });
    return;
  }

  const nextVersion = tool.version + 1;
  const changelog = parsed.data.changelog || `Release v${nextVersion}`;
  const payloadToSign = {
    ...(tool.draftRecipe as Record<string, unknown>),
    toolId: tool.slug,
    version: nextVersion,
    changelog,
  };

  const checked = RepairRecipeZodSchema.safeParse(payloadToSign);
  if (!checked.success) {
    res.status(422).json({ error: { code: 'INVALID_RECIPE', issues: checked.error.issues } });
    return;
  }

  // Canonicalize & Sign
  const canonicalPayload = toCanonicalJson(checked.data);
  const { signature, algorithm } = signCanonicalPayload(canonicalPayload);

  const now = new Date().toISOString();
  const versionRecord: RepairToolVersionRecord = {
    id: `ver_${tool.id}_${nextVersion}`,
    toolId: tool.id,
    version: nextVersion,
    payload: checked.data,
    canonicalPayload,
    signature,
    signatureAlgorithm: algorithm,
    changelog,
    publishedBy: req.user!.id,
    publishedAt: now,
  };

  await db.saveToolVersion(versionRecord);

  tool.version = nextVersion;
  tool.status = 'PUBLISHED';
  tool.updatedAt = now;
  await db.saveTool(tool);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'tool.published',
    targetType: 'repair_tool',
    targetId: tool.id,
    details: { version: nextVersion, signatureAlgorithm: algorithm },
  });

  res.json({
    data: {
      tool_id: tool.id,
      version: nextVersion,
      signature_algorithm: algorithm,
      published: true,
      published_at: now,
    },
  });
});

// PUT /api/v1/tools/:id/state - Update tool state (DRAFT, TESTING, ARCHIVED)
const StateSchema = z.object({
  status: z.enum(['DRAFT', 'TESTING', 'ARCHIVED']),
}).strict();

router.put('/:id/state', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = StateSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_STATUS', issues: parsed.error.issues } });
    return;
  }

  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  const oldStatus = tool.status;
  tool.status = parsed.data.status;
  tool.updatedAt = new Date().toISOString();
  await db.saveTool(tool);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'tool.state_changed',
    targetType: 'repair_tool',
    targetId: tool.id,
    details: { oldStatus, newStatus: tool.status },
  });

  res.json({ data: tool });
});

// PUT /api/v1/tools/:id/disable - Toggle disable state
const DisableSchema = z.object({
  disabled: z.boolean(),
}).strict();

router.put('/:id/disable', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = DisableSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  tool.status = parsed.data.disabled ? 'DISABLED' : (tool.version > 0 ? 'PUBLISHED' : 'DRAFT');
  tool.updatedAt = new Date().toISOString();
  await db.saveTool(tool);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: parsed.data.disabled ? 'tool.disabled' : 'tool.enabled',
    targetType: 'repair_tool',
    targetId: tool.id,
    details: { disabled: parsed.data.disabled },
  });

  res.json({ data: tool });
});

// POST /api/v1/tools/:id/assign - Assign tool audience and member list
const AssignSchema = z.object({
  audience: z.enum(['all_active_members', 'selected_members']),
  member_ids: z.array(z.string()).optional(),
}).strict();

router.post('/:id/assign', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = AssignSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const tool = await db.getTool(getId(req.params.id));
  if (!tool) {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found.' } });
    return;
  }

  tool.audience = parsed.data.audience;
  tool.updatedAt = new Date().toISOString();
  await db.saveTool(tool);

  const memberIds = parsed.data.audience === 'selected_members' ? (parsed.data.member_ids || []) : [];
  await db.replaceToolAccess(tool.id, memberIds, req.user!.id);

  res.json({
    data: {
      tool_id: tool.id,
      audience: tool.audience,
      assigned_members_count: memberIds.length,
    },
  });
});

// POST /api/v1/tools/preview-compatibility - Simulate tool compatibility against all enrolled devices
router.post('/preview-compatibility', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const toolId = req.body?.tool_id;
  const draftTool = req.body?.draft_tool;

  let toolToTest: RepairToolRecord | null = null;
  if (toolId) {
    toolToTest = await db.getTool(toolId);
  } else if (draftTool) {
    toolToTest = draftTool as RepairToolRecord;
  }

  if (!toolToTest) {
    res.status(400).json({ error: { code: 'INVALID_REQUEST', message: 'tool_id or draft_tool object is required.' } });
    return;
  }

  const devices = await db.getAllDevices();
  const previews = devices.map((device) => {
    const decision = evaluateDeviceCompatibility(device, toolToTest!);
    return {
      deviceId: device.id,
      deviceLabel: device.deviceLabel,
      manufacturer: device.hardwareProfile?.manufacturer || 'Unknown',
      model: device.hardwareProfile?.model || 'Unknown',
      androidSdk: device.hardwareProfile?.androidSdk || device.androidApi,
      decision,
    };
  });

  const eligibleCount = previews.filter((p) => p.decision.isEligible).length;

  res.json({
    data: {
      total_enrolled_devices: devices.length,
      eligible_count: eligibleCount,
      ineligible_count: devices.length - eligibleCount,
      device_previews: previews,
    },
  });
});

export const toolsRouter = router;
