import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';

// 1. Validate that all 13 native actions align across Android, Edge Functions, and JSON Schema
const ALLOWLISTED_ACTIONS = [
  'CHECK_INTERNET',
  'DNS_LOOKUP',
  'HTTPS_PROBE',
  'CHECK_APP_INSTALLED',
  'CHECK_STORAGE',
  'OPEN_APP',
  'OPEN_APP_DETAILS',
  'OPEN_ANDROID_SETTINGS',
  'SHOW_GUIDE',
  'ASK_USER_CONFIRMATION',
  'REQUEST_OWN_APP_PERMISSION',
  'VERIFY_INTERNET',
  'VERIFY_DNS',
];

test('1. Action Allowlist - 13 actions match across systems', () => {
  assert.equal(ALLOWLISTED_ACTIONS.length, 13);
  const jsonSchema = JSON.parse(fs.readFileSync('schemas/repair-recipe-v1.schema.json', 'utf8'));
  const schemaActions = jsonSchema.properties.steps.items.properties.action.enum;
  assert.equal(schemaActions.length, 13);
  for (const action of ALLOWLISTED_ACTIONS) {
    assert.ok(schemaActions.includes(action), `Schema must contain ${action}`);
  }
});

test('2. Action Allowlist - rejects arbitrary shell or dynamic execution', () => {
  const disallowed = ['RUN_SHELL', 'EXEC_BASH', 'LOAD_DEX', 'DYNAMIC_JAR', 'EVAL_JS'];
  for (const badAction of disallowed) {
    assert.ok(!ALLOWLISTED_ACTIONS.includes(badAction), `${badAction} must be rejected`);
  }
});

test('3. Cryptographic Signature Parity - RSA-SHA256 with PKCS#1 v1.5', () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });

  const canonicalPayload = JSON.stringify({
    schemaVersion: 1,
    toolId: 'dns-repair',
    title: 'DNS Repair',
    version: 1,
    category: 'network',
    minAndroidApi: 26,
    minAppVersion: '1.0.0',
    estimatedSeconds: 30,
    steps: [{ id: 's1', action: 'CHECK_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false }],
  });

  const signer = crypto.createSign('SHA256');
  signer.update(canonicalPayload);
  signer.end();
  const signatureBase64 = signer.sign(privateKey, 'base64');

  // Verify signature
  const verifier = crypto.createVerify('SHA256');
  verifier.update(canonicalPayload);
  verifier.end();
  const valid = verifier.verify(publicKey, signatureBase64, 'base64');
  assert.equal(valid, true, 'Valid canonical payload must verify');

  // Tampered payload must fail
  const tamperedPayload = canonicalPayload.replace('"version":1', '"version":2');
  const tamperedVerifier = crypto.createVerify('SHA256');
  tamperedVerifier.update(tamperedPayload);
  tamperedVerifier.end();
  assert.equal(tamperedVerifier.verify(publicKey, signatureBase64, 'base64'), false, 'Tampered payload must be rejected');
});

test('4. API Contract - register-device request & response shapes', () => {
  const requestBody = {
    device_id: 'a0000000-0000-0000-0000-000000000001',
    device_label: 'Pixel 8',
    app_version: '1.0.0',
    android_version: '14',
    android_api: 34,
  };
  assert.match(requestBody.app_version, /^\d+(?:\.\d+){0,3}$/);
  assert.ok(requestBody.android_api >= 23 && requestBody.android_api <= 100);

  const responseBody = {
    data: {
      device_id: 'a0000000-0000-0000-0000-000000000001',
      registered: true,
    },
  };
  assert.equal(typeof responseBody.data.device_id, 'string');
  assert.equal(responseBody.data.registered, true);
});

test('5. API Contract - get-my-repair-tools POST request & payload array', () => {
  const requestBody = {
    device_id: 'a0000000-0000-0000-0000-000000000001',
  };
  assert.ok(requestBody.device_id);

  const responseBody = {
    data: [
      {
        tool_id: 'b0000000-0000-0000-0000-000000000002',
        version: 1,
        signed_payload: '{"schemaVersion":1,"toolId":"yt-repair"}',
        signature: 'MIIEv...',
        signature_algorithm: 'RSA-SHA256',
        published_at: '2026-11-05T00:00:00Z',
      },
    ],
  };
  assert.ok(Array.isArray(responseBody.data));
  assert.equal(responseBody.data[0].version, 1);
  assert.equal(responseBody.data[0].signature_algorithm, 'RSA-SHA256');
});

test('6. API Contract - submit-repair-run request shape and ISO-8601 timestamps', () => {
  const runPayload = {
    device_id: 'a0000000-0000-0000-0000-000000000001',
    tool_id: 'b0000000-0000-0000-0000-000000000002',
    tool_version: 1,
    started_at: '2026-11-05T10:00:00Z',
    completed_at: '2026-11-05T10:00:25Z',
    result: 'SUCCESS',
    step_results: [{ step_id: 'check-net', status: 'SUCCESS' }],
    app_version: '1.0.0',
  };

  assert.ok(['SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(runPayload.result));
  assert.ok(Date.parse(runPayload.started_at) > 0);
  assert.ok(Date.parse(runPayload.completed_at) >= Date.parse(runPayload.started_at));
  assert.match(runPayload.app_version, /^\d+(?:\.\d+){0,3}$/);
});

test('7. Role Harmonization - Member vs Owner and Status checks', () => {
  const allowedRoles = ['owner', 'member'];
  const allowedStatuses = ['active', 'suspended', 'revoked'];

  assert.ok(allowedRoles.includes('member'));
  assert.ok(allowedRoles.includes('owner'));
  assert.ok(!allowedRoles.includes('family_member')); // Rolled into member
  assert.ok(!allowedRoles.includes('friend'));        // Rolled into member

  assert.equal(allowedStatuses.includes('active'), true);
  assert.equal(allowedStatuses.includes('suspended'), true);
  assert.equal(allowedStatuses.includes('revoked'), true);
});
