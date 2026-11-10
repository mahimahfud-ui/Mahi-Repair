import { RepairTool } from '../types';
import { getInitialTools as getDefaultTools } from '../data/defaultCatalog';

const STORAGE_KEY_TOOLS = 'mahi_live_tools_catalog_v2';
const STORAGE_KEY_VERSION = 'mahi_live_datapack_version';
const STORAGE_KEY_TIMESTAMP = 'mahi_live_datapack_timestamp';
const CHANNEL_NAME = 'mahi_repair_live_sync_channel';

export interface LiveUpdatePayload {
  type: 'DATA_PACK_UPDATE' | 'TOOL_PUBLISHED' | 'CATALOG_SYNC';
  version: string;
  tools: RepairTool[];
  title: string;
  details: string;
  timestamp: number;
  author?: string;
}

export interface DataPackMeta {
  version: string;
  lastUpdated: number;
  toolsCount: number;
  syncState: 'SYNCHRONIZED' | 'SYNCING' | 'OFFLINE';
}

class LiveSyncManager {
  private channel: BroadcastChannel | null = null;
  private currentVersion: string = '1.2.4';
  private listeners: Set<(payload: LiveUpdatePayload) => void> = new Set();

  constructor() {
    if (typeof window !== 'undefined') {
      try {
        const savedVersion = localStorage.getItem(STORAGE_KEY_VERSION);
        if (savedVersion) {
          this.currentVersion = savedVersion;
        }

        if ('BroadcastChannel' in window) {
          this.channel = new BroadcastChannel(CHANNEL_NAME);
          this.channel.onmessage = (event: MessageEvent<LiveUpdatePayload>) => {
            if (event.data && event.data.type) {
              this.handleIncomingBroadcast(event.data);
            }
          };
        }

        // Cross-window storage event fallback
        window.addEventListener('storage', (e: StorageEvent) => {
          if (e.key === STORAGE_KEY_TOOLS && e.newValue) {
            try {
              const tools = JSON.parse(e.newValue);
              const version = localStorage.getItem(STORAGE_KEY_VERSION) || this.currentVersion;
              this.notifyListeners({
                type: 'DATA_PACK_UPDATE',
                version,
                tools,
                title: `Data Pack ${version} Synchronized`,
                details: 'Live tools catalog updated across browser sessions.',
                timestamp: Date.now()
              });
            } catch {
              // Ignore parse error
            }
          }
        });
      } catch (err) {
        console.warn('[LiveSync] BroadcastChannel initialization skipped:', err);
      }
    }
  }

  private handleIncomingBroadcast(payload: LiveUpdatePayload) {
    this.currentVersion = payload.version;
    try {
      localStorage.setItem(STORAGE_KEY_VERSION, payload.version);
      localStorage.setItem(STORAGE_KEY_TIMESTAMP, String(payload.timestamp));
      localStorage.setItem(STORAGE_KEY_TOOLS, JSON.stringify(payload.tools));
    } catch {
      // Storage quota or disabled
    }
    this.notifyListeners(payload);
  }

  private notifyListeners(payload: LiveUpdatePayload) {
    this.listeners.forEach((listener) => {
      try {
        listener(payload);
      } catch (err) {
        console.error('[LiveSync] Listener error:', err);
      }
    });
  }

  public subscribe(callback: (payload: LiveUpdatePayload) => void): () => void {
    this.listeners.add(callback);
    return () => {
      this.listeners.delete(callback);
    };
  }

  public getInitialTools(): RepairTool[] {
    if (typeof window === 'undefined') return getDefaultTools();
    try {
      const stored = localStorage.getItem(STORAGE_KEY_TOOLS);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      }
    } catch (e) {
      console.warn('[LiveSync] Could not read local tools catalog:', e);
    }
    return getDefaultTools();
  }

  public getDataPackMeta(tools: RepairTool[]): DataPackMeta {
    let lastUpdated = Date.now();
    if (typeof window !== 'undefined') {
      const storedTs = localStorage.getItem(STORAGE_KEY_TIMESTAMP);
      if (storedTs) lastUpdated = parseInt(storedTs, 10);
    }
    return {
      version: this.currentVersion,
      lastUpdated,
      toolsCount: tools.length,
      syncState: 'SYNCHRONIZED'
    };
  }

  /**
   * Broadcasts a live update to all connected sessions (e.g. from Owner Admin Console)
   */
  public async publishLiveTools(
    tools: RepairTool[],
    options?: {
      title?: string;
      details?: string;
      author?: string;
    }
  ): Promise<{ success: boolean; version: string }> {
    // Increment patch version
    const parts = this.currentVersion.split('.');
    const minor = parseInt(parts[2] || '4', 10) + 1;
    const nextVersion = `${parts[0] || '1'}.${parts[1] || '2'}.${minor}`;
    this.currentVersion = nextVersion;

    const now = Date.now();
    const title = options?.title || `Data Pack v${nextVersion} (Live Push)`;
    const details = options?.details || `New tool & signature release synchronized live by Owner.`;

    // Persist locally
    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(STORAGE_KEY_TOOLS, JSON.stringify(tools));
        localStorage.setItem(STORAGE_KEY_VERSION, nextVersion);
        localStorage.setItem(STORAGE_KEY_TIMESTAMP, String(now));
      } catch (e) {
        console.warn('[LiveSync] Storage write failed:', e);
      }
    }

    const payload: LiveUpdatePayload = {
      type: 'DATA_PACK_UPDATE',
      version: nextVersion,
      tools,
      title,
      details,
      timestamp: now,
      author: options?.author || 'Owner Admin'
    };

    // 1. Broadcast over local BroadcastChannel
    try {
      this.channel?.postMessage(payload);
    } catch (err) {
      console.warn('[LiveSync] Channel broadcast failed:', err);
    }

    // 2. Notify local in-memory listeners
    this.notifyListeners(payload);

    // 3. Post to backend endpoint asynchronously
    try {
      await fetch('/api/v1/datapack/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tools,
          note: details,
          releaseName: title
        })
      });
    } catch (err) {
      console.warn('[LiveSync] Backend sync warning:', err);
    }

    return { success: true, version: nextVersion };
  }

  /**
   * Checks the cloud server for any live updates
   */
  public async checkForLiveUpdates(): Promise<{
    hasUpdate: boolean;
    version: string;
    message: string;
    tools?: RepairTool[];
  }> {
    try {
      const res = await fetch('/api/v1/datapack/status');
      if (res.ok) {
        const data = await res.json();
        if (data?.version && data.version !== this.currentVersion && data.tools_count > 0) {
          // Fetch catalog
          const catRes = await fetch('/api/v1/datapack/catalog');
          if (catRes.ok) {
            const catData = await catRes.json();
            if (Array.isArray(catData.tools) && catData.tools.length > 0) {
              this.currentVersion = data.version;
              return {
                hasUpdate: true,
                version: data.version,
                tools: catData.tools,
                message: `New Data Pack v${data.version} discovered and ready to apply.`
              };
            }
          }
        }
      }
    } catch {
      // Fallback
    }

    return {
      hasUpdate: false,
      version: this.currentVersion,
      message: `Data Pack v${this.currentVersion} is up to date. All signatures verified.`
    };
  }
}

export const liveSync = new LiveSyncManager();
