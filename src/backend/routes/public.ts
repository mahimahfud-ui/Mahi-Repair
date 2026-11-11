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
