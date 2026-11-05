import React from 'react';
import {
  History,
  X,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Clock,
  Trash2,
} from 'lucide-react';
import { RepairHistoryItem } from '../types';

interface HistoryDialogProps {
  history: RepairHistoryItem[];
  onClearHistory: () => void;
  onDismiss: () => void;
}

export const HistoryDialog: React.FC<HistoryDialogProps> = ({
  history,
  onClearHistory,
  onDismiss,
}) => {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget) onDismiss();
      }}
    >
      <div
        className="w-full max-w-xl rounded-2xl border border-[#252834] bg-[#11131a] p-6 shadow-2xl shadow-black/80 max-h-[90vh] flex flex-col"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#20232e]">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#1e212b] text-red-400">
              <History size={18} />
            </div>
            <div>
              <h2 className="text-sm font-bold text-gray-100 tracking-tight">
                Repair History
              </h2>
              <p className="text-[11px] text-gray-400">
                Device diagnostic audit log
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {history.length > 0 && (
              <button
                onClick={onClearHistory}
                className="flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs text-gray-400 hover:text-red-400 hover:bg-[#20232e] transition-colors"
                title="Clear history"
              >
                <Trash2 size={13} />
                <span>Clear</span>
              </button>
            )}
            <button
              onClick={onDismiss}
              className="rounded-lg p-1 text-gray-400 hover:bg-[#20232e] hover:text-gray-200 transition-colors"
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="overflow-y-auto py-4 flex-1 space-y-3">
          {history.length === 0 ? (
            <div className="py-14 flex flex-col items-center justify-center text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#191b24] text-gray-500">
                <History size={24} />
              </div>
              <p className="mt-3 text-sm font-medium text-gray-300">
                No repair runs recorded yet.
              </p>
              <p className="mt-1 text-xs text-gray-500">
                Runs performed on this device will appear here.
              </p>
            </div>
          ) : (
            history.map((item) => {
              const isSuccess = item.result === 'SUCCESS';
              const isPartial = item.result === 'PARTIAL';
              const dateStr = new Date(item.timestamp).toLocaleString();

              return (
                <div
                  key={item.id}
                  className="rounded-xl border border-[#20232e] bg-[#14161f] p-4 transition-all hover:border-[#2d3140]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-sm font-semibold text-gray-100">
                      {item.toolName}
                    </h4>
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-bold tracking-wider ${
                        isSuccess
                          ? 'border border-emerald-500/30 bg-emerald-950/30 text-emerald-400'
                          : isPartial
                          ? 'border border-amber-500/30 bg-amber-950/30 text-amber-400'
                          : 'border border-red-500/30 bg-red-950/30 text-red-400'
                      }`}
                    >
                      {item.result}
                    </span>
                  </div>

                  <div className="mt-1 flex items-center justify-between text-[11px] text-gray-500">
                    <span>{dateStr}</span>
                    <span className="flex items-center gap-1 font-mono">
                      <Clock size={11} /> {item.durationMs}ms • {item.stepsCount} steps
                    </span>
                  </div>

                  <p className="mt-2.5 text-xs text-gray-300 leading-relaxed bg-[#0e1017] p-2.5 rounded-lg border border-[#1b1d26]">
                    {item.diagnosis}
                  </p>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
