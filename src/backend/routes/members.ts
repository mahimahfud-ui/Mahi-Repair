import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { UserProfile } from '../types';

const router = Router();

// GET /api/v1/members - List all members (Owner only)
router.get('/', authenticateToken, requireOwner, async (_req: AuthenticatedRequest, res: Response): Promise<void> => {
  const allProfiles = await db.getAllProfiles();
  const members = allProfiles.filter((p) => p.role === 'member');
  res.json({ data: members });
});

// POST /api/v1/members/invite - Invite a new member
const InviteSchema = z.object({
  email: z.string().email().max(255),
  displayName: z.string().max(100).optional(),
  display_name: z.string().max(100).optional(),
}).passthrough();

router.post('/invite', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = InviteSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({
      error: { code: 'INVALID_INVITE_PAYLOAD', issues: parsed.error.issues },
    });
    return;
  }

  const email = parsed.data.email.toLowerCase().trim();
  const displayName = (parsed.data.display_name || parsed.data.displayName || email.split('@')[0]).trim();
  const existing = await db.getProfile(email);
  if (existing) {
    res.status(409).json({
      error: { code: 'MEMBER_ALREADY_EXISTS', message: 'A user with this email already exists.' },
    });
    return;
  }

  const now = new Date().toISOString();
  const newProfile: UserProfile = {
    id: `mem_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    role: 'member',
    status: 'active',
    email,
    displayName,
    createdAt: now,
    updatedAt: now,
  };

  await db.saveProfile(newProfile);
  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'member.invited',
    targetType: 'member_profile',
    targetId: newProfile.id,
    details: { email },
  });

  res.status(201).json({ data: newProfile });
});

// PUT /api/v1/members/:id/status - Update member status (active, suspended, revoked)
const StatusSchema = z.object({
  status: z.enum(['active', 'suspended', 'revoked']),
}).strict();

router.put('/:id/status', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = StatusSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({
      error: { code: 'INVALID_STATUS', issues: parsed.error.issues },
    });
    return;
  }

  const id = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const member = await db.getProfile(id);
  if (!member) {
    res.status(404).json({ error: { code: 'MEMBER_NOT_FOUND', message: 'Member not found.' } });
    return;
  }

  if (member.role === 'owner') {
    res.status(400).json({ error: { code: 'CANNOT_MODIFY_OWNER', message: 'Owner status cannot be modified through this endpoint.' } });
    return;
  }

  const oldStatus = member.status;
  member.status = parsed.data.status;
  member.updatedAt = new Date().toISOString();
  await db.saveProfile(member);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'member.status_changed',
    targetType: 'member_profile',
    targetId: member.id,
    details: { oldStatus, newStatus: member.status, email: member.email },
  });

  res.json({ data: member });
});

export const membersRouter = router;
