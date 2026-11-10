import React, { useEffect, useState } from 'react';
import { Zap, CheckCircle2, X, ArrowRight } from 'lucide-react';
import { LiveUpdatePayload } from '../services/liveSync';

interface LiveUpdateBannerProps {
  payload: LiveUpdatePayload | null;
  onDismiss: () => void;
  onViewTools?: () => void;
}

export const LiveUpdateBanner: React.FC<LiveUpdateBannerProps> = ({
  payload,
  onDismiss,
  onViewTools
}) => {
  const [progress, setProgress] = useState(100);

  useEffect(() => {
    if (!payload) return;
    setProgress(100);

    const startTime = Date.now();
    const duration = 5000;

    const interval = setInterval(() => {
      const elapsed = Date.now() - startTime;
      const remaining = Math.max(0, 100 - (elapsed / duration) * 100);
      setProgress(remaining);
      if (elapsed >= duration) {
        clearInterval(interval);
        onDismiss();
      }
    }, 50);

    return () => clearInterval(interval);
  }, [payload, onDismiss]);

  if (!payload) return null;

  return (
    <aside
      role="status"
      aria-live="polite"
      className="fixed top-3 left-1/2 -translate-x-1/2 z-50 w-[94%] max-w-[520px] rounded-2xl bg-gradient-to-r from-[#0d162b] via-[#101a35] to-[#0c1426] border border-[#3b5998]/40 shadow-[0_20px_50px_rgba(0,0,0,0.85),0_0_25px_rgba(91,140,255,0.25)] p-3.5 anim-rise overflow-hidden backdrop-blur-md"
    >
      {/* Glow highlight */}
      <div className="absolute -top-10 -right-10 w-28 h-28 bg-[#5B8CFF]/20 rounded-full blur-2xl pointer-events-none" />

      <div className="flex items-start gap-3 relative z-10">
        {/* Animated Zap Icon */}
        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#5B8CFF] to-[#3a6bd4] text-white grid place-items-center shrink-0 shadow-md shadow-[#5B8CFF]/30 mt-0.5">
          <Zap size={18} className="fill-current animate-bounce" />
        </div>

        <div className="flex-1 min-w-0 pr-1">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-extrabold uppercase tracking-widest text-[#5B8CFF] bg-[#5B8CFF]/15 px-2 py-0.5 rounded-md border border-[#5B8CFF]/30">
              Live Data Pack Push
            </span>
            <span className="text-[11px] font-mono text-emerald-400 font-semibold flex items-center gap-1">
              <CheckCircle2 size={12} />
              v{payload.version}
            </span>
          </div>

          <h4 className="text-[14px] font-bold text-white tracking-tight mt-1 truncate">
            {payload.title}
          </h4>

          <p className="text-[12px] text-gray-300 mt-0.5 leading-snug line-clamp-2">
            {payload.details}
          </p>

          <div className="flex items-center gap-3 mt-2.5">
            {onViewTools && (
              <button
                onClick={() => {
                  onViewTools();
                  onDismiss();
                }}
                className="flex items-center gap-1 text-[11px] font-semibold text-[#5B8CFF] hover:text-[#8FB2FF] transition-colors"
              >
                <span>Open Library</span>
                <ArrowRight size={12} />
              </button>
            )}
            <span className="text-[10px] text-gray-500">
              No restart required · In-App Auto Synced
            </span>
          </div>
        </div>

        {/* Dismiss Button */}
        <button
          onClick={onDismiss}
          className="text-gray-400 hover:text-white p-1 rounded-lg transition-colors shrink-0"
          aria-label="Dismiss banner"
        >
          <X size={16} />
        </button>
      </div>

      {/* Countdown progress bar */}
      <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/10">
        <div
          className="h-full bg-gradient-to-r from-[#5B8CFF] to-[#4FD69C] transition-all duration-75"
          style={{ width: `${progress}%` }}
        />
      </div>
    </aside>
  );
};
