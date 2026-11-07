import React from 'react';
import { RepairHistoryItem, RepairTool } from '../types';
import { getToolMonogram, formatFriendlyDate } from '../utils/helpers';

interface HomeScreenProps {
  tools: RepairTool[];
  history: RepairHistoryItem[];
  onOpenTool: (toolId: string) => void;
  onGoTab: (tab: 'tools' | 'hist') => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  tools,
  history,
  onOpenTool,
  onGoTab,
}) => {
  // Find DNS tool if available
  const dnsTool = tools.find((t) => t.id.includes('dns')) || tools[0];

  // Battery & storage estimates or detected
  const batteryPct = 82;
  const storagePct = 61;

  // Recent 3 history items or fallback
  const recentRuns = history.slice(0, 3);

  return (
    <div className="anim-push flex flex-col gap-7 max-w-[1200px] mx-auto pb-12">
      {/* Eyebrow & Title */}
      <div>
        <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
          Good morning
        </div>
        <h1 className="text-[28px] font-bold tracking-tight text-[#F3F6FC] mt-1">
          Your device is ready
        </h1>
      </div>

      {/* Top Hero Grid: Connected Device + Recommended Action */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-4">
        {/* Device Status Card */}
        <section className="mahi-card relative overflow-hidden">
          <div className="flex items-center gap-5">
            {/* Phone Silhouette */}
            <div className="w-[54px] h-[96px] rounded-[14px] border-2 border-[rgba(150,180,255,0.16)] bg-[#060A14] grid place-items-center relative flex-shrink-0">
              <div className="absolute top-[6px] w-[14px] h-[3px] rounded-[3px] bg-[#1A2540]"></div>
              <span className="pulse-indicator"></span>
            </div>

            {/* Device Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between mb-2">
                <span className="mahi-badge mahi-badge-ok">
                  <span className="dot"></span>
                  Connected
                </span>
                <span className="mono text-[12px] text-[#9AA7C2]">
                  DEMO-0001
                </span>
              </div>
              <div className="text-[22px] font-bold text-[#F3F6FC] tracking-tight truncate">
                Pixel 8
              </div>
              <div className="text-[13px] text-[#9AA7C2] mt-0.5">
                Android 15 · Connected · Wi-Fi
              </div>
            </div>
          </div>

          {/* Key Value Stats */}
          <div className="grid grid-cols-2 gap-4 mt-6 pt-5 border-t border-[rgba(150,180,255,0.08)]">
            <div>
              <div className="text-[12px] text-[#5E6B88] mb-1">Battery</div>
              <div className="font-semibold mono text-[16px] text-[#F3F6FC]">
                {batteryPct}%
              </div>
              <div className="h-[6px] rounded-[6px] bg-[#1A2540] overflow-hidden mt-2">
                <div
                  className="h-full bg-gradient-to-r from-[#5B8CFF] to-[#8FB2FF] rounded-[6px] transition-all duration-500"
                  style={{ width: `${batteryPct}%` }}
                ></div>
              </div>
            </div>

            <div>
              <div className="text-[12px] text-[#5E6B88] mb-1">Storage used</div>
              <div className="font-semibold mono text-[16px] text-[#F3F6FC]">
                {storagePct}%
              </div>
              <div className="h-[6px] rounded-[6px] bg-[#1A2540] overflow-hidden mt-2">
                <div
                  className="h-full bg-gradient-to-r from-[#5B8CFF] to-[#8FB2FF] rounded-[6px] transition-all duration-500"
                  style={{ width: `${storagePct}%` }}
                ></div>
              </div>
            </div>
          </div>
        </section>

        {/* Recommended Action Card */}
        <section className="mahi-card flex flex-col justify-between gap-5">
          <div>
            <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
              Recommended
            </div>
            <h2 className="text-[19px] font-bold text-[#F3F6FC] mt-1.5">
              Run a connection check
            </h2>
            <p className="text-[14px] text-[#9AA7C2] mt-2 leading-relaxed">
              Verifies internet and DNS resolution before you troubleshoot an app.
            </p>
          </div>

          <button
            onClick={() => onOpenTool(dnsTool.id)}
            className="mahi-btn mahi-btn-primary w-full"
          >
            Start DNS diagnostics
          </button>
        </section>
      </div>

      {/* Featured Repair Tools */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-[17px] font-semibold text-[#F3F6FC] tracking-tight">
            Repair tools
          </h2>
          <button
            onClick={() => onGoTab('tools')}
            className="text-[13px] font-medium text-[#5B8CFF] hover:underline cursor-pointer"
          >
            View all
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
          {tools.slice(0, 3).map((tool) => {
            const monogram = getToolMonogram(tool);
            return (
              <button
                key={tool.id}
                onClick={() => onOpenTool(tool.id)}
                className="mahi-card mahi-card-interactive"
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-[12px] bg-[rgba(91,140,255,0.14)] text-[#5B8CFF] font-bold text-[14px] grid place-items-center flex-shrink-0">
                    {monogram}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-[15px] text-[#F3F6FC] truncate">
                      {tool.name}
                    </div>
                    <div className="text-[13px] text-[#9AA7C2] mt-0.5">
                      Verified recipe
                    </div>
                  </div>
                  <span className="text-[#9AA7C2] text-lg">›</span>
                </div>
                <p className="text-[14px] text-[#9AA7C2] mt-3.5 line-clamp-2 leading-relaxed">
                  {tool.description}
                </p>
              </button>
            );
          })}
        </div>
      </div>

      {/* Recent Activity */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-[17px] font-semibold text-[#F3F6FC] tracking-tight">
            Recent activity
          </h2>
          <button
            onClick={() => onGoTab('hist')}
            className="text-[13px] font-medium text-[#5B8CFF] hover:underline cursor-pointer"
          >
            History
          </button>
        </div>

        <div className="mahi-card divide-y divide-[rgba(150,180,255,0.08)] py-1.5 px-4">
          {recentRuns.length > 0 ? (
            recentRuns.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between py-3.5 gap-3"
              >
                <div className="min-w-0 flex-1">
                  <div className="font-semibold text-[14px] text-[#F3F6FC] truncate">
                    {item.toolName}
                  </div>
                  <div className="text-[12px] text-[#9AA7C2] mt-0.5">
                    {formatFriendlyDate(item.timestamp)}
                  </div>
                </div>
                <span
                  className={`mahi-badge ${
                    item.result === 'SUCCESS'
                      ? 'mahi-badge-ok'
                      : item.result === 'PARTIAL'
                      ? 'mahi-badge-warn'
                      : 'mahi-badge-bad'
                  }`}
                >
                  <span className="dot"></span>
                  {item.result === 'SUCCESS'
                    ? 'Verified'
                    : item.result === 'PARTIAL'
                    ? 'Attention'
                    : 'Failed'}
                </span>
              </div>
            ))
          ) : (
            <div className="py-6 text-center text-[14px] text-[#9AA7C2]">
              No runs yet. Select a repair tool above to run diagnostics.
            </div>
          )}
        </div>
      </div>

      {/* Prototype Footer Note */}
      <p className="text-[11px] text-[#5E6B88] mt-2">
        Verified Native Execution · Cryptographic RSA-2048 Client Verification · Offline Ready
      </p>
    </div>
  );
};
