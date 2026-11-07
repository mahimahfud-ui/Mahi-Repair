import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { DeviceRecord } from '../types';

const router = Router();

// POST /api/v1/devices/register - Register or ping an Android device
const RegisterDeviceSchema = z.object({
  device_id: z.string().max(128).optional(),
  device_label: z.string().max(100).optional(),
  app_version: z.string().max(32).default('1.0.0'),
  android_version: z.string().max(32).default('15'),
  android_api: z.number().int().min(21).max(100).optional(),
}).strict();

router.post('/register', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const parsed = RegisterDeviceSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({
      error: { code: 'INVALID_DEVICE_PAYLOAD', issues: parsed.error.issues },
    });
    return;
  }

  const now = new Date().toISOString();
  let device: DeviceRecord | null = null;

  if (parsed.data.device_id) {
    device = await db.getDevice(parsed.data.device_id);
    if (device) {
      if (device.userId !== user.id && user.role !== 'owner') {
        res.status(403).json({ error: { code: 'DEVICE_OWNERSHIP_MISMATCH', message: 'Device is registered to another user.' } });
        return;
      }
      if (device.revokedAt) {
        res.status(403).json({ error: { code: 'DEVICE_REVOKED', message: 'This device identity has been revoked.' } });
        return;
      }
      // Update device ping
      device.lastSeenAt = now;
      if (parsed.data.device_label) device.deviceLabel = parsed.data.device_label;
      device.appVersion = parsed.data.app_version;
      device.androidVersion = parsed.data.android_version;
      if (parsed.data.android_api) device.androidApi = parsed.data.android_api;
      await db.saveDevice(device);

      res.json({
        data: {
          device_id: device.id,
          status: 'updated',
          last_seen_at: device.lastSeenAt,
        },
      });
      return;
    }
  }

  // Generate server-issued UUID device identity
  const newDeviceId = `dev_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const newDevice: DeviceRecord = {
    id: newDeviceId,
    userId: user.id,
    deviceLabel: parsed.data.device_label || 'Android Device',
    appVersion: parsed.data.app_version,
    androidVersion: parsed.data.android_version,
    androidApi: parsed.data.android_api || 35,
    registeredAt: now,
    lastSeenAt: now,
    revokedAt: null,
  };

  await db.saveDevice(newDevice);
  await db.addAuditLog({
    actorId: user.id,
    action: 'device.registered',
    targetType: 'device',
    targetId: newDevice.id,
    details: { deviceLabel: newDevice.deviceLabel, appVersion: newDevice.appVersion },
  });

  res.status(201).json({
    data: {
      device_id: newDevice.id,
      status: 'registered',
      registered_at: newDevice.registeredAt,
    },
  });
});

// GET /api/v1/devices - List devices
router.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  if (user.role === 'owner') {
    const all = await db.getAllDevices();
    res.json({ data: all });
  } else {
    const mine = await db.getUserDevices(user.id);
    res.json({ data: mine });
  }
});

// PUT /api/v1/devices/:id/revoke - Revoke a device (Owner only)
const RevokeSchema = z.object({
  revoked: z.boolean().default(true),
}).strict();

router.put('/:id/revoke', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = RevokeSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const id = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const device = await db.getDevice(id);
  if (!device) {
    res.status(404).json({ error: { code: 'DEVICE_NOT_FOUND', message: 'Device not found.' } });
    return;
  }

  device.revokedAt = parsed.data.revoked ? new Date().toISOString() : null;
  await db.saveDevice(device);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: parsed.data.revoked ? 'device.revoked' : 'device.restored',
    targetType: 'device',
    targetId: device.id,
    details: { revoked: parsed.data.revoked },
  });

  res.json({ data: device });
});

export const devicesRouter = router;
