import React, { useState, useEffect, useRef } from 'react';
import {
  AuthSession,
  BackendConfig,
  ExecutionEvent,
  RepairHistoryItem,
  RepairTool,
} from './types';
import { getInitialTools } from './data/defaultCatalog';
import { DEFAULT_OWNER_PUBLIC_KEY } from './security/verifier';
import { ActionRegistry } from './engine/ActionRegistry';
import { RecipeExecutionEngine } from './engine/RecipeExecutionEngine';
import { AuthScreen } from './components/AuthScreen';
import { MainScreen } from './components/MainScreen';
import { AdminConsole } from './components/AdminConsole';

const LOCAL_STORAGE_KEY_HISTORY = 'mahi_repair_history_v1';
const LOCAL_STORAGE_KEY_CONFIG = 'mahi_repair_backend_config_v1';
const LOCAL_STORAGE_KEY_SESSION = 'mahi_repair_session_v1';

export const App: React.FC = () => {
  // Session state
  const [session, setSession] = useState<AuthSession>(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY_SESSION);
      if (saved) return JSON.parse(saved);
    } catch {
      // Fallback
    }
    // Default to active authenticated demo user for instant accessibility
    return {
      isAuthenticated: true,
      email: 'alex.owner@example.com',
      role: 'owner',
      isOfflineMode: true,
    };
  });

  // Tools catalog
  const [tools, setTools] = useState<RepairTool[]>(() => getInitialTools());

  // Backend config
  const [backendConfig, setBackendConfig] = useState<BackendConfig>(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY_CONFIG);
      if (saved) return JSON.parse(saved);
    } catch {
      // Fallback
    }
    return {
      supabaseUrl: 'https://demo-mahi-cloud.supabase.co',
      supabaseAnonKey:
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1haGktcmVwYWlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE2ODAwMDAwMDB9',
      publicKeyPem: DEFAULT_OWNER_PUBLIC_KEY,
    };
  });

  // Execution History
  const [history, setHistory] = useState<RepairHistoryItem[]>(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY_HISTORY);
      if (saved) return JSON.parse(saved);
    } catch {
      // Fallback
    }
    return [
      {
        id: 'hist_demo_1',
        toolId: 'tool_youtube_repair',
        toolName: 'YouTube Quick Repair',
        timestamp: Date.now() - 3600000 * 2,
        result: 'SUCCESS',
        durationMs: 1240,
        diagnosis:
          'All repair and diagnostic steps passed successfully. Device issue resolved.',
        stepsCount: 7,
      },
      {
        id: 'hist_demo_2',
        toolId: 'tool_dns_repair',
        toolName: 'DNS Repair & Diagnostics',
        timestamp: Date.now() - 86400000,
        result: 'SUCCESS',
        durationMs: 820,
        diagnosis:
          'DNS resolution confirmed for google.com & Cloudflare endpoints.',
        stepsCount: 6,
      },
    ];
  });

  // Engine state
  const [executionEvent, setExecutionEvent] = useState<ExecutionEvent>({
    type: 'Idle',
  });
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState<number>(Date.now());
  const [viewMode, setViewMode] = useState<'client' | 'admin'>('client');

  // Engine ref
  const engineRef = useRef<RecipeExecutionEngine | null>(null);

  useEffect(() => {
    const registry = new ActionRegistry();
    engineRef.current = new RecipeExecutionEngine(registry, (event) => {
      setExecutionEvent(event);
      if (event.type === 'Finished') {
        // Record into history
        const newHist: RepairHistoryItem = {
          id: `hist_${event.report.runId}`,
          toolId: event.report.toolId,
          toolName: event.report.toolName,
          timestamp: event.report.endTime,
          result: event.report.result,
          durationMs: event.report.endTime - event.report.startTime,
          diagnosis: event.report.finalDiagnosis,
          stepsCount: event.report.stepRecords.length,
          stepRecords: event.report.stepRecords,
        };
        setHistory((prev) => {
          const updated = [newHist, ...prev];
          try {
            localStorage.setItem(
              LOCAL_STORAGE_KEY_HISTORY,
              JSON.stringify(updated)
            );
          } catch {
            // Ignore
          }
          return updated;
        });
      }
    });
  }, []);

  const handleLogin = (newSession: AuthSession) => {
    setSession(newSession);
    try {
      localStorage.setItem(
        LOCAL_STORAGE_KEY_SESSION,
        JSON.stringify(newSession)
      );
    } catch {
      // Ignore
    }
  };

  const handleSignOut = () => {
    const defaultLoggedOut: AuthSession = {
      isAuthenticated: false,
      email: '',
      role: 'member',
      isOfflineMode: false,
    };
    setSession(defaultLoggedOut);
    try {
      localStorage.removeItem(LOCAL_STORAGE_KEY_SESSION);
    } catch {
      // Ignore
    }
  };

  const handleSync = () => {
    setIsSyncing(true);
    setTimeout(() => {
      setIsSyncing(false);
      setLastSyncTime(Date.now());
    }, 700);
  };

  const handleRunTool = async (tool: RepairTool) => {
    if (!engineRef.current) return;
    const rawPayload = tool.rawSignedPayload || '';
    const signature = tool.signatureBase64 || '';
    await engineRef.current.executeRecipe(
      tool,
      rawPayload,
      signature,
      session.email,
      backendConfig.publicKeyPem
    );
  };

  const handleSubmitUserAction = (approved: boolean) => {
    engineRef.current?.submitUserActionResponse(approved);
  };

  const handleDismissExecution = () => {
    engineRef.current?.reset();
    setExecutionEvent({ type: 'Idle' });
  };

  const handleClearHistory = () => {
    setHistory([]);
    try {
      localStorage.removeItem(LOCAL_STORAGE_KEY_HISTORY);
    } catch {
      // Ignore
    }
  };

  const handleSaveBackendConfig = (config: BackendConfig) => {
    setBackendConfig(config);
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY_CONFIG, JSON.stringify(config));
    } catch {
      // Ignore
    }
    handleSync();
  };

  if (!session.isAuthenticated) {
    return <AuthScreen onLogin={handleLogin} />;
  }

  if (viewMode === 'admin') {
    return (
      <AdminConsole
        tools={tools}
        onUpdateTools={(newTools) => setTools(newTools)}
        onSwitchToClient={() => setViewMode('client')}
      />
    );
  }

  return (
    <MainScreen
      session={session}
      tools={tools}
      executionEvent={executionEvent}
      history={history}
      backendConfig={backendConfig}
      lastSyncTime={lastSyncTime}
      isSyncing={isSyncing}
      onSync={handleSync}
      onRunTool={handleRunTool}
      onSubmitUserAction={handleSubmitUserAction}
      onDismissExecution={handleDismissExecution}
      onClearHistory={handleClearHistory}
      onSaveBackendConfig={handleSaveBackendConfig}
      onSignOut={handleSignOut}
      onSwitchToAdmin={() => setViewMode('admin')}
    />
  );
};
