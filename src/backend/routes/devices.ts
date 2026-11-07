import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import { DeviceRecord } from '../types';

const router = Router();

// Zod schemas for device intelligence
const HardwareProfileSchema = z.object({
  manufacturer: z.string().max(100),
  brand: z.string().max(100),
  model: z.string().max(100),
  commercialModelName: z.string().max(100).optional(),
  modelIdentifier: z.string().max(100).optional(),
  productName: z.string().max(100).default('UNKNOWN'),
  deviceCodename: z.string().max(100).default('UNKNOWN'),
  hardware: z.string().max(100).default('UNKNOWN'),
  socManufacturer: z.string().max(100).default('UNKNOWN'),
  socModel: z.string().max(100).default('UNKNOWN'),
  cpuAbis: z.array(z.string().max(64)).default([]),
  processorArchitecture: z.string().max(64).default('UNKNOWN'),
  cpuCoreCount: z.number().int().min(1).max(128).default(8),
  androidRelease: z.string().max(32).default('15'),
  androidSdk: z.number().int().min(21).max(100).default(35),
  sdkMinorVersion: z.string().max(32).optional(),
  securityPatchDate: z.string().max(32).default('UNKNOWN'),
  buildDisplayInfo: z.string().max(128).default('UNKNOWN'),
  screenWidthPx: z.number().int().default(1080),
  screenHeightPx: z.number().int().default(2400),
  densityDpi: z.number().int().default(420),
  refreshRateHz: z.number().default(60),
  supportedFeatures: z.array(z.string().max(128)).default([]),
  availableSensors: z.array(z.object({
    type: z.number().int(),
    name: z.string().max(100),
    vendor: z.string().max(100),
  })).default([]),
  cameraAvailable: z.boolean().default(true),
  profileConsentVersion: z.number().int().default(1),
  collectedAt: z.string().datetime().optional(),
}).passthrough();

const TelemetrySchema = z.object({
  totalRamBytes: z.number().nonnegative().default(0),
  availableRamBytes: z.number().nonnegative().default(0),
  isLowMemory: z.boolean().default(false),
  internalStorageTotalBytes: z.number().nonnegative().default(0),
  internalStorageAvailableBytes: z.number().nonnegative().default(0),
  storagePressure: z.enum(['NORMAL', 'MODERATE', 'LOW', 'CRITICAL']).default('NORMAL'),
  appSpecificStorageBytes: z.number().nonnegative().optional(),
  batteryLevel: z.number().min(0).max(100).default(100),
  chargingStatus: z.enum(['CHARGING', 'DISCHARGING', 'NOT_CHARGING', 'FULL', 'UNKNOWN']).default('UNKNOWN'),
  powerSource: z.enum(['AC', 'USB', 'WIRELESS', 'BATTERY', 'UNKNOWN']).default('UNKNOWN'),
  batteryTemperature: z.number().optional(),
  batteryHealth: z.enum(['GOOD', 'OVERHEAT', 'DEAD', 'OVER_VOLTAGE', 'UNSPECIFIED_FAILURE', 'COLD', 'UNKNOWN']).default('GOOD'),
  batteryCapacityEstimateMah: z.number().positive().optional(),
  batterySaverActive: z.boolean().default(false),
  thermalStatus: z.enum(['NONE', 'LIGHT', 'MODERATE', 'SEVERE', 'CRITICAL', 'EMERGENCY', 'SHUTDOWN', 'UNKNOWN']).default('NONE'),
  networkConnectivity: z.enum(['CONNECTED', 'DISCONNECTED', 'CAPTIVE_PORTAL']).default('CONNECTED'),
  networkTransport: z.enum(['WIFI', 'CELLULAR', 'ETHERNET', 'BLUETOOTH', 'VPN', 'NONE']).default('WIFI'),
  internetValidated: z.boolean().default(true),
  dnsResolutionLatencyMs: z.number().nonnegative().optional(),
  httpsProbeLatencyMs: z.number().nonnegative().optional(),
  connectionFailureDetails: z.string().max(500).optional(),
  grantedPermissions: z.array(z.string().max(128)).default([]),
  detectedRepairApps: z.array(z.object({
    packageName: z.string().max(255),
    installed: z.boolean(),
    versionName: z.string().max(64).optional(),
  })).default([]),
  lastTelemetryUpdatedAt: z.string().datetime().optional(),
}).passthrough();

const RegisterDeviceSchema = z.object({
  device_id: z.string().max(128).optional(),
  device_label: z.string().max(100).optional(),
  app_installation_id: z.string().max(128).optional(),
  app_version: z.string().max(32).default('1.2.0'),
  android_version: z.string().max(32).default('15'),
  android_api: z.number().int().min(21).max(100).optional(),
  hardware_profile: HardwareProfileSchema.optional(),
  telemetry: TelemetrySchema.optional(),
  field_availability: z.record(z.string(), z.enum(['OBSERVED', 'ESTIMATED', 'UNAVAILABLE', 'PERMISSION_REQUIRED', 'UNSUPPORTED'])).optional(),
}).passthrough();

// POST /api/v1/devices/register - Register device and store device intelligence
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

      device.lastSeenAt = now;
      if (parsed.data.device_label) device.deviceLabel = parsed.data.device_label;
      device.appVersion = parsed.data.app_version;
      device.androidVersion = parsed.data.android_version;
      if (parsed.data.android_api) device.androidApi = parsed.data.android_api;
      if (parsed.data.app_installation_id) device.appInstallationId = parsed.data.app_installation_id;

      if (parsed.data.hardware_profile) {
        device.hardwareProfile = {
          ...parsed.data.hardware_profile,
          collectedAt: parsed.data.hardware_profile.collectedAt || now,
        };
        device.profileVersion = (device.profileVersion || 1) + 1;
        device.lastProfileUpdatedAt = now;
      }

      if (parsed.data.telemetry) {
        device.telemetry = {
          ...parsed.data.telemetry,
          lastTelemetryUpdatedAt: now,
        };
      }

      if (parsed.data.field_availability) {
        device.fieldAvailability = parsed.data.field_availability;
      }

      await db.saveDevice(device);

      res.json({
        data: {
          device_id: device.id,
          status: 'updated',
          last_seen_at: device.lastSeenAt,
          profile_version: device.profileVersion || 1,
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
    deviceLabel: parsed.data.device_label || (parsed.data.hardware_profile ? `${parsed.data.hardware_profile.brand} ${parsed.data.hardware_profile.model}` : 'Android Device'),
    appInstallationId: parsed.data.app_installation_id || `fid_${Math.random().toString(36).slice(2, 10)}`,
    appVersion: parsed.data.app_version,
    androidVersion: parsed.data.android_version,
    androidApi: parsed.data.android_api || parsed.data.hardware_profile?.androidSdk || 35,
    registeredAt: now,
    lastSeenAt: now,
    revokedAt: null,
    profileVersion: 1,
    lastProfileUpdatedAt: now,
    hardwareProfile: parsed.data.hardware_profile
      ? { ...parsed.data.hardware_profile, collectedAt: now }
      : undefined,
    telemetry: parsed.data.telemetry
      ? { ...parsed.data.telemetry, lastTelemetryUpdatedAt: now }
      : undefined,
    fieldAvailability: parsed.data.field_availability,
  };

  await db.saveDevice(newDevice);
  await db.addAuditLog({
    actorId: user.id,
    action: 'device.registered',
    targetType: 'device',
    targetId: newDevice.id,
    details: {
      deviceLabel: newDevice.deviceLabel,
      manufacturer: newDevice.hardwareProfile?.manufacturer || 'Unknown',
      model: newDevice.hardwareProfile?.model || 'Unknown',
    },
  });

  res.status(201).json({
    data: {
      device_id: newDevice.id,
      status: 'registered',
      registered_at: newDevice.registeredAt,
      profile_version: 1,
    },
  });
});

// PUT /api/v1/devices/:id/telemetry - Rapid telemetry refresh (battery, thermal, RAM, network)
router.put('/:id/telemetry', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const id = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const device = await db.getDevice(id);

  if (!device) {
    res.status(404).json({ error: { code: 'DEVICE_NOT_FOUND', message: 'Device not found.' } });
    return;
  }
  if (device.userId !== user.id && user.role !== 'owner') {
    res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Unauthorized device update.' } });
    return;
  }

  const parsed = TelemetrySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_TELEMETRY', issues: parsed.error.issues } });
    return;
  }

  const now = new Date().toISOString();
  device.telemetry = {
    ...parsed.data,
    lastTelemetryUpdatedAt: now,
  };
  device.lastSeenAt = now;
  await db.saveDevice(device);

  res.json({
    data: {
      device_id: device.id,
      telemetry_updated_at: now,
    },
  });
});

// GET /api/v1/devices/:id/compatibility - Real-time compatibility evaluation and categorized tool catalogue
router.get('/:id/compatibility', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const id = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const compatibility = await db.getDeviceCompatibility(id);

  if (!compatibility) {
    res.status(404).json({ error: { code: 'DEVICE_NOT_FOUND', message: 'Device not found.' } });
    return;
  }

  if (compatibility.device.userId !== user.id && user.role !== 'owner') {
    res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Access denied.' } });
    return;
  }

  res.json({
    data: {
      device: compatibility.device,
      catalogue: compatibility.categorized,
      summary: {
        compatibleCount: compatibility.categorized.compatible.length,
        permissionRequiredCount: compatibility.categorized.availableAfterPermission.length,
        preconditionRequiredCount: compatibility.categorized.requiresDiagnosticCheck.length,
        temporarilyUnavailableCount: compatibility.categorized.temporarilyUnavailable.length,
        unsupportedCount: compatibility.categorized.unsupported.length,
      },
    },
  });
});

// GET /api/v1/devices - List all registered devices
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

// GET /api/v1/devices/:id - Single device detail
router.get('/:id', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const id = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const device = await db.getDevice(id);

  if (!device) {
    res.status(404).json({ error: { code: 'DEVICE_NOT_FOUND', message: 'Device not found.' } });
    return;
  }
  if (device.userId !== user.id && user.role !== 'owner') {
    res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Access denied.' } });
    return;
  }

  res.json({ data: device });
});

// PUT /api/v1/devices/:id/revoke - Revoke or restore device access (Owner only)
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
