import http from 'http';
import crypto from 'crypto';

function request(options: http.RequestOptions, body?: any): Promise<{ status: number; data: any }> {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let raw = '';
      res.on('data', (chunk) => (raw += chunk));
      res.on('end', () => {
        try {
          const parsed = JSON.parse(raw);
          resolve({ status: res.statusCode || 0, data: parsed });
        } catch {
          resolve({ status: res.statusCode || 0, data: raw });
        }
      });
    });
    req.on('error', reject);
    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

async function runTests() {
  console.log('=== STARTING MAHI REPAIR BACKEND VERIFICATION SUITE ===\n');
  let passed = 0;
  let failed = 0;

  function assert(condition: boolean, testName: string) {
    if (condition) {
      console.log(`[PASS] ${testName}`);
      passed++;
    } else {
      console.error(`[FAIL] ${testName}`);
      failed++;
    }
  }

  try {
    // 1. Healthcheck
    const health = await request({ host: 'localhost', port: 3000, path: '/api/v1/health', method: 'GET' });
    assert(health.status === 200 && health.data.status === 'healthy', 'Health endpoint returns 200 and healthy status');
    assert(health.data.firebase.project_id === 'manifest-hawk-76tvw', 'Health endpoint references Firebase staging project manifest-hawk-76tvw');

    // 2. Public Key Exposure
    const pubKeyRes = await request({ host: 'localhost', port: 3000, path: '/api/v1/public-key', method: 'GET' });
    assert(pubKeyRes.status === 200, 'Public key endpoint returns 200');
    assert(pubKeyRes.data.data.key_size === 3072, 'Public key algorithm is RSA-3072');
    assert(pubKeyRes.data.data.public_key_pem.includes('BEGIN PUBLIC KEY'), 'Public key is valid X.509 PEM');

    // 3. PIN Verification (Initial 6993)
    const pinRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/admin/pin/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer alex.owner@example.com' },
      },
      { pin: '6993' }
    );
    assert(pinRes.status === 200 && pinRes.data.data.verified === true, 'PIN 6993 verifies successfully with Owner account');
    assert(typeof pinRes.data.data.adminSessionToken === 'string', 'Admin session token issued upon PIN verification');

    // 4. Invalid PIN rejection
    const badPinRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/admin/pin/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer alex.owner@example.com' },
      },
      { pin: '0000' }
    );
    assert(badPinRes.status === 401 && badPinRes.data.error.code === 'INVALID_ADMIN_PIN', 'Invalid PIN is rejected with 401');

    // 5. Member cannot access Owner PIN verification
    const memberPinRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/admin/pin/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer member.test@example.com' },
      },
      { pin: '6993' }
    );
    assert(memberPinRes.status === 403, 'Member account is denied PIN unlock access (403 Owner Required)');

    // 6. Device Registration
    const devRegRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/devices/register',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer member.test@example.com' },
      },
      {
        device_label: 'Pixel 8 Test Device',
        app_version: '1.0.0',
        android_version: '15',
        android_api: 35,
      }
    );
    assert(devRegRes.status === 201 && devRegRes.data.data.status === 'registered', 'Device registers and receives server-issued UUID');
    const deviceId = devRegRes.data.data.device_id;

    // 7. Tool Publishing & Signature Verification
    const uniqueSlug = `storage-probe-${Date.now()}`;
    const createToolRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/tools',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer alex.owner@example.com' },
      },
      {
        name: 'Storage Cleaner & Probe',
        slug: uniqueSlug,
        category: 'storage',
        description: 'Asserts minimum internal storage availability and guides system settings cleanup.',
        draft_recipe: {
          schemaVersion: 1,
          toolId: uniqueSlug,
          title: 'Storage Cleaner & Probe',
          version: 1,
          category: 'storage',
          minAndroidApi: 23,
          minAppVersion: '1.0.0',
          estimatedSeconds: 60,
          steps: [
            { id: 's1', action: 'CHECK_STORAGE', params: { minimumAvailableMb: 500 }, risk: 'read_only', requiresConfirmation: false },
            { id: 's2', action: 'OPEN_ANDROID_SETTINGS', params: { screen: 'storage' }, risk: 'guided', requiresConfirmation: true },
          ],
        },
      }
    );
    assert(createToolRes.status === 201 && createToolRes.data.data.status === 'DRAFT', 'Owner can create new repair tool in DRAFT mode');
    const createdToolId = createToolRes.data.data.id;

    // 8. Publish Tool
    const publishRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: `/api/v1/tools/${createdToolId}/publish`,
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer alex.owner@example.com' },
      },
      { changelog: 'Production initial release' }
    );
    assert(publishRes.status === 200 && publishRes.data.data.published === true, 'Owner publishes tool with version increment');
    assert(publishRes.data.data.signature_algorithm === 'RSA-SHA256', 'Tool signed using RSA-SHA256');

    // 9. Fetch signed tool release and cryptographically verify signature with public key
    const signedToolRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/recipes/signed-tool',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer member.test@example.com' },
      },
      {
        tool_id: createdToolId,
        device_id: deviceId,
      }
    );
    assert(signedToolRes.status === 200, 'Member retrieves signed tool release');
    const { signed_payload, signature } = signedToolRes.data.data;

    // Verify detached RSA-3072 signature
    const verifier = crypto.createVerify('SHA256');
    verifier.update(signed_payload, 'utf-8');
    verifier.end();
    const isCryptoValid = verifier.verify(pubKeyRes.data.data.public_key_pem, signature, 'base64');
    assert(isCryptoValid === true, 'Detached RSA-3072 signature cryptographically verifies against server public key');

    // 10. Telemetry Submission
    const runRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/repair-runs',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer member.test@example.com' },
      },
      {
        device_id: deviceId,
        tool_id: createdToolId,
        tool_version: 1,
        started_at: new Date(Date.now() - 30000).toISOString(),
        completed_at: new Date().toISOString(),
        result: 'SUCCESS',
        step_results: [
          { step_id: 's1', action_name: 'CHECK_STORAGE', status: 'COMPLETED', duration_ms: 80 },
          { step_id: 's2', action_name: 'OPEN_ANDROID_SETTINGS', status: 'COMPLETED', duration_ms: 1200 },
        ],
        app_version: '1.0.0',
      }
    );
    assert(runRes.status === 201 && runRes.data.data.recorded === true, 'Repair run telemetry recorded successfully');

    // 11. Analytics Dashboard
    const analyticsRes = await request({
      host: 'localhost',
      port: 3000,
      path: '/api/v1/analytics/dashboard',
      method: 'GET',
      headers: { Authorization: 'Bearer alex.owner@example.com' },
    });
    assert(analyticsRes.status === 200, 'Owner dashboard analytics returns 200');
    assert(analyticsRes.data.data.total_runs >= 2, 'Total runs recorded in analytics');

    // 12. Gemini AI Admin Engine
    const geminiRes = await request(
      {
        host: 'localhost',
        port: 3000,
        path: '/api/v1/admin/gemini',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer alex.owner@example.com' },
      },
      { instruction: 'Show me the latest repair run reports and analytics' }
    );
    assert(geminiRes.status === 200, 'Gemini AI Admin engine responds with 200');
    assert(Array.isArray(geminiRes.data.data.executedTools), 'Gemini assistant returns structured function call executions');

    console.log(`\n=== VERIFICATION COMPLETE: ${passed} PASSED, ${failed} FAILED ===`);
    if (failed > 0) process.exit(1);
  } catch (e) {
    console.error('Test execution error:', e);
    process.exit(1);
  }
}

runTests();
