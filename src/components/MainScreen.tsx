import React, { useState, useMemo } from 'react';
import {
  RefreshCw,
  History,
  Settings,
  LogOut,
  Search,
  X,
  Wrench,
  ShieldCheck,
} from 'lucide-react';
import {
  AuthSession,
  BackendConfig,
  ExecutionEvent,
  RepairHistoryItem,
  RepairTool,
  ToolCategory,
} from '../types';
import { ToolCard } from './ToolCard';
import { ExecutionModal } from './ExecutionModal';
import { HistoryDialog } from './HistoryDialog';
import { BackendConfigDialog } from './BackendConfigDialog';

interface MainScreenProps {
  session: AuthSession;
  tools: RepairTool[];
  executionEvent: ExecutionEvent;
  history: RepairHistoryItem[];
  backendConfig: BackendConfig;
  lastSyncTime: number;
  isSyncing: boolean;
  onSync: () => void;
  onRunTool: (tool: RepairTool) => void;
  onSubmitUserAction: (approved: boolean) => void;
  onDismissExecution: () => void;
  onClearHistory: () => void;
  onSaveBackendConfig: (config: BackendConfig) => void;
  onSignOut: () => void;
}

const CATEGORIES: { id: ToolCategory; title: string }[] = [
  { id: 'ALL', title: 'All' },
  { id: 'NETWORK', title: 'Network' },
  { id: 'SOCIAL', title: 'Social Apps' },
  { id: 'SYSTEM', title: 'System & Storage' },
  { id: 'MEDIA', title: 'Media & Streaming' },
  { id: 'GENERAL', title: 'General' },
];

export const MainScreen: React.FC<MainScreenProps> = ({
  session,
  tools,
  executionEvent,
  history,
  backendConfig,
  lastSyncTime,
  isSyncing,
  onSync,
  onRunTool,
  onSubmitUserAction,
  onDismissExecution,
  onClearHistory,
  onSaveBackendConfig,
  onSignOut,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<ToolCategory>('ALL');
  const [showHistory, setShowHistory] = useState(false);
  const [showBackendConfig, setShowBackendConfig] = useState(false);

  // Filter tools
  const filteredTools = useMemo(() => {
    return tools.filter((tool) => {
      const matchesCategory =
        selectedCategory === 'ALL' || tool.category === selectedCategory;

      const query = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !query ||
        tool.name.toLowerCase().includes(query) ||
        tool.description.toLowerCase().includes(query) ||
        tool.problemDescription.toLowerCase().includes(query) ||
        (tool.targetPackage &&
          tool.targetPackage.toLowerCase().includes(query));

      return matchesCategory && matchesSearch;
    });
  }, [tools, selectedCategory, searchQuery]);

  const syncTimeStr =
    lastSyncTime > 0
      ? new Date(lastSyncTime).toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })
      : 'Never';

  return (
    <div className="min-h-screen bg-[#090a0d] text-gray-200 flex flex-col antialiased">
      {/* Top App Bar matching MainScreen.kt */}
      <header className="sticky top-0 z-40 border-b border-[#20232e] bg-[#0e1017]/95 backdrop-blur-md px-4 py-3 sm:px-6">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          {/* Logo & Identity */}
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#161822] border border-red-500/40 p-1.5 shadow-sm">
              <img
                src="/ic_launcher_logo.png"
                alt="Logo"
                className="h-full w-full object-contain"
                onError={(e) => {
                  (e.target as HTMLElement).style.display = 'none';
                }}
              />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h1 className="text-sm font-bold text-white tracking-tight">
                  Mahi Repair
                </h1>
                {session.isOfflineMode && (
                  <span className="rounded bg-amber-500/20 text-amber-400 border border-amber-500/30 px-1.5 py-0.2 text-[9px] font-semibold">
                    OFFLINE
                  </span>
                )}
              </div>
              <p className="text-[11px] text-gray-400 truncate max-w-[200px] sm:max-w-xs">
                {session.isAuthenticated
                  ? session.email
                  : 'Personal Android Toolkit'}
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-1">
            {/* Sync */}
            <button
              onClick={onSync}
              disabled={isSyncing}
              className="rounded-xl p-2 text-gray-400 hover:text-gray-200 hover:bg-[#1c1f2b] transition-colors disabled:opacity-50"
              title="Sync tools from Cloud"
              aria-label="Sync"
            >
              <RefreshCw
                size={16}
                className={isSyncing ? 'animate-spin text-red-400' : ''}
              />
            </button>

            {/* History */}
            <button
              onClick={() => setShowHistory(true)}
              className="rounded-xl p-2 text-gray-400 hover:text-gray-200 hover:bg-[#1c1f2b] transition-colors"
              title="View Repair History"
              aria-label="History"
            >
              <History size={16} />
            </button>

            {/* Backend Settings */}
            <button
              onClick={() => setShowBackendConfig(true)}
              className="rounded-xl p-2 text-gray-400 hover:text-gray-200 hover:bg-[#1c1f2b] transition-colors"
              title="Connection Settings"
              aria-label="Connection Settings"
            >
              <Settings size={16} />
            </button>

            {/* Sign Out */}
            {session.isAuthenticated && (
              <button
                onClick={onSignOut}
                className="rounded-xl p-2 text-red-400 hover:text-red-300 hover:bg-[#1c1f2b] transition-colors"
                title="Sign Out"
                aria-label="Sign Out"
              >
                <LogOut size={16} />
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 sm:p-6 space-y-4">
        {/* Status Bar */}
        <div className="flex items-center justify-between rounded-xl border border-[#20232e] bg-[#12131a] px-4 py-2 text-xs">
          <div
            onClick={() => setShowBackendConfig(true)}
            className="flex items-center gap-2 cursor-pointer hover:opacity-80 transition-opacity"
          >
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-medium text-gray-300">
              Mahi Cloud Backend
            </span>
          </div>
          <span className="text-gray-500 font-mono text-[11px]">
            Synced: {syncTimeStr}
          </span>
        </div>

        {/* Search Bar */}
        <div className="relative">
          <Search
            size={16}
            className="absolute left-3.5 top-3 text-gray-500"
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search repair tools..."
            className="w-full rounded-2xl border border-[#252834] bg-[#14161f] pl-10 pr-10 py-2.5 text-xs text-gray-200 placeholder-gray-500 focus:border-red-500 focus:outline-none transition-colors"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3.5 top-3 text-gray-500 hover:text-gray-300"
            >
              <X size={15} />
            </button>
          )}
        </div>

        {/* Category Filter Chips */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
          {CATEGORIES.map((cat) => {
            const isSelected = selectedCategory === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={`rounded-xl px-3.5 py-1.5 text-xs font-medium whitespace-nowrap transition-all ${
                  isSelected
                    ? 'bg-red-600 text-white font-bold shadow-md shadow-red-950/40'
                    : 'border border-[#222530] bg-[#14161f] text-gray-400 hover:border-[#323646] hover:text-gray-200'
                }`}
              >
                {cat.title}
              </button>
            );
          })}
        </div>

        {/* Tools List */}
        <div className="space-y-3 pt-1">
          {filteredTools.length === 0 ? (
            <div className="py-20 flex flex-col items-center justify-center text-center rounded-2xl border border-[#20232e] bg-[#101219] p-6">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[#1b1d28] text-gray-500">
                <Wrench size={30} />
              </div>
              <h3 className="mt-4 text-base font-semibold text-gray-200">
                No repair tools found
              </h3>
              <p className="mt-1 text-xs text-gray-400 max-w-sm">
                {searchQuery
                  ? `No tools matched '${searchQuery}'.`
                  : 'The Owner has not published any tools in this category yet.'}
              </p>
            </div>
          ) : (
            filteredTools.map((tool) => (
              <ToolCard key={tool.id} tool={tool} onRun={onRunTool} />
            ))
          )}
        </div>
      </main>

      {/* Execution Modal */}
      <ExecutionModal
        event={executionEvent}
        onSubmitUserAction={onSubmitUserAction}
        onDismiss={onDismissExecution}
      />

      {/* History Dialog */}
      {showHistory && (
        <HistoryDialog
          history={history}
          onClearHistory={onClearHistory}
          onDismiss={() => setShowHistory(false)}
        />
      )}

      {/* Backend Config Dialog */}
      {showBackendConfig && (
        <BackendConfigDialog
          config={backendConfig}
          onSave={onSaveBackendConfig}
          onDismiss={() => setShowBackendConfig(false)}
        />
      )}
    </div>
  );
};
