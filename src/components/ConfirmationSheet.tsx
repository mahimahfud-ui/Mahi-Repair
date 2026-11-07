import React, { useEffect } from 'react';
import { RepairTool } from '../types';
import { getParsedToolSteps } from '../utils/helpers';

interface ConfirmationSheetProps {
  tool: RepairTool;
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ConfirmationSheet: React.FC<ConfirmationSheetProps> = ({
  tool,
  isOpen,
  onConfirm,
  onCancel,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onCancel();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onCancel]);

  if (!isOpen) return null;

  const steps = getParsedToolSteps(tool);

  return (
    <>
      {/* Scrim with blur */}
      <div
        onClick={onCancel}
        className="fixed inset-0 bg-black/60 backdrop-blur-[6px] z-50 transition-opacity"
        aria-hidden="true"
      />

      {/* Sheet on Mobile (<640px) / Centered Dialog on Desktop (>=640px) */}
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmSheetTitle"
        className="fixed bottom-0 sm:bottom-auto sm:top-1/2 left-0 sm:left-1/2 right-0 sm:right-auto sm:-translate-x-1/2 sm:-translate-y-1/2 w-full sm:max-w-[500px] z-50 bg-[#0D1424] border border-[rgba(150,180,255,0.16)] rounded-t-[26px] sm:rounded-[22px] p-6 pb-8 sm:p-7 shadow-[0_-20px_60px_rgba(0,0,0,0.6)] sm:shadow-[0_24px_60px_rgba(0,0,0,0.6)] anim-rise"
      >
        {/* Mobile Grab Handle */}
        <div className="sm:hidden w-10 h-1.5 rounded-full bg-[#1A2540] mx-auto mb-4" />

        <div className="flex flex-col gap-4">
          <div>
            <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
              Confirmation required
            </div>
            <h2
              id="confirmSheetTitle"
              className="text-[20px] font-bold text-[#F3F6FC] mt-1 tracking-tight"
            >
              Run {tool.name}?
            </h2>
            <p className="text-[14px] text-[#9AA7C2] mt-2 leading-relaxed">
              This action will execute {steps.length} sequential diagnostic and recovery steps.
              Every step is signed with the Owner's RSA key and allowlisted for safe native execution.
            </p>
          </div>

          {/* Steps Preview */}
          <div className="rounded-[12px] bg-[#121B2F] border border-[rgba(150,180,255,0.08)] p-3 text-[12px] text-[#9AA7C2] flex flex-col gap-1.5 max-h-[140px] overflow-y-auto">
            {steps.slice(0, 4).map((s, idx) => (
              <div key={s.id} className="flex items-center gap-2 truncate">
                <span className="w-4 h-4 rounded-full bg-[#1A2540] text-[#5B8CFF] font-bold text-[10px] grid place-items-center flex-shrink-0">
                  {idx + 1}
                </span>
                <span className="truncate">{s.name}</span>
              </div>
            ))}
            {steps.length > 4 && (
              <div className="text-[11px] text-[#5E6B88] pl-6">
                + {steps.length - 4} more verified actions
              </div>
            )}
          </div>

          {/* Action Buttons */}
          <div className="flex flex-col sm:flex-row gap-2.5 mt-2">
            <button
              onClick={onConfirm}
              className="mahi-btn mahi-btn-primary flex-1"
            >
              Confirm Repair Run
            </button>
            <button
              onClick={onCancel}
              className="mahi-btn mahi-btn-ghost flex-1"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </>
  );
};
