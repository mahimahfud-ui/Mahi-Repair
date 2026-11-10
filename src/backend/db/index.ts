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
  RemoteSupportSessionRecord,
  RemoteCommandRecord,
} from '../types';
import { createInitialPinConfig } from '../security/pin';
import { toCanonicalJson, signCanonicalPayload } from '../crypto/signer';
import { categorizeToolsForDevice } from '../engine/compatibility';

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
  remoteSessions: Map<string, RemoteSupportSessionRecord> = new Map();
  remoteCommands: Map<string, RemoteCommandRecord> = new Map();

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
    const mahin = Array.from(this.profiles.values()).find((p) => p.email?.toLowerCase() === 'mahinmhfd@gmail.com');
    const now = new Date().toISOString();
    if (!mahin) {
      const owner0: UserProfile = {
        id: 'owner_mahinmhfd',
        role: 'owner',
        status: 'active',
        email: 'mahinmhfd@gmail.com',
        displayName: 'Mahi Mahfud (Owner)',
        createdAt: now,
        updatedAt: now,
      };
      this.profiles.set(owner0.id, owner0);
      this.save();
    } else if (mahin.role !== 'owner' || mahin.status !== 'active') {
      mahin.role = 'owner';
      mahin.status = 'active';
      this.profiles.set(mahin.id, mahin);
      this.save();
    }

    const mahimahfud = Array.from(this.profiles.values()).find((p) => p.email?.toLowerCase() === 'mahimahfud@gmail.com');
    if (!mahimahfud) {
      const member0: UserProfile = {
        id: 'member_mahimahfud',
        role: 'member',
        status: 'active',
        email: 'mahimahfud@gmail.com',
        displayName: 'Mahi Mahfud (Member)',
        createdAt: now,
        updatedAt: now,
      };
      this.profiles.set(member0.id, member0);
      this.save();
    } else if (mahimahfud.role !== 'member') {
      // Invariant: mahimahfud@gmail.com must strictly remain a member
      mahimahfud.role = 'member';
      this.profiles.set(mahimahfud.id, mahimahfud);
      this.save();
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
        remoteSessions: Array.from(this.remoteSessions.values()),
        remoteCommands: Array.from(this.remoteCommands.values()),
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
        if (state.remoteSessions) state.remoteSessions.forEach((s: RemoteSupportSessionRecord) => this.remoteSessions.set(s.id, s));
        if (state.remoteCommands) state.remoteCommands.forEach((c: RemoteCommandRecord) => this.remoteCommands.set(c.id, c));
      }
    } catch {
      // Start fresh
    }
  }

  private seedInitialData(): void {
    const now = new Date().toISOString();

    // Default Owner accounts
    const owner0: UserProfile = {
      id: 'owner_mahinmhfd',
      role: 'owner',
      status: 'active',
      email: 'mahinmhfd@gmail.com',
      displayName: 'Mahi Mahfud (Owner)',
      createdAt: now,
      updatedAt: now,
    };
    const owner1: UserProfile = {
      id: 'demo_owner',
      role: 'owner',
      status: 'active',
      email: 'demo.owner@example.test',
      displayName: 'Demo Owner',
      createdAt: now,
      updatedAt: now,
    };
    const owner2: UserProfile = {
      id: 'demo_owner_secondary',
      role: 'owner',
      status: 'active',
      email: 'secondary.demo.owner@example.test',
      displayName: 'Secondary Demo Owner',
      createdAt: now,
      updatedAt: now,
    };
    const member1: UserProfile = {
      id: 'demo_member',
      role: 'member',
      status: 'active',
      email: 'demo.member@example.test',
      displayName: 'Demo Member',
      createdAt: now,
      updatedAt: now,
    };

    this.profiles.set(owner0.id, owner0);
    this.profiles.set(owner1.id, owner1);
    this.profiles.set(owner2.id, owner2);
    this.profiles.set(member1.id, member1);

    // Initial PIN Config
    const initialPin = createInitialPinConfig(owner1.id);
    this.adminSettings.set(initialPin.id, initialPin);

    // Initial Demo Devices
    const demoDevice1: DeviceRecord = {
      id: 'DEMO-0001',
      userId: member1.id,
      deviceLabel: 'Pixel 8 Pro (Demo)',
      appInstallationId: 'fid_pixel8_pro_99a8b7',
      appVersion: '1.2.0',
      androidVersion: '15',
      androidApi: 35,
      registeredAt: now,
      lastSeenAt: now,
      profileVersion: 1,
      lastProfileUpdatedAt: now,
      hardwareProfile: {
        manufacturer: 'Google',
        brand: 'google',
        model: 'Pixel 8 Pro',
        commercialModelName: 'Pixel 8 Pro',
        modelIdentifier: 'GC3VE',
        productName: 'husky',
        deviceCodename: 'husky',
        hardware: 'zuma',
        socManufacturer: 'Google',
        socModel: 'Tensor G3',
        cpuAbis: ['arm64-v8a'],
        processorArchitecture: 'aarch64',
        cpuCoreCount: 9,
        androidRelease: '15',
        androidSdk: 35,
        securityPatchDate: '2024-10-05',
        buildDisplayInfo: 'AP3A.241005.015',
        screenWidthPx: 1344,
        screenHeightPx: 2992,
        densityDpi: 489,
        refreshRateHz: 120,
        supportedFeatures: [
          'android.hardware.camera',
          'android.hardware.camera.autofocus',
          'android.hardware.wifi',
          'android.hardware.bluetooth',
          'android.hardware.nfc',
          'android.hardware.biometrics',
          'android.hardware.sensor.accelerometer',
          'android.hardware.sensor.gyroscope',
        ],
        availableSensors: [
          { type: 1, name: 'LSM6DSO Accelerometer', vendor: 'STMicroelectronics' },
          { type: 4, name: 'LSM6DSO Gyroscope', vendor: 'STMicroelectronics' },
          { type: 5, name: 'TCS3718 Light Sensor', vendor: 'AMS' },
        ],
        cameraAvailable: true,
        profileConsentVersion: 1,
        collectedAt: now,
      },
      telemetry: {
        totalRamBytes: 12884901888, // 12GB
        availableRamBytes: 5905580032, // 5.5GB
        isLowMemory: false,
        internalStorageTotalBytes: 256000000000,
        internalStorageAvailableBytes: 168000000000,
        storagePressure: 'NORMAL',
        appSpecificStorageBytes: 42000000,
        batteryLevel: 82,
        chargingStatus: 'DISCHARGING',
        powerSource: 'BATTERY',
        batteryTemperature: 31.4,
        batteryHealth: 'GOOD',
        batteryCapacityEstimateMah: 5050,
        batterySaverActive: false,
        thermalStatus: 'NONE',
        networkConnectivity: 'CONNECTED',
        networkTransport: 'WIFI',
        internetValidated: true,
        dnsResolutionLatencyMs: 22,
        httpsProbeLatencyMs: 65,
        grantedPermissions: [
          'android.permission.INTERNET',
          'android.permission.ACCESS_NETWORK_STATE',
          'android.permission.ACCESS_WIFI_STATE',
          'android.permission.POST_NOTIFICATIONS',
        ],
        detectedRepairApps: [
          { packageName: 'com.google.android.youtube', installed: true, versionName: '19.42.34' },
          { packageName: 'com.facebook.katana', installed: false },
          { packageName: 'com.android.vending', installed: true, versionName: '43.2.19' },
        ],
        lastTelemetryUpdatedAt: now,
      },
      fieldAvailability: {
        commercialModelName: 'OBSERVED',
        socModel: 'OBSERVED',
        batteryHealth: 'OBSERVED',
        batteryTemperature: 'OBSERVED',
        totalRamBytes: 'OBSERVED',
        internalStorageAvailableBytes: 'OBSERVED',
      },
    };

    const demoDevice2: DeviceRecord = {
      id: 'DEMO-0002',
      userId: member1.id,
      deviceLabel: "Sarah's Galaxy S24 (Demo)",
      appInstallationId: 'fid_galaxy_s24_22c3d4',
      appVersion: '1.2.0',
      androidVersion: '14',
      androidApi: 34,
      registeredAt: now,
      lastSeenAt: now,
      profileVersion: 1,
      lastProfileUpdatedAt: now,
      hardwareProfile: {
        manufacturer: 'Samsung',
        brand: 'samsung',
        model: 'SM-S921B',
        commercialModelName: 'Galaxy S24',
        modelIdentifier: 'SM-S921B',
        productName: 'e1s',
        deviceCodename: 'e1s',
        hardware: 's5e9945',
        socManufacturer: 'Samsung',
        socModel: 'Exynos 2400',
        cpuAbis: ['arm64-v8a'],
        processorArchitecture: 'aarch64',
        cpuCoreCount: 10,
        androidRelease: '14',
        androidSdk: 34,
        securityPatchDate: '2024-09-01',
        buildDisplayInfo: 'UP1A.231005.007.S921BXXU3AXH7',
        screenWidthPx: 1080,
        screenHeightPx: 2340,
        densityDpi: 416,
        refreshRateHz: 120,
        supportedFeatures: [
          'android.hardware.camera',
          'android.hardware.wifi',
          'android.hardware.bluetooth',
          'android.hardware.nfc',
          'android.hardware.biometrics',
          'android.hardware.sensor.accelerometer',
        ],
        availableSensors: [
          { type: 1, name: 'LSM6DSO Accelerometer', vendor: 'STMicroelectronics' },
          { type: 4, name: 'LSM6DSO Gyroscope', vendor: 'STMicroelectronics' },
        ],
        cameraAvailable: true,
        profileConsentVersion: 1,
        collectedAt: now,
      },
      telemetry: {
        totalRamBytes: 8589934592, // 8GB
        availableRamBytes: 3124580000,
        isLowMemory: false,
        internalStorageTotalBytes: 128000000000,
        internalStorageAvailableBytes: 42000000000,
        storagePressure: 'NORMAL',
        batteryLevel: 65,
        chargingStatus: 'DISCHARGING',
        powerSource: 'BATTERY',
        batteryTemperature: 33.1,
        batteryHealth: 'GOOD',
        batteryCapacityEstimateMah: 4000,
        batterySaverActive: false,
        thermalStatus: 'NONE',
        networkConnectivity: 'CONNECTED',
        networkTransport: 'WIFI',
        internetValidated: true,
        dnsResolutionLatencyMs: 28,
        httpsProbeLatencyMs: 74,
        grantedPermissions: [
          'android.permission.INTERNET',
          'android.permission.ACCESS_NETWORK_STATE',
          'android.permission.ACCESS_WIFI_STATE',
        ],
        detectedRepairApps: [
          { packageName: 'com.google.android.youtube', installed: true, versionName: '19.40.38' },
        ],
        lastTelemetryUpdatedAt: now,
      },
      fieldAvailability: {
        commercialModelName: 'OBSERVED',
        socModel: 'OBSERVED',
        batteryHealth: 'OBSERVED',
      },
    };

    this.devices.set(demoDevice1.id, demoDevice1);
    this.devices.set(demoDevice2.id, demoDevice2);

    // Seed Verified Catalog Tools with Signed v1 Payloads and Compatibility Metadata
    const initialToolsData = [
      {
        id: 'tool_dns_repair',
        name: 'DNS Diagnostics & Repair',
        slug: 'dns-diagnostics-repair',
        category: 'network',
        description: 'Diagnoses domain lookup bottlenecks, verifies Cloudflare/Google DNS resolvers, and tests latency.',
        icon: 'globe',
        estimatedSeconds: 30,
        compatibilityMetadata: {
          minimumAndroidSdk: 23,
          supportedActionIds: ['CHECK_INTERNET', 'DNS_LOOKUP', 'HTTPS_PROBE', 'VERIFY_DNS', 'VERIFY_INTERNET'],
          networkPrerequisites: { requiresInternet: true },
          riskLevel: 'read_only' as const,
          userConfirmationRequired: false,
          supportedRecipeVersions: [1],
          rollbackOrRecoveryInstructions: 'Diagnostic read-only operation; safe to retry.',
        },
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
        compatibilityMetadata: {
          minimumAndroidSdk: 24,
          supportedActionIds: ['CHECK_APP_INSTALLED', 'CHECK_INTERNET', 'HTTPS_PROBE', 'OPEN_APP_DETAILS', 'OPEN_APP'],
          riskLevel: 'guided' as const,
          userConfirmationRequired: true,
          supportedRecipeVersions: [1],
          knownLimitations: ['Requires YouTube app installed or guidance to install from Google Play.'],
        },
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
        compatibilityMetadata: {
          minimumAndroidSdk: 23,
          supportedActionIds: ['CHECK_INTERNET', 'DNS_LOOKUP', 'OPEN_ANDROID_SETTINGS', 'VERIFY_INTERNET'],
          networkPrerequisites: { requiresWifi: true },
          riskLevel: 'guided' as const,
          userConfirmationRequired: true,
          supportedRecipeVersions: [1],
        },
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
        minAndroidApi: item.compatibilityMetadata.minimumAndroidSdk,
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
        androidCompatibility: { minApi: item.compatibilityMetadata.minimumAndroidSdk },
        compatibilityMetadata: item.compatibilityMetadata,
        minimumAppVersion: '1.0.0',
        estimatedSeconds: item.estimatedSeconds,
        riskClassification: item.compatibilityMetadata.riskLevel,
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

  async getToolVersions(toolId?: string, limitCount = 50): Promise<RepairToolVersionRecord[]> {
    let list = Array.from(this.toolVersions.values());
    if (toolId) {
      list = list.filter((v) => v.toolId === toolId);
    }
    list.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
    return list.slice(0, limitCount);
  }

  async saveToolVersion(version: RepairToolVersionRecord): Promise<void> {
    this.toolVersions.set(version.id, version);
    this.save();
  }

  async getToolAccess(toolId: string): Promise<RepairToolAccessRecord[]> {
    return Array.from(this.toolAccess.values()).filter((a) => a.toolId === toolId);
  }

  async getAllToolAssignments(toolId?: string): Promise<RepairToolAccessRecord[]> {
    const all = Array.from(this.toolAccess.values());
    if (toolId) return all.filter((a) => a.toolId === toolId);
    return all;
  }

  async deleteDraftTool(toolId: string): Promise<{ deleted: boolean; reason?: string }> {
    const tool = await this.getTool(toolId);
    if (!tool) {
      return { deleted: false, reason: 'Tool not found' };
    }
    if (tool.status !== 'DRAFT' || tool.version > 0) {
      return { deleted: false, reason: 'Only unreleased DRAFT tools with no immutable versions can be deleted' };
    }
    this.tools.delete(tool.id);
    for (const [key, val] of this.toolAccess.entries()) {
      if (val.toolId === tool.id) this.toolAccess.delete(key);
    }
    this.save();
    return { deleted: true };
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

  async getRepairRun(runId: string): Promise<RepairRunRecord | null> {
    return this.repairRuns.get(runId) || null;
  }

  async getRepairRuns(userId?: string): Promise<RepairRunRecord[]> {
    const all = Array.from(this.repairRuns.values()).sort(
      (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
    );
    if (userId) return all.filter((r) => r.userId === userId);
    return all;
  }

  async getRecipeTests(limitCount = 50): Promise<RecipeTestHistoryRecord[]> {
    return this.testHistory.slice(-limitCount).reverse();
  }

  async saveRecipeTest(record: RecipeTestHistoryRecord): Promise<void> {
    this.testHistory.push(record);
    if (this.testHistory.length > 200) {
      this.testHistory = this.testHistory.slice(-200);
    }
    this.save();
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
      totalRuns,
      total_runs: totalRuns,
      success: successCount,
      partial: partialCount,
      failed: failedCount,
      cancelled: cancelledCount,
      activeMembersCount: activeMembers,
      active_members: activeMembers,
      publishedTools,
      published_tools: publishedTools,
      mostUsedTools,
      most_used_tools: mostUsedTools,
      commonFailures,
      common_failures: commonFailures,
    };
  }

  // Remote Support Session Methods
  async createRemoteSession(deviceId: string, ownerId: string): Promise<RemoteSupportSessionRecord> {
    const now = new Date();
    const sessionId = `sess_${now.getTime()}_${Math.random().toString(36).slice(2, 8)}`;
    // Generate human-friendly 6-digit pairing code
    const pairingCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(now.getTime() + 15 * 60 * 1000).toISOString();

    const session: RemoteSupportSessionRecord = {
      id: sessionId,
      deviceId,
      ownerId,
      pairingCode,
      status: 'WAITING_FOR_DEVICE',
      createdAt: now.toISOString(),
      expiresAt,
    };

    this.remoteSessions.set(session.id, session);
    // Mark on device
    const device = this.devices.get(deviceId);
    if (device) {
      device.activeSessionId = session.id;
      this.devices.set(device.id, device);
    }
    this.save();
    return session;
  }

  async getRemoteSession(sessionId: string): Promise<RemoteSupportSessionRecord | null> {
    return this.remoteSessions.get(sessionId) || null;
  }

  async getRemoteSessionByPairingCode(code: string): Promise<RemoteSupportSessionRecord | null> {
    const cleanCode = code.trim();
    for (const s of this.remoteSessions.values()) {
      if (s.pairingCode === cleanCode && s.status !== 'TERMINATED' && new Date(s.expiresAt).getTime() > Date.now()) {
        return s;
      }
    }
    return null;
  }

  async getActiveSessionForDevice(deviceId: string): Promise<RemoteSupportSessionRecord | null> {
    for (const s of this.remoteSessions.values()) {
      if (s.deviceId === deviceId && s.status !== 'TERMINATED' && new Date(s.expiresAt).getTime() > Date.now()) {
        return s;
      }
    }
    return null;
  }

  async saveRemoteSession(session: RemoteSupportSessionRecord): Promise<void> {
    this.remoteSessions.set(session.id, session);
    this.save();
  }

  async addRemoteCommand(command: RemoteCommandRecord): Promise<void> {
    this.remoteCommands.set(command.id, command);
    this.save();
  }

  async getRemoteCommands(sessionId: string): Promise<RemoteCommandRecord[]> {
    return Array.from(this.remoteCommands.values())
      .filter((c) => c.sessionId === sessionId)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  }

  async updateRemoteCommand(command: RemoteCommandRecord): Promise<void> {
    this.remoteCommands.set(command.id, command);
    this.save();
  }

  async getDeviceCompatibility(deviceId: string) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const tools = await this.getAllTools();
    const categorized = categorizeToolsForDevice(device, tools);
    return { device, categorized };
  }
}

export const db = new MemoryStagingDb();
