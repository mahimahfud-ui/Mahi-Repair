import React, { useState, useMemo } from 'react';
import { RepairHistoryItem } from '../types';
import { formatFriendlyDate } from '../utils/helpers';

interface HistoryScreenProps {
  history: RepairHistoryItem[];
  onClearHistory: () => void;
}

type FilterType = 'all' | 'ok' | 'warn' | 'bad';

export const HistoryScreen: React.FC<HistoryScreenProps> = ({
  history,
  onClearHistory,
}) => {
  const [filter, setFilter] = useState<FilterType>('all');
  const [selectedItem, setSelectedItem] = useState<RepairHistoryItem | null>(null);

  const filteredHistory = useMemo(() => {
    return history.filter((item) => {
      if (filter === 'all') return true;
      if (filter === 'ok') return item.result === 'SUCCESS';
      if (filter === 'warn') return item.result === 'PARTIAL';
      if (filter === 'bad') return item.result === 'FAILED' || item.result === 'CANCELLED';
      return true;
    });
  }, [history, filter]);

  const latestRun = history[0];

  return (
    <div className="anim-push flex flex-col gap-6 max-w-[1200px] mx-auto pb-12">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
            Audit
          </div>
          <h1 className="text-[28px] font-bold tracking-tight text-[#F3F6FC] mt-1">
            History
          </h1>
        </div>

        {history.length > 0 && (
          <button
            onClick={onClearHistory}
            className="text-[13px] text-[#5E6B88] hover:text-[#FF4D5E] transition-colors cursor-pointer"
          >
            Clear log
          </button>
        )}
      </div>

      {/* Segmented Filter */}
      <div className="w-full max-w-[440px] flex p-1 rounded-[14px] bg-[#121B2F] border border-[rgba(150,180,255,0.08)]">
        {[
          { id: 'all' as FilterType, label: 'All' },
          { id: 'ok' as FilterType, label: 'Passed' },
          { id: 'warn' as FilterType, label: 'Attention' },
          { id: 'bad' as FilterType, label: 'Failed' },
        ].map((tab) => {
          const isActive = filter === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setFilter(tab.id)}
              className={`flex-1 h-9 rounded-[10px] text-[13px] font-medium transition-all ${
                isActive
                  ? 'bg-[#1A2540] text-[#F3F6FC] font-semibold shadow-[0_1px_2px_rgba(0,0,0,0.45)]'
                  : 'text-[#9AA7C2] hover:text-[#F3F6FC]'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* History List or Empty State */}
      <div>
        {filteredHistory.length > 0 ? (
          <div className="mahi-card divide-y divide-[rgba(150,180,255,0.08)] py-1.5 px-4">
            {filteredHistory.map((item) => {
              const isOk = item.result === 'SUCCESS';
              const isWarn = item.result === 'PARTIAL';
              const isSelected = selectedItem?.id === item.id;

              return (
                <div key={item.id} className="py-3">
                  <div
                    onClick={() => setSelectedItem(isSelected ? null : item)}
                    className="flex items-center justify-between gap-3 cursor-pointer group"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-[14px] text-[#F3F6FC] group-hover:text-[#5B8CFF] transition-colors truncate">
                        {item.toolName}
                      </div>
                      <div className="text-[12px] text-[#9AA7C2] mt-0.5">
                        {formatFriendlyDate(item.timestamp)} · {item.durationMs}ms
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <span
                        className={`mahi-badge ${
                          isOk
                            ? 'mahi-badge-ok'
                            : isWarn
                            ? 'mahi-badge-warn'
                            : 'mahi-badge-bad'
                        }`}
                      >
                        <span className="dot"></span>
                        {isOk
                          ? 'Verified'
                          : isWarn
                          ? 'Needs attention'
                          : 'Failed'}
                      </span>
                      <span className="text-[#5E6B88] text-sm">
                        {isSelected ? '▲' : '▼'}
                      </span>
                    </div>
                  </div>

                  {/* Expanded Step Details */}
                  {isSelected && (
                    <div className="mt-3 p-3.5 rounded-[12px] bg-[#0D1424] border border-[rgba(150,180,255,0.08)] flex flex-col gap-2 text-[12px] anim-rise">
                      <div className="text-[#9AA7C2]">
                        <span className="text-[#5E6B88]">Verdict: </span>
                        <span className="text-[#F3F6FC]">{item.diagnosis}</span>
                      </div>

                      {item.stepRecords && item.stepRecords.length > 0 && (
                        <div className="mt-1 flex flex-col gap-1 border-t border-[rgba(150,180,255,0.08)] pt-2 mono text-[11px] text-[#9AA7C2]">
                          {item.stepRecords.map((step) => (
                            <div
                              key={step.stepId}
                              className="flex items-center justify-between gap-2"
                            >
                              <span className="truncate">
                                [{step.status === 'COMPLETED' ? 'OK' : step.status}] {step.title}
                              </span>
                              <span className="text-[#5E6B88]">{step.durationMs}ms</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="mahi-card py-16 text-center text-[#9AA7C2]">
            <div className="w-12 h-12 rounded-full bg-[#121B2F] border border-[rgba(150,180,255,0.08)] grid place-items-center text-lg mx-auto mb-3 text-[#5E6B88]">
              ○
            </div>
            <h2 className="text-[17px] font-semibold text-[#F3F6FC]">
              Nothing here yet
            </h2>
            <p className="text-[14px] text-[#5E6B88] mt-1">
              Runs you complete will appear in this audit log.
            </p>
          </div>
        )}
      </div>

      {/* Latest Log Section */}
      <div>
        <div className="text-[15px] font-semibold text-[#F3F6FC] mb-2.5">
          Latest log
        </div>
        <div className="mahi-card bg-[#0D1424] p-4 text-[12px] mono text-[#9AA7C2] leading-relaxed">
          {latestRun ? (
            <div className="flex flex-col gap-1">
              <div>
                [SYSTEM] Tool: {latestRun.toolName} ({latestRun.result})
              </div>
              <div>[SYSTEM] Duration: {latestRun.durationMs}ms</div>
              <div>[SYSTEM] Verdict: {latestRun.diagnosis}</div>
              {latestRun.stepRecords?.map((r) => (
                <div key={r.stepId}>
                  [{r.actionName}] {r.title} ........ {r.status}
                </div>
              ))}
            </div>
          ) : (
            <div>[09:14:02] CHECK_INTERNET ........ OK<br />[09:14:03] DNS_LOOKUP ............ OK<br />[09:14:04] VERIFY_DNS .............. OK<br />Verdict: healthy</div>
          )}
        </div>
      </div>
    </div>
  );
};
