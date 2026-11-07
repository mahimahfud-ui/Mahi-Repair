import fs from 'fs';
import path from 'path';
import {
  UserProfile,
  DeviceRecord,
  RepairToolRecord,
  RepairToolVersionRecord,
  RepairToolAccessRecord,
  DeviceToolSyncRecord,
  RepairRunRecord,
  AdminAuditLogRecord,
  RecipeTestHistoryRecord,
  AdminPinConfig,
} from '../types';
import { createInitialPinConfig } from '../security/pin';
import { toCanonicalJson, signCanonicalPayload } from '../crypto/signer';

// Data storage abstraction supporting Cloud Firestore with resilient local persistence
class MemoryStagingDb {
  private dataFile: string;
  profiles: Map<string, UserProfile> = new Map();
  devices: Map<string, DeviceRecord> = new Map();
  tools: Map<string, RepairToolRecord> = new Map();
  toolVersions: Map<string, RepairToolVersionRecord> = new Map();
  toolAccess: Map<string, RepairToolAccessRecord> = new Map();
  deviceSyncs: Map<string, DeviceToolSyncRecord> = new Map();
  repairRuns: Map<string, RepairRunRecord> = new Map();
  auditLogs: AdminAuditLogRecord[] = [];
  testHistory: RecipeTestHistoryRecord[] = [];
  adminSettings: Map<string, AdminPinConfig> = new Map();

  constructor() {
    const secretsDir = path.join(process.cwd(), '.server-secrets');
    if (!fs.existsSync(secretsDir)) {
      fs.mkdirSync(secretsDir, { recursive: true, mode: 0o700 });
    }
    this.dataFile = path.join(secretsDir, 'staging_db_state.json');
    this.load();
    if (this.profiles.size === 0) {
      this.seedInitialData();
    }
  }

  private save(): void {
    try {
      const state = {
        profiles: Array.from(this.profiles.values()),
        devices: Array.from(this.devices.values()),
        tools: Array.from(this.tools.values()),
        toolVersions: Array.from(this.toolVersions.values()),
        toolAccess: Array.from(this.toolAccess.values()),
        deviceSyncs: Array.from(this.deviceSyncs.values()),
        repairRuns: Array.from(this.repairRuns.values()),
        auditLogs: this.auditLogs,
        testHistory: this.testHistory,
        adminSettings: Array.from(this.adminSettings.values()),
      };
      fs.writeFileSync(this.dataFile, JSON.stringify(state, null, 2), { mode: 0o600 });
    } catch {
      // Ignore file persistence errors in read-only runtimes
    }
  }

  private load(): void {
    try {
      if (fs.existsSync(this.dataFile)) {
        const raw = fs.readFileSync(this.dataFile, 'utf-8');
        const state = JSON.parse(raw);
        if (state.profiles) state.profiles.forEach((p: UserProfile) => this.profiles.set(p.id, p));
        if (state.devices) state.devices.forEach((d: DeviceRecord) => this.devices.set(d.id, d));
        if (state.tools) state.tools.forEach((t: RepairToolRecord) => this.tools.set(t.id, t));
        if (state.toolVersions) state.toolVersions.forEach((v: RepairToolVersionRecord) => this.toolVersions.set(v.id, v));
        if (state.toolAccess) state.toolAccess.forEach((a: RepairToolAccessRecord) => this.toolAccess.set(a.id, a));
        if (state.deviceSyncs) state.deviceSyncs.forEach((s: DeviceToolSyncRecord) => this.deviceSyncs.set(`${s.deviceId}_${s.toolId}`, s));
        if (state.repairRuns) state.repairRuns.forEach((r: RepairRunRecord) => this.repairRuns.set(r.id, r));
        if (state.auditLogs) this.auditLogs = state.auditLogs;
        if (state.testHistory) this.testHistory = state.testHistory;
        if (state.adminSettings) state.adminSettings.forEach((s: AdminPinConfig) => this.adminSettings.set(s.id, s));
      }
    } catch {
      // Start fresh
    }
  }

  private seedInitialData(): void {
    const now = new Date().toISOString();

    // Default Owner accounts
    const owner1: UserProfile = {
      id: 'owner_mahin',
      role: 'owner',
      status: 'active',
      email: 'mahinmhfd@gmail.com',
      displayName: 'Mahin (Owner)',
      createdAt: now,
      updatedAt: now,
    };
    const owner2: UserProfile = {
      id: 'owner_alex',
      role: 'owner',
      status: 'active',
      email: 'alex.owner@example.com',
      displayName: 'Alex Owner',
      createdAt: now,
      updatedAt: now,
    };
    const member1: UserProfile = {
      id: 'member_demo',
      role: 'member',
      status: 'active',
      email: 'member.test@example.com',
      displayName: 'Demo Member',
      createdAt: now,
      updatedAt: now,
    };

    this.profiles.set(owner1.id, owner1);
    this.profiles.set(owner2.id, owner2);
    this.profiles.set(member1.id, member1);

    // Initial PIN Config
    const initialPin = createInitialPinConfig(owner1.id);
    this.adminSettings.set(initialPin.id, initialPin);

    // Initial Demo Device
    const demoDevice: DeviceRecord = {
      id: 'DEMO-0001',
      userId: member1.id,
      deviceLabel: 'Pixel 8 (Demo)',
      appVersion: '1.0.0',
      androidVersion: '15',
      androidApi: 35,
      registeredAt: now,
      lastSeenAt: now,
    };
    this.devices.set(demoDevice.id, demoDevice);

    // Seed Verified Catalog Tools with Signed v1 Payloads
    const initialToolsData = [
      {
        id: 'tool_dns_repair',
        name: 'DNS Diagnostics & Repair',
        slug: 'dns-diagnostics-repair',
        category: 'network',
        description: 'Diagnoses domain lookup bottlenecks, verifies Cloudflare/Google DNS resolvers, and tests latency.',
        icon: 'globe',
        estimatedSeconds: 30,
        steps: [
          { id: 's1', action: 'CHECK_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false },
          { id: 's2', action: 'DNS_LOOKUP', params: { host: 'google.com' }, risk: 'read_only', requiresConfirmation: false },
          { id: 's3', action: 'HTTPS_PROBE', params: { host: '1.1.1.1', timeoutMs: 3000 }, risk: 'read_only', requiresConfirmation: false },
          { id: 's4', action: 'VERIFY_DNS', params: { host: 'cloudflare.com' }, risk: 'read_only', requiresConfirmation: false },
          { id: 's5', action: 'VERIFY_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false },
        ],
      },
      {
        id: 'tool_youtube_repair',
        name: 'YouTube Quick Repair',
        slug: 'youtube-quick-repair',
        category: 'apps',
        description: 'Checks YouTube app installation, probes connectivity, and guides storage cache cleaning.',
        icon: 'video',
        estimatedSeconds: 45,
        steps: [
          { id: 'yt1', action: 'CHECK_APP_INSTALLED', params: { packageName: 'com.google.android.youtube' }, risk: 'read_only', requiresConfirmation: false },
          { id: 'yt2', action: 'CHECK_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false },
          { id: 'yt3', action: 'HTTPS_PROBE', params: { host: 'youtube.com', timeoutMs: 5000 }, risk: 'read_only', requiresConfirmation: false },
          { id: 'yt4', action: 'OPEN_APP_DETAILS', params: { packageName: 'com.google.android.youtube' }, risk: 'guided', requiresConfirmation: true },
          { id: 'yt5', action: 'OPEN_APP', params: { packageName: 'com.google.android.youtube' }, risk: 'guided', requiresConfirmation: true },
        ],
      },
      {
        id: 'tool_wifi_repair',
        name: 'Wi-Fi Connection Assistant',
        slug: 'wifi-connection-assistant',
        category: 'connectivity',
        description: 'Analyzes Wi-Fi link parameters, gateway response, and navigates to Android network settings.',
        icon: 'wifi',
        estimatedSeconds: 40,
        steps: [
          { id: 'wf1', action: 'CHECK_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false },
          { id: 'wf2', action: 'DNS_LOOKUP', params: { host: 'connectivitycheck.gstatic.com' }, risk: 'read_only', requiresConfirmation: false },
          { id: 'wf3', action: 'OPEN_ANDROID_SETTINGS', params: { screen: 'wifi' }, risk: 'guided', requiresConfirmation: true },
          { id: 'wf4', action: 'VERIFY_INTERNET', params: {}, risk: 'read_only', requiresConfirmation: false },
        ],
      },
    ];

    for (const item of initialToolsData) {
      const draftRecipe = {
        schemaVersion: 1,
        toolId: item.slug,
        title: item.name,
        version: 1,
        category: item.category,
        minAndroidApi: 23,
        minAppVersion: '1.0.0',
        estimatedSeconds: item.estimatedSeconds,
        changelog: 'Initial production release v1',
        steps: item.steps,
      };

      const toolRecord: RepairToolRecord = {
        id: item.id,
        name: item.name,
        slug: item.slug,
        description: item.description,
        category: item.category,
        icon: item.icon,
        status: 'PUBLISHED',
        audience: 'all_active_members',
        version: 1,
        draftRecipe,
        androidCompatibility: { minApi: 23 },
        minimumAppVersion: '1.0.0',
        estimatedSeconds: item.estimatedSeconds,
        riskClassification: 'guided',
        createdBy: owner1.id,
        createdAt: now,
        updatedAt: now,
      };
      this.tools.set(toolRecord.id, toolRecord);

      // Sign canonical payload
      const canonical = toCanonicalJson(draftRecipe);
      const { signature, algorithm } = signCanonicalPayload(canonical);
      const versionRecord: RepairToolVersionRecord = {
        id: `ver_${item.id}_1`,
        toolId: toolRecord.id,
        version: 1,
        payload: draftRecipe,
        canonicalPayload: canonical,
        signature,
        signatureAlgorithm: algorithm,
        changelog: 'Initial signed staging release',
        publishedBy: owner1.id,
        publishedAt: now,
      };
      this.toolVersions.set(versionRecord.id, versionRecord);
    }

    this.save();
  }

  // Database helper methods
  async getProfile(idOrEmail: string): Promise<UserProfile | null> {
    for (const p of this.profiles.values()) {
      if (p.id === idOrEmail || p.email.toLowerCase() === idOrEmail.toLowerCase()) {
        return p;
      }
    }
    return null;
  }

  async saveProfile(profile: UserProfile): Promise<void> {
    this.profiles.set(profile.id, profile);
    this.save();
  }

  async getAllProfiles(): Promise<UserProfile[]> {
    return Array.from(this.profiles.values());
  }

  async getDevice(deviceId: string): Promise<DeviceRecord | null> {
    return this.devices.get(deviceId) || null;
  }

  async saveDevice(device: DeviceRecord): Promise<void> {
    this.devices.set(device.id, device);
    this.save();
  }

  async getUserDevices(userId: string): Promise<DeviceRecord[]> {
    return Array.from(this.devices.values()).filter((d) => d.userId === userId);
  }

  async getAllDevices(): Promise<DeviceRecord[]> {
    return Array.from(this.devices.values());
  }

  async getTool(toolIdOrSlug: string): Promise<RepairToolRecord | null> {
    for (const t of this.tools.values()) {
      if (t.id === toolIdOrSlug || t.slug === toolIdOrSlug) {
        return t;
      }
    }
    return null;
  }

  async saveTool(tool: RepairToolRecord): Promise<void> {
    this.tools.set(tool.id, tool);
    this.save();
  }

  async getAllTools(): Promise<RepairToolRecord[]> {
    return Array.from(this.tools.values());
  }

  async getToolVersion(toolId: string, version: number): Promise<RepairToolVersionRecord | null> {
    for (const v of this.toolVersions.values()) {
      if (v.toolId === toolId && v.version === version) {
        return v;
      }
    }
    return null;
  }

  async saveToolVersion(version: RepairToolVersionRecord): Promise<void> {
    this.toolVersions.set(version.id, version);
    this.save();
  }

  async getToolAccess(toolId: string): Promise<RepairToolAccessRecord[]> {
    return Array.from(this.toolAccess.values()).filter((a) => a.toolId === toolId);
  }

  async replaceToolAccess(toolId: string, memberIds: string[], assignedBy: string): Promise<void> {
    for (const [key, val] of this.toolAccess.entries()) {
      if (val.toolId === toolId) this.toolAccess.delete(key);
    }
    const now = new Date().toISOString();
    for (const mId of memberIds) {
      const id = `${toolId}_${mId}`;
      this.toolAccess.set(id, { id, toolId, memberId: mId, assignedBy, createdAt: now });
    }
    this.save();
  }

  async recordDeviceSync(deviceId: string, userId: string, toolId: string, version: number): Promise<void> {
    const key = `${deviceId}_${toolId}`;
    this.deviceSyncs.set(key, {
      deviceId,
      userId,
      toolId,
      version,
      syncedAt: new Date().toISOString(),
    });
    this.save();
  }

  async saveRepairRun(run: RepairRunRecord): Promise<void> {
    this.repairRuns.set(run.id, run);
    this.save();
  }

  async getRepairRuns(userId?: string): Promise<RepairRunRecord[]> {
    const all = Array.from(this.repairRuns.values()).sort(
      (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
    );
    if (userId) return all.filter((r) => r.userId === userId);
    return all;
  }

  async addAuditLog(entry: Omit<AdminAuditLogRecord, 'id' | 'createdAt'>): Promise<AdminAuditLogRecord> {
    const record: AdminAuditLogRecord = {
      ...entry,
      id: `audit_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      createdAt: new Date().toISOString(),
    };
    this.auditLogs.unshift(record);
    this.save();
    return record;
  }

  async getAuditLogs(limitCount = 100): Promise<AdminAuditLogRecord[]> {
    return this.auditLogs.slice(0, limitCount);
  }

  async getAdminPinConfig(): Promise<AdminPinConfig> {
    let cfg = this.adminSettings.get('pin_config');
    if (!cfg) {
      cfg = createInitialPinConfig();
      this.adminSettings.set('pin_config', cfg);
      this.save();
    }
    return cfg;
  }

  async saveAdminPinConfig(cfg: AdminPinConfig): Promise<void> {
    this.adminSettings.set('pin_config', cfg);
    this.save();
  }

  async getAnalytics(): Promise<Record<string, unknown>> {
    const runs = Array.from(this.repairRuns.values());
    const totalRuns = runs.length;
    const successCount = runs.filter((r) => r.result === 'SUCCESS').length;
    const partialCount = runs.filter((r) => r.result === 'PARTIAL').length;
    const failedCount = runs.filter((r) => r.result === 'FAILED').length;
    const cancelledCount = runs.filter((r) => r.result === 'CANCELLED').length;

    const activeMembers = Array.from(this.profiles.values()).filter(
      (p) => p.role === 'member' && p.status === 'active'
    ).length;
    const publishedTools = Array.from(this.tools.values()).filter((t) => t.status === 'PUBLISHED').length;

    // Tool run counts
    const toolRunMap = new Map<string, number>();
    for (const r of runs) {
      toolRunMap.set(r.toolId, (toolRunMap.get(r.toolId) || 0) + 1);
    }
    const mostUsedTools = Array.from(toolRunMap.entries())
      .map(([toolId, count]) => {
        const tool = this.tools.get(toolId);
        return { toolId, name: tool?.name || toolId, runs: count };
      })
      .sort((a, b) => b.runs - a.runs)
      .slice(0, 5);

    // Common failure counts
    const failureMap = new Map<string, number>();
    for (const r of runs) {
      if ((r.result === 'FAILED' || r.result === 'PARTIAL') && r.errorCode) {
        failureMap.set(r.errorCode, (failureMap.get(r.errorCode) || 0) + 1);
      }
    }
    const commonFailures = Array.from(failureMap.entries())
      .map(([code, count]) => ({ errorCode: code, occurrences: count }))
      .sort((a, b) => b.occurrences - a.occurrences)
      .slice(0, 5);

    return {
      total_runs: totalRuns,
      success: successCount,
      partial: partialCount,
      failed: failedCount,
      cancelled: cancelledCount,
      active_members: activeMembers,
      published_tools: publishedTools,
      most_used_tools: mostUsedTools,
      common_failures: commonFailures,
    };
  }
}

export const db = new MemoryStagingDb();
