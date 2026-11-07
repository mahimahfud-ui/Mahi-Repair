import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

const SECRETS_DIR = path.join(process.cwd(), '.server-secrets');
const PRIVATE_KEY_PATH = path.join(SECRETS_DIR, 'recipe_signing_private_key.pem');
const PUBLIC_KEY_PATH = path.join(process.cwd(), 'recipe_signing_public_key.pem');

let privateKeyPem = '';
let publicKeyPem = '';

function normalizePem(value: string): string {
  return value.replace(/\\n/g, '\n').trim() + '\n';
}

function assertMatchingRsa3072Keypair(privatePem: string, publicPem: string): void {
  const privateKey = crypto.createPrivateKey(privatePem);
  const publicKey = crypto.createPublicKey(publicPem);

  if (privateKey.asymmetricKeyType !== 'rsa' || publicKey.asymmetricKeyType !== 'rsa') {
    throw new Error('Recipe signing keypair must use RSA.');
  }

  const modulusLength = privateKey.asymmetricKeyDetails?.modulusLength;
  if (modulusLength !== 3072 || publicKey.asymmetricKeyDetails?.modulusLength !== 3072) {
    throw new Error('Recipe signing keypair must use RSA-3072.');
  }

  const derivedPublicDer = crypto.createPublicKey(privateKey).export({ type: 'spki', format: 'der' });
  const configuredPublicDer = publicKey.export({ type: 'spki', format: 'der' });
  if (!Buffer.from(derivedPublicDer).equals(Buffer.from(configuredPublicDer))) {
    throw new Error('Recipe signing private and public keys do not match.');
  }
}

export function getOrInitializeKeypair(): { privateKeyPem: string; publicKeyPem: string } {
  if (privateKeyPem && publicKeyPem) {
    return { privateKeyPem, publicKeyPem };
  }

  // Accept the canonical *_PEM names and the original names for controlled rotation.
  const configuredPrivate = process.env.RECIPE_SIGNING_PRIVATE_KEY_PEM || process.env.RECIPE_SIGNING_PRIVATE_KEY;
  const configuredPublic = process.env.RECIPE_SIGNING_PUBLIC_KEY_PEM || process.env.RECIPE_SIGNING_PUBLIC_KEY;

  if (configuredPrivate || configuredPublic) {
    if (!configuredPrivate || !configuredPublic) {
      throw new Error('Both recipe signing private and public PEM secrets must be configured.');
    }

    const normalizedPrivate = normalizePem(configuredPrivate);
    const normalizedPublic = normalizePem(configuredPublic);
    assertMatchingRsa3072Keypair(normalizedPrivate, normalizedPublic);
    privateKeyPem = normalizedPrivate;
    publicKeyPem = normalizedPublic;
    return { privateKeyPem, publicKeyPem };
  }

  // Local development may use a disk keypair. Production must use Secret Manager secrets.
  if (fs.existsSync(PRIVATE_KEY_PATH) && fs.existsSync(PUBLIC_KEY_PATH)) {
    const diskPrivate = fs.readFileSync(PRIVATE_KEY_PATH, 'utf-8');
    const diskPublic = fs.readFileSync(PUBLIC_KEY_PATH, 'utf-8');
    assertMatchingRsa3072Keypair(diskPrivate, diskPublic);
    privateKeyPem = diskPrivate;
    publicKeyPem = diskPublic;
    return { privateKeyPem, publicKeyPem };
  }

  if (process.env.NODE_ENV === 'production') {
    throw new Error('Recipe signing keys are not configured. Set both PEM values in the server secret manager.');
  }

  // Development-only ephemeral keypair. Never silently generate per-instance keys in production.
  const generated = crypto.generateKeyPairSync('rsa', {
    modulusLength: 3072,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });

  privateKeyPem = generated.privateKey;
  publicKeyPem = generated.publicKey;

  if (!fs.existsSync(SECRETS_DIR)) {
    fs.mkdirSync(SECRETS_DIR, { mode: 0o700, recursive: true });
  }

  fs.writeFileSync(PRIVATE_KEY_PATH, privateKeyPem, { mode: 0o600 });
  fs.writeFileSync(PUBLIC_KEY_PATH, publicKeyPem, { mode: 0o644 });

  return { privateKeyPem, publicKeyPem };
}

// Canonical JSON matching Admin, server and Android expectations: recursively sorted keys.
export function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (typeof value === 'object' && value !== null) {
    const record = value as Record<string, unknown>;
    const sortedKeys = Object.keys(record).sort();
    const result: Record<string, unknown> = {};
    for (const key of sortedKeys) {
      result[key] = canonicalize(record[key]);
    }
    return result;
  }
  return value;
}

export function toCanonicalJson(value: unknown): string {
  return JSON.stringify(canonicalize(value));
}

export function signCanonicalPayload(canonicalPayload: string): { signature: string; algorithm: 'RSA-SHA256' } {
  const { privateKeyPem: privateKey } = getOrInitializeKeypair();
  const signer = crypto.createSign('SHA256');
  signer.update(canonicalPayload, 'utf-8');
  signer.end();
  const signature = signer.sign(privateKey, 'base64');
  return { signature, algorithm: 'RSA-SHA256' };
}

export function verifyCanonicalSignature(canonicalPayload: string, signatureBase64: string): boolean {
  try {
    const { publicKeyPem: publicKey } = getOrInitializeKeypair();
    const verifier = crypto.createVerify('SHA256');
    verifier.update(canonicalPayload, 'utf-8');
    verifier.end();
    return verifier.verify(publicKey, signatureBase64, 'base64');
  } catch {
    return false;
  }
}

export function getPublicKeyDerBase64(): string {
  const { publicKeyPem: publicKey } = getOrInitializeKeypair();
  return publicKey
    .replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\r|\\n|\\s/g, '')
    .trim();
}
