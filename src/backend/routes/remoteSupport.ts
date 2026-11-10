import { Router, Response } from 'express';
import { AuthenticatedRequest, authenticateToken, requireOwner } from '../security/auth';
import { db } from '../db';
import { z } from 'zod';
import crypto from 'crypto';
import { signCanonicalPayload, toCanonicalJson } from '../crypto/signer';
import {
  ActionDefinitions,
  ActionRiskMap,
  ConfirmationRequiredActions,
  SupportedActionName,
  RemoteSupportSessionRecord,
  RemoteCommandRecord,
} from '../types';

export const remoteSupportRouter = Router();

async function getSessionAccess(session: RemoteSupportSessionRecord, user: { id: string; role: string }) {
  const device = await db.getDevice(session.deviceId);
  return {
    isOwner: user.role === 'owner' && user.id === session.ownerId,
    isDeviceUser: Boolean(device && device.userId === user.id),
  };
}

// POST /api/v1/remote-support/sessions - Owner creates a remote support session
const CreateSessionSchema = z.object({
  device_id: z.string().min(1).max(128),
}).strict();

remoteSupportRouter.post('/sessions', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const parsed = CreateSessionSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_REQUEST', issues: parsed.error.issues } });
    return;
  }

  const device = await db.getDevice(parsed.data.device_id);
  if (!device) {
    res.status(404).json({ error: { code: 'DEVICE_NOT_FOUND', message: 'Target device not found.' } });
    return;
  }
  if (device.revokedAt) {
    res.status(403).json({ error: { code: 'DEVICE_REVOKED', message: 'Cannot support a revoked device.' } });
    return;
  }

  const session = await db.createRemoteSession(device.id, req.user!.id);

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'remote_session.created',
    targetType: 'remote_session',
    targetId: session.id,
    details: { deviceId: device.id },
  });

  res.status(201).json({
    data: {
      session_id: session.id,
      device_id: device.id,
      pairing_code: session.pairingCode,
      qr_payload: JSON.stringify({
        sessionId: session.id,
        pairingCode: session.pairingCode,
        serverUrl: process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000',
        deviceId: device.id,
      }),
      status: session.status,
      expires_at: session.expiresAt,
    },
  });
});

// GET /api/v1/remote-support/sessions/:id - Only Owner and paired Member may inspect session state
remoteSupportRouter.get('/sessions/:id', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);
  if (!session) {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Session not found.' } });
    return;
  }
  const access = await getSessionAccess(session, req.user!);
  if (!access.isOwner && !access.isDeviceUser) {
    res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Unauthorized session inspection.' } });
    return;
  }
  res.json({ data: session });
});

// POST /api/v1/remote-support/sessions/:id/approve - Paired Member explicitly approves on Android device
remoteSupportRouter.post('/sessions/:id/approve', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);
  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }
  const access = await getSessionAccess(session, req.user!);
  if (!access.isDeviceUser) {
    res.status(403).json({ error: { code: 'DEVICE_FORBIDDEN', message: 'Only paired device user can approve remote support.' } });
    return;
  }
  session.status = 'DEVICE_CONNECTED';
  session.connectedAt = new Date().toISOString();
  await db.saveRemoteSession(session);
  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'remote_session.approved',
    targetType: 'remote_session',
    targetId: session.id,
    details: { deviceId: session.deviceId },
  });
  res.json({ data: { session_id: session.id, status: session.status, connected_at: session.connectedAt } });
});

// POST /api/v1/remote-support/join - Android phone joins with pairing code or session ID
const JoinSessionSchema = z.object({
  pairing_code: z.string().min(4).max(16).optional(),
  session_id: z.string().max(128).optional(),
  device_id: z.string().min(1).max(128),
}).strict();

remoteSupportRouter.post('/join', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const user = req.user!;
  const parsed = JoinSessionSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_REQUEST', issues: parsed.error.issues } });
    return;
  }

  let session: RemoteSupportSessionRecord | null = null;
  if (parsed.data.session_id) {
    session = await db.getRemoteSession(parsed.data.session_id);
  } else if (parsed.data.pairing_code) {
    session = await db.getRemoteSessionByPairingCode(parsed.data.pairing_code);
  }

  if (!session) {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Invalid or expired remote support session.' } });
    return;
  }

  if (session.status === 'TERMINATED') {
    res.status(410).json({ error: { code: 'SESSION_TERMINATED', message: 'This remote support session has ended.' } });
    return;
  }

  if (new Date(session.expiresAt).getTime() < Date.now()) {
    session.status = 'TERMINATED';
    await db.saveRemoteSession(session);
    res.status(410).json({ error: { code: 'SESSION_EXPIRED', message: 'This pairing session has expired.' } });
    return;
  }

  // Verify device identity
  const device = await db.getDevice(parsed.data.device_id);
  if (!device || device.userId !== user.id) {
    res.status(403).json({ error: { code: 'DEVICE_UNAUTHORIZED', message: 'Device authorization mismatch.' } });
    return;
  }

  session.status = 'DEVICE_CONNECTED';
  session.connectedAt = new Date().toISOString();
  await db.saveRemoteSession(session);

  res.json({
    data: {
      session_id: session.id,
      device_id: session.deviceId,
      owner_id: session.ownerId,
      status: session.status,
      connected_at: session.connectedAt,
    },
  });
});

// POST /api/v1/remote-support/:id/signal - Send WebRTC Offer, Answer, or ICE Candidate
const SignalSchema = z.object({
  type: z.enum(['offer', 'answer', 'ice-candidate', 'ping']),
  sdp: z.string().max(100_000).optional(),
  candidate: z.string().max(4_000).optional(),
  sdpMid: z.string().max(100).optional(),
  sdpMLineIndex: z.number().int().min(0).max(255).optional(),
}).strict().superRefine((value, context) => {
  if ((value.type === 'offer' || value.type === 'answer') && !value.sdp) {
    context.addIssue({ code: 'custom', path: ['sdp'], message: 'SDP is required for offers and answers.' });
  }
  if (value.type === 'ice-candidate' && !value.candidate) {
    context.addIssue({ code: 'custom', path: ['candidate'], message: 'ICE candidate is required.' });
  }
});

remoteSupportRouter.post(['/:id/signal', '/sessions/:id/signal'], authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }

  const parsed = SignalSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_SIGNAL_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const user = req.user!;
  const access = await getSessionAccess(session, user);
  if (!access.isOwner && !access.isDeviceUser) {
    res.status(403).json({ error: { code: 'SESSION_FORBIDDEN', message: 'This account is not a participant in the support session.' } });
    return;
  }
  const fromRole = access.isOwner ? 'owner' : 'device';
  const candidates = session.iceCandidates ?? [];

  if (parsed.data.type === 'offer') {
    session.webrtcOffer = parsed.data.sdp;
  } else if (parsed.data.type === 'answer') {
    session.webrtcAnswer = parsed.data.sdp;
    session.status = 'SCREEN_SHARING';
  } else if (parsed.data.type === 'ice-candidate' && parsed.data.candidate) {
    candidates.push({
      candidate: parsed.data.candidate,
      sdpMid: parsed.data.sdpMid,
      sdpMLineIndex: parsed.data.sdpMLineIndex,
      from: fromRole,
    });
    session.iceCandidates = candidates.slice(-256);
  }

  await db.saveRemoteSession(session);
  res.json({ data: { received: true, timestamp: Date.now() } });
});

// GET /api/v1/remote-support/:id/signal - Retrieve peer WebRTC signals
remoteSupportRouter.get(['/:id/signal', '/sessions/:id/signal'], authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }

  const user = req.user!;
  const access = await getSessionAccess(session, user);
  if (!access.isOwner && !access.isDeviceUser) {
    res.status(403).json({ error: { code: 'SESSION_FORBIDDEN', message: 'This account is not a participant in the support session.' } });
    return;
  }
  const myRole = access.isOwner ? 'owner' : 'device';
  const peerCandidates = (session.iceCandidates ?? []).filter((candidate) => candidate.from !== myRole).slice(-128);
  const peerSdp = myRole === 'owner' ? session.webrtcAnswer : session.webrtcOffer;

  res.json({
    data: {
      session_id: session.id,
      status: session.status,
      peer_sdp: peerSdp || null,
      peer_candidates: peerCandidates,
    },
  });
});

// POST /api/v1/remote-support/:id/screen-share-status - Phone updates screen share status
const ScreenShareStatusSchema = z.object({
  active: z.boolean(),
  error: z.string().optional(),
}).strict();

remoteSupportRouter.post('/:id/screen-share-status', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }

  const screenDevice = await db.getDevice(session.deviceId);
  if (!screenDevice || screenDevice.userId !== req.user!.id) {
    res.status(403).json({ error: { code: 'DEVICE_FORBIDDEN', message: 'Only the paired device owner can update screen-sharing status.' } });
    return;
  }

  const parsed = ScreenShareStatusSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  if (parsed.data.active) {
    session.status = 'SCREEN_SHARING';
  } else {
    session.status = 'DEVICE_CONNECTED';
  }
  await db.saveRemoteSession(session);

  res.json({ data: { session_id: session.id, status: session.status } });
});

// POST /api/v1/remote-support/:id/command - Owner sends safe allowlisted command to Android phone
const RemoteCommandInputSchema = z.object({
  action: z.string(),
  params: z.record(z.string(), z.unknown()).default({}),
}).strict();

remoteSupportRouter.post('/:id/command', authenticateToken, requireOwner, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }

  if (session.status !== 'DEVICE_CONNECTED' && session.status !== 'SCREEN_SHARING') {
    res.status(409).json({ error: { code: 'DEVICE_NOT_CONNECTED', message: 'The device must join and approve the support session before commands can be dispatched.' } });
    return;
  }

  if (session.ownerId !== req.user!.id) {
    res.status(403).json({ error: { code: 'SESSION_FORBIDDEN', message: 'Only the Owner who created this session can dispatch commands.' } });
    return;
  }

  const parsed = RemoteCommandInputSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_COMMAND', issues: parsed.error.issues } });
    return;
  }

  const actionName = parsed.data.action as SupportedActionName;
  if (!Object.prototype.hasOwnProperty.call(ActionDefinitions, actionName)) {
    res.status(400).json({
      error: {
        code: 'ACTION_NOT_ALLOWLISTED',
        message: `Action "${parsed.data.action}" is not supported. Must be one of the 13 allowable declarative Android actions.`,
      },
    });
    return;
  }

  // Validate parameters against strict declarative schema
  const paramCheck = ActionDefinitions[actionName].safeParse(parsed.data.params);
  if (!paramCheck.success) {
    res.status(422).json({
      error: {
        code: 'INVALID_ACTION_PARAMS',
        issues: paramCheck.error.issues,
      },
    });
    return;
  }

  const actionRisk = ActionRiskMap[actionName];
  if (actionRisk !== 'read_only' || ConfirmationRequiredActions.has(actionName)) {
    res.status(409).json({
      error: {
        code: 'FOREGROUND_USER_CONFIRMATION_REQUIRED',
        message: 'This action requires a foreground confirmation flow on the Android device and cannot be executed by the background remote service.',
        risk: actionRisk,
      },
    });
    return;
  }

  const now = new Date();
  const commandId = `cmd_${crypto.randomUUID()}`;
  const nonce = crypto.randomBytes(16).toString('hex');
  const expiresAt = new Date(now.getTime() + 5 * 60 * 1000).toISOString(); // 5-minute expiry

  // Cryptographically sign the command with RSA-3072 private key
  const commandPayload = {
    commandId,
    sessionId: session.id,
    deviceId: session.deviceId,
    action: actionName,
    params: parsed.data.params,
    nonce,
    expiresAt,
    issuedAt: now.toISOString(),
  };

  const canonical = toCanonicalJson(commandPayload);
  const { signature } = signCanonicalPayload(canonical);

  const commandRecord: RemoteCommandRecord = {
    id: commandId,
    sessionId: session.id,
    deviceId: session.deviceId,
    action: actionName,
    params: parsed.data.params,
    signature,
    canonicalPayload: canonical,
    nonce,
    expiresAt,
    status: 'PENDING',
    createdAt: now.toISOString(),
  };

  await db.addRemoteCommand(commandRecord);
  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'remote_command.dispatched',
    targetType: 'remote_command',
    targetId: commandId,
    details: { action: actionName, sessionId: session.id, deviceId: session.deviceId },
  });

  res.status(201).json({
    data: {
      command: commandRecord,
      canonical_payload: canonical,
    },
  });
});

// GET /api/v1/remote-support/:id/commands - Phone retrieves pending commands
remoteSupportRouter.get('/:id/commands', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session || session.status === 'TERMINATED') {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Active session not found.' } });
    return;
  }

  const access = await getSessionAccess(session, req.user!);
  if (!access.isDeviceUser) {
    res.status(403).json({ error: { code: 'DEVICE_FORBIDDEN', message: 'Only the paired device owner can retrieve commands.' } });
    return;
  }

  const commands = (await db.getRemoteCommands(sessionId)).filter((command) => command.status === 'PENDING');
  res.json({ data: commands });
});

// POST /api/v1/remote-support/:id/command-result - Phone submits step outcome
const CommandResultSchema = z.object({
  command_id: z.string().min(1).max(128),
  status: z.enum(['COMPLETED', 'FAILED', 'REJECTED', 'WAITING_USER']),
  result: z.unknown().optional(),
  error: z.string().max(500).optional(),
}).strict();

remoteSupportRouter.post('/:id/command-result', authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session) {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Session not found.' } });
    return;
  }

  const access = await getSessionAccess(session, req.user!);
  if (!access.isDeviceUser) {
    res.status(403).json({ error: { code: 'DEVICE_FORBIDDEN', message: 'Only the paired device owner can submit command results.' } });
    return;
  }

  const parsed = CommandResultSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(422).json({ error: { code: 'INVALID_RESULT_PAYLOAD', issues: parsed.error.issues } });
    return;
  }

  const commands = await db.getRemoteCommands(sessionId);
  const cmd = commands.find((c) => c.id === parsed.data.command_id);
  if (!cmd) {
    res.status(404).json({ error: { code: 'COMMAND_NOT_FOUND', message: 'Command not found.' } });
    return;
  }

  if (cmd.deviceId !== session.deviceId || cmd.status !== 'PENDING') {
    res.status(409).json({ error: { code: 'COMMAND_NOT_PENDING', message: 'The command is not pending for this device.' } });
    return;
  }

  cmd.status = parsed.data.status;
  cmd.result = parsed.data.result;
  cmd.error = parsed.data.error;
  cmd.executedAt = new Date().toISOString();
  await db.updateRemoteCommand(cmd);

  res.json({ data: { success: true, command_id: cmd.id, status: cmd.status } });
});

// POST /api/v1/remote-support/:id/terminate - Immediately terminate remote support session
remoteSupportRouter.post(['/:id/terminate', '/sessions/:id/terminate'], authenticateToken, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const sessionId = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const session = await db.getRemoteSession(sessionId);

  if (!session) {
    res.status(404).json({ error: { code: 'SESSION_NOT_FOUND', message: 'Session not found.' } });
    return;
  }

  const access = await getSessionAccess(session, req.user!);
  if (!access.isOwner && !access.isDeviceUser) {
    res.status(403).json({ error: { code: 'SESSION_FORBIDDEN', message: 'This account is not a participant in the support session.' } });
    return;
  }

  session.status = 'TERMINATED';
  session.terminatedAt = new Date().toISOString();
  session.terminatedBy = req.user!.id;
  await db.saveRemoteSession(session);

  // Unlink device active session
  const device = await db.getDevice(session.deviceId);
  if (device && device.activeSessionId === session.id) {
    device.activeSessionId = null;
    await db.saveDevice(device);
  }

  await db.addAuditLog({
    actorId: req.user!.id,
    action: 'remote_session.terminated',
    targetType: 'remote_session',
    targetId: session.id,
    details: { terminatedBy: req.user!.id },
  });

  res.json({
    data: {
      session_id: session.id,
      status: 'TERMINATED',
      terminated_at: session.terminatedAt,
    },
  });
});
