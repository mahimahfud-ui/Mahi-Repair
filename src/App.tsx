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
import { Navigation, NavTab } from './components/Navigation';
import { HomeScreen } from './components/HomeScreen';
import { ToolsScreen } from './components/ToolsScreen';
import { ToolDetailScreen } from './components/ToolDetailScreen';
import { ConfirmationSheet } from './components/ConfirmationSheet';
import { HistoryScreen } from './components/HistoryScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { BackendConfigDialog } from './components/BackendConfigDialog';
import { AdminConsole } from './components/AdminConsole';

const LOCAL_STORAGE_KEY_HISTORY = 'mahi_repair_history_v1';
const LOCAL_STORAGE_KEY_CONFIG = 'mahi_repair_backend_config_v1';
const LOCAL_STORAGE_KEY_SESSION = 'mahi_repair_session_v1';

export const App: React.FC = () => {
  // Mode selection: Laptop Admin Console vs Native Mobile Client
  const [viewMode, setViewMode] = useState<'admin' | 'phone'>('admin');

  // Navigation & Screen selection
  const [activeTab, setActiveTab] = useState<NavTab>('home');
  const [selectedToolId, setSelectedToolId] = useState<string | null>(null);
  const [confirmTool, setConfirmTool] = useState<RepairTool | null>(null);
  const [showBackendConfig, setShowBackendConfig] = useState(false);

  // Session state (default authenticated for instant access)
  const [session, setSession] = useState<AuthSession>(() => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY_SESSION);
      if (saved) return JSON.parse(saved);
    } catch {
      // Fallback
    }
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
  };

  const handleSelectTab = (tab: NavTab) => {
    setSelectedToolId(null);
    setActiveTab(tab);
  };

  const selectedTool = selectedToolId
    ? tools.find((t) => t.id === selectedToolId) || null
    : null;

  if (viewMode === 'admin') {
    return (
      <AdminConsole
        tools={tools}
        onUpdateTools={setTools}
        onSwitchToClient={() => setViewMode('phone')}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#060A14] text-[#F3F6FC] flex flex-col lg:flex-row antialiased selection:bg-[#5B8CFF]/30">
      {/* Floating button to switch to Laptop Admin Console */}
      <button
        onClick={() => setViewMode('admin')}
        className="fixed top-4 right-4 z-50 flex items-center gap-2 rounded-full bg-red-600/90 hover:bg-red-600 border border-red-400/40 text-white px-3.5 py-1.5 text-xs font-bold shadow-xl backdrop-blur transition-all"
      >
        <span className="h-2 w-2 rounded-full bg-white animate-pulse" />
        <span>Owner Admin Console</span>
      </button>

      {/* Navigation Shell (Left sidebar on desktop, bottom bar on mobile) */}
      <Navigation
        activeTab={activeTab}
        onSelectTab={handleSelectTab}
        isOfflineMode={session.isOfflineMode}
      />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Mobile Top Header (<1024px) */}
        <header className="lg:hidden sticky top-0 z-30 bg-[#060A14]/90 backdrop-blur-md border-b border-[rgba(150,180,255,0.08)] px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-[8px] bg-gradient-to-br from-[#5B8CFF] to-[#FF4D5E] grid place-items-center text-white font-extrabold text-xs shadow-sm">
              M
            </div>
            <span className="font-bold text-[15px] tracking-tight text-[#F3F6FC]">
              Mahi Repair
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#4FD69C] animate-pulse"></span>
            <span className="text-[11px] font-semibold text-[#9AA7C2] uppercase tracking-wider">
              {session.isOfflineMode ? 'Offline' : 'Connected'}
            </span>
          </div>
        </header>

        {/* Viewport Screen Content */}
        <main className="flex-1 px-4 py-6 sm:px-8 sm:py-8 pb-28 lg:pb-12 max-w-[1240px] w-full mx-auto">
          {selectedTool ? (
            <ToolDetailScreen
              tool={selectedTool}
              executionEvent={executionEvent}
              onBack={() => setSelectedToolId(null)}
              onRequestRun={(tool) => setConfirmTool(tool)}
              onResetRun={handleDismissExecution}
              onSubmitUserAction={handleSubmitUserAction}
            />
          ) : (
            <>
              {activeTab === 'home' && (
                <HomeScreen
                  tools={tools}
                  history={history}
                  onOpenTool={(toolId) => {
                    setSelectedToolId(toolId);
                  }}
                  onGoTab={(tab) => {
                    setSelectedToolId(null);
                    setActiveTab(tab);
                  }}
                />
              )}

              {activeTab === 'tools' && (
                <ToolsScreen
                  tools={tools}
                  onOpenTool={(toolId) => {
                    setSelectedToolId(toolId);
                  }}
                />
              )}

              {activeTab === 'hist' && (
                <HistoryScreen
                  history={history}
                  onClearHistory={handleClearHistory}
                />
              )}

              {activeTab === 'set' && (
                <SettingsScreen
                  session={session}
                  backendConfig={backendConfig}
                  onSignOut={handleSignOut}
                  onLogin={handleLogin}
                  onOpenBackendConfig={() => setShowBackendConfig(true)}
                />
              )}
            </>
          )}
        </main>
      </div>

      {/* Confirmation Sheet (Modal / Bottom Sheet) */}
      {confirmTool && (
        <ConfirmationSheet
          tool={confirmTool}
          isOpen={true}
          onConfirm={async () => {
            const toolToRun = confirmTool;
            setConfirmTool(null);
            if (toolToRun) {
              await handleRunTool(toolToRun);
            }
          }}
          onCancel={() => setConfirmTool(null)}
        />
      )}

      {/* Backend Configuration Modal */}
      {showBackendConfig && (
        <BackendConfigDialog
          config={backendConfig}
          onSave={handleSaveBackendConfig}
          onDismiss={() => setShowBackendConfig(false)}
        />
      )}
    </div>
  );
};
