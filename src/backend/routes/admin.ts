import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { checkPinRateLimit, resetPinRateLimit, verifyPinConstantTime, hashPin } from '../security/pin';
import { handleGeminiAdminInstruction } from '../ai/geminiAdmin';
import crypto from 'crypto';

const router = Router();

// POST /api/v1/admin/pin/verify - Verify Admin unlock PIN
const VerifyPinSchema = z.object({
  pin: z.string().min(4).max(8).regex(/^\d+$/),
}).strict();

router.post('/pin/verify', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const rateLimitKey = `pin_${user.id}_${req.ip || 'ip'}`;
  const rateLimit = checkPinRateLimit(rateLimitKey);

  if (!rateLimit.allowed) {
    res.status(429).json({
      error: {
        code: 'PIN_RATE_LIMIT_EXCEEDED',
        message: `Too many invalid PIN attempts. Please wait ${rateLimit.waitSeconds} seconds before trying again.`,
      },
    });
    return;
  }

  const parsed = VerifyPinSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PIN_FORMAT', issues: parsed.error.issues } });
    return;
  }

  const storedPinConfig = await db.getAdminPinConfig();
  const isValid = verifyPinConstantTime(parsed.data.pin, storedPinConfig);

  if (!isValid) {
    res.status(401).json({
      error: {
        code: 'INVALID_ADMIN_PIN',
        message: 'Incorrect administrative PIN.',
      },
    });
    return;
  }

  // Reset rate limit on successful authentication
  resetPinRateLimit(rateLimitKey);

  // Generate short-lived admin session token (1 hour)
  const sessionToken = crypto.randomBytes(32).toString('hex');

  await db.addAuditLog({
    actorId: user.id,
    action: 'admin.pin_unlocked',
    targetType: 'admin_panel',
    details: { success: true },
  });

  res.json({
    data: {
      verified: true,
      adminSessionToken: sessionToken,
      expiresInSeconds: 3600,
    },
  });
});

// POST /api/v1/admin/pin/change - Change Admin unlock PIN
const ChangePinSchema = z.object({
  current_pin: z.string().min(4).max(8).regex(/^\d+$/),
  new_pin: z.string().min(4).max(8).regex(/^\d+$/),
}).strict();

router.post('/pin/change', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const parsed = ChangePinSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const storedPinConfig = await db.getAdminPinConfig();
  const isCurrentValid = verifyPinConstantTime(parsed.data.current_pin, storedPinConfig);
  if (!isCurrentValid) {
    res.status(401).json({ error: { code: 'INVALID_CURRENT_PIN', message: 'Current PIN is incorrect.' } });
    return;
  }

  // Hash new PIN with fresh random salt
  const { pinHash, pinSalt, iterations } = hashPin(parsed.data.new_pin);
  storedPinConfig.pinHash = pinHash;
  storedPinConfig.pinSalt = pinSalt;
  storedPinConfig.iterations = iterations;
  storedPinConfig.updatedBy = user.id;
  storedPinConfig.updatedAt = new Date().toISOString();

  await db.saveAdminPinConfig(storedPinConfig);

  await db.addAuditLog({
    actorId: user.id,
    action: 'admin.pin_changed',
    targetType: 'admin_setting',
    targetId: 'pin_config',
    details: { updatedBy: user.email },
  });

  res.json({
    data: {
      success: true,
      message: 'Admin PIN updated successfully.',
      updated_at: storedPinConfig.updatedAt,
    },
  });
});

// POST /api/v1/admin/gemini - Natural language Gemini AI Admin assistant
const GeminiInstructionSchema = z.object({
  instruction: z.string().min(1).max(2000),
  confirmed: z.boolean().default(false),
}).strict();

router.post('/gemini', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = GeminiInstructionSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_INSTRUCTION', issues: parsed.error.issues } });
    return;
  }

  const result = await handleGeminiAdminInstruction(
    parsed.data.instruction,
    req.user!.id,
    parsed.data.confirmed
  );

  res.json({ data: result });
});

export const adminRouter = router;
