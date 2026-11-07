import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

const SECRETS_DIR = path.join(process.cwd(), '.server-secrets');
const PRIVATE_KEY_PATH = path.join(SECRETS_DIR, 'recipe_signing_private_key.pem');
const PUBLIC_KEY_PATH = path.join(process.cwd(), 'recipe_signing_public_key.pem');

let privateKeyPem: string = '';
let publicKeyPem: string = '';

export function getOrInitializeKeypair(): { privateKeyPem: string; publicKeyPem: string } {
  if (privateKeyPem && publicKeyPem) {
    return { privateKeyPem, publicKeyPem };
  }

  // Check environment variables first (e.g. from Google Cloud Secret Manager)
  if (process.env.RECIPE_SIGNING_PRIVATE_KEY && process.env.RECIPE_SIGNING_PUBLIC_KEY) {
    privateKeyPem = process.env.RECIPE_SIGNING_PRIVATE_KEY.replace(/\\n/g, '\n');
    publicKeyPem = process.env.RECIPE_SIGNING_PUBLIC_KEY.replace(/\\n/g, '\n');
    return { privateKeyPem, publicKeyPem };
  }

  // Check file on disk
  if (fs.existsSync(PRIVATE_KEY_PATH) && fs.existsSync(PUBLIC_KEY_PATH)) {
    privateKeyPem = fs.readFileSync(PRIVATE_KEY_PATH, 'utf-8');
    publicKeyPem = fs.readFileSync(PUBLIC_KEY_PATH, 'utf-8');
    return { privateKeyPem, publicKeyPem };
  }

  // Generate new RSA-3072 keypair
  const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 3072,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });

  privateKeyPem = privateKey;
  publicKeyPem = publicKey;

  if (!fs.existsSync(SECRETS_DIR)) {
    fs.mkdirSync(SECRETS_DIR, { mode: 0o700, recursive: true });
  }

  fs.writeFileSync(PRIVATE_KEY_PATH, privateKey, { mode: 0o600 });
  fs.writeFileSync(PUBLIC_KEY_PATH, publicKey, { mode: 0o644 });

  return { privateKeyPem, publicKeyPem };
}

// Canonical JSON matching Edge function and Android expectation: recursive sorted keys
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
  const { privateKeyPem: privKey } = getOrInitializeKeypair();
  const signer = crypto.createSign('SHA256');
  signer.update(canonicalPayload, 'utf-8');
  signer.end();
  const signature = signer.sign(privKey, 'base64');
  return {
    signature,
    algorithm: 'RSA-SHA256',
  };
}

export function verifyCanonicalSignature(canonicalPayload: string, signatureBase64: string): boolean {
  try {
    const { publicKeyPem: pubKey } = getOrInitializeKeypair();
    const verifier = crypto.createVerify('SHA256');
    verifier.update(canonicalPayload, 'utf-8');
    verifier.end();
    return verifier.verify(pubKey, signatureBase64, 'base64');
  } catch {
    return false;
  }
}

export function getPublicKeyDerBase64(): string {
  const { publicKeyPem: pubKey } = getOrInitializeKeypair();
  return pubKey
    .replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\r|\n|\s/g, '')
    .trim();
}
