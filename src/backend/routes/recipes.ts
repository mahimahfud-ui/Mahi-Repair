import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireActiveMember } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';

const router = Router();

// POST /api/v1/recipes/my-tools - Return current authorized tool releases for Android
const MyToolsSchema = z.object({
  device_id: z.string().min(1).max(128),
}).strict();

router.post('/my-tools', authenticateToken, requireActiveMember, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = MyToolsSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_REQUEST', issues: parsed.error.issues } });
    return;
  }

  const user = req.user!;
  const device = await db.getDevice(parsed.data.device_id);
  if (!device || device.userId !== user.id) {
    res.status(403).json({ error: { code: 'UNAUTHORIZED_DEVICE', message: 'Device not registered to current member.' } });
    return;
  }

  if (device.revokedAt) {
    res.status(403).json({ error: { code: 'DEVICE_REVOKED', message: 'Device has been revoked.' } });
    return;
  }

  const allTools = await db.getAllTools();
  const authorizedTools = [];

  for (const tool of allTools) {
    if (tool.status === 'PUBLISHED') {
      let hasAccess = false;
      if (tool.audience === 'all_active_members') {
        hasAccess = true;
      } else {
        const accessList = await db.getToolAccess(tool.id);
        if (accessList.some((a) => a.memberId === user.id)) {
          hasAccess = true;
        }
      }

      if (hasAccess && tool.version > 0) {
        const versionRecord = await db.getToolVersion(tool.id, tool.version);
        if (versionRecord) {
          authorizedTools.push({
            tool_id: tool.id,
            slug: tool.slug,
            name: tool.name,
            category: tool.category,
            description: tool.description,
            icon: tool.icon,
            version: tool.version,
            estimated_seconds: tool.estimatedSeconds,
            signed_payload: versionRecord.canonicalPayload,
            signature: versionRecord.signature,
            signature_algorithm: versionRecord.signatureAlgorithm,
            published_at: versionRecord.publishedAt,
          });

          // Record per-device sync version
          await db.recordDeviceSync(device.id, user.id, tool.id, tool.version);
        }
      }
    }
  }

  res.json({
    data: authorizedTools,
  });
});

// POST /api/v1/recipes/signed-tool - Return detached signature and canonical payload
const SignedToolSchema = z.object({
  tool_id: z.string().min(1).max(128),
  device_id: z.string().min(1).max(128),
}).strict();

router.post('/signed-tool', authenticateToken, requireActiveMember, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = SignedToolSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_REQUEST', issues: parsed.error.issues } });
    return;
  }

  const user = req.user!;
  const device = await db.getDevice(parsed.data.device_id);
  if (!device || device.userId !== user.id) {
    res.status(403).json({ error: { code: 'UNAUTHORIZED_DEVICE', message: 'Device not registered to member.' } });
    return;
  }

  if (device.revokedAt) {
    res.status(403).json({ error: { code: 'DEVICE_REVOKED', message: 'Device has been revoked.' } });
    return;
  }

  const tool = await db.getTool(parsed.data.tool_id);
  if (!tool || tool.status !== 'PUBLISHED') {
    res.status(404).json({ error: { code: 'TOOL_NOT_FOUND', message: 'Tool not found or not published.' } });
    return;
  }

  if (tool.audience === 'selected_members') {
    const accessList = await db.getToolAccess(tool.id);
    if (!accessList.some((a) => a.memberId === user.id)) {
      res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Member not granted access to this tool.' } });
      return;
    }
  }

  const versionRecord = await db.getToolVersion(tool.id, tool.version);
  if (!versionRecord) {
    res.status(404).json({ error: { code: 'VERSION_NOT_FOUND', message: 'No signed version release found.' } });
    return;
  }

  // Record per-device sync
  await db.recordDeviceSync(device.id, user.id, tool.id, tool.version);

  res.json({
    data: {
      tool_id: tool.id,
      version: versionRecord.version,
      signed_payload: versionRecord.canonicalPayload,
      signature: versionRecord.signature,
      signature_algorithm: versionRecord.signatureAlgorithm,
      key_id: 'repair-signing-2026-01',
      published_at: versionRecord.publishedAt,
    },
  });
});

export const recipesRouter = router;
