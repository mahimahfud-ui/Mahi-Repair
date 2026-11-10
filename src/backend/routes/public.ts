import { Router, Request, Response } from 'express';
import { getOrInitializeKeypair, getPublicKeyDerBase64 } from '../crypto/signer';

const router = Router();

// GET /api/v1/public-key - Expose RSA-3072 public key for Android APK
router.get('/public-key', (_req: Request, res: Response): void => {
  const { publicKeyPem } = getOrInitializeKeypair();
  const derBase64 = getPublicKeyDerBase64();

  res.json({
    data: {
      algorithm: 'RSA-SHA256',
      key_format: 'X.509',
      key_size: 3072,
      public_key_pem: publicKeyPem,
      public_key_der_base64: derBase64,
    },
  });
});

// In-memory live Data Pack state for dual-sync across web and app
let liveDataPack = {
  version: '1.2.4',
  releaseName: 'eFootball-Style Live Data Pack',
  publishedAt: Date.now(),
  tools: [] as any[],
};

// GET /api/v1/datapack/status - Live Data Pack status for in-app updates
router.get('/datapack/status', (_req: Request, res: Response): void => {
  res.json({
    status: 'active',
    version: liveDataPack.version,
    release_name: liveDataPack.releaseName,
    published_at: liveDataPack.publishedAt,
    tools_count: liveDataPack.tools.length,
    dual_sync_status: 'SYNCHRONIZED',
  });
});

// GET /api/v1/datapack/catalog - Fetch latest published tools
router.get('/datapack/catalog', (_req: Request, res: Response): void => {
  res.json({
    version: liveDataPack.version,
    published_at: liveDataPack.publishedAt,
    tools: liveDataPack.tools,
  });
});

// POST /api/v1/datapack/publish - Publish tools from Owner Admin Console
router.post('/datapack/publish', (req: Request, res: Response): void => {
  const { tools, note, releaseName } = req.body || {};
  if (Array.isArray(tools)) {
    liveDataPack.tools = tools;
  }
  // Increment minor version
  const parts = liveDataPack.version.split('.');
  const minor = parseInt(parts[2] || '0', 10) + 1;
  liveDataPack.version = `${parts[0] || '1'}.${parts[1] || '2'}.${minor}`;
  liveDataPack.publishedAt = Date.now();
  if (releaseName) {
    liveDataPack.releaseName = releaseName;
  }

  console.log(`[DataPack] Published version ${liveDataPack.version} (${liveDataPack.tools.length} tools): ${note || 'Admin live push'}`);
  res.json({
    success: true,
    version: liveDataPack.version,
    published_at: liveDataPack.publishedAt,
    tools_count: liveDataPack.tools.length,
    message: `Data Pack ${liveDataPack.version} published and synchronized live.`,
  });
});

// GET /api/v1/health - System healthcheck
router.get('/health', (_req: Request, res: Response): void => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    uptime_seconds: Math.floor(process.uptime()),
    service: 'mahi-repair-cloud-backend',
    firebase: {
      project_id: 'manifest-hawk-76tvw',
      firestore_database_id: 'ai-studio-mahirepair-a294799a-c294-4076-998a-6efc3ff9c0b7',
      auth: 'active',
    },
    version: '1.2.0-staging',
  });
});

export const publicRouter = router;

