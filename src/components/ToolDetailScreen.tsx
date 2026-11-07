import React from 'react';
import { ExecutionEvent, RepairTool } from '../types';
import { getToolMonogram, getParsedToolSteps } from '../utils/helpers';

interface ToolDetailScreenProps {
  tool: RepairTool;
  executionEvent: ExecutionEvent;
  onBack: () => void;
  onRequestRun: (tool: RepairTool) => void;
  onResetRun: () => void;
  onSubmitUserAction: (approved: boolean) => void;
}

export const ToolDetailScreen: React.FC<ToolDetailScreenProps> = ({
  tool,
  executionEvent,
  onBack,
  onRequestRun,
  onResetRun,
  onSubmitUserAction,
}) => {
  const monogram = getToolMonogram(tool);
  const steps = getParsedToolSteps(tool);

  const isRunning =
    executionEvent.type === 'Running' ||
    executionEvent.type === 'VerifyingSignature' ||
    executionEvent.type === 'PromptUserAction';

  const isDone = executionEvent.type === 'Finished';

  // Calculate current active step index
  let currentStepIdx = -1;
  let runningStepName = '';
  if (executionEvent.type === 'Running') {
    currentStepIdx = executionEvent.currentStepIndex;
    runningStepName = executionEvent.currentStep.name;
  } else if (executionEvent.type === 'VerifyingSignature') {
    currentStepIdx = 0;
    runningStepName = 'Verifying Owner RSA signature...';
  } else if (executionEvent.type === 'PromptUserAction') {
    currentStepIdx = Math.max(0, currentStepIdx);
    runningStepName = executionEvent.title;
  } else if (isDone) {
    currentStepIdx = steps.length;
  }

  const progressPct =
    isDone
      ? 100
      : isRunning
      ? Math.round((Math.max(1, currentStepIdx + 1) / steps.length) * 100)
      : 0;

  return (
    <div className="anim-push flex flex-col gap-6 max-w-[1200px] mx-auto pb-16">
      {/* Back Button */}
      <button
        onClick={onBack}
        className="self-start text-[14px] text-[#9AA7C2] hover:text-[#F3F6FC] transition-colors flex items-center gap-1.5 cursor-pointer -ml-1 py-1"
      >
        <span>‹</span>
        <span>Tools</span>
      </button>

      {/* Tool Header */}
      <div className="flex items-center gap-4">
        <div className="w-[52px] h-[52px] rounded-[16px] bg-[rgba(91,140,255,0.14)] text-[#5B8CFF] font-bold text-[18px] grid place-items-center flex-shrink-0 shadow-[0_4px_16px_rgba(91,140,255,0.2)]">
          {monogram}
        </div>
        <div>
          <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
            Repair tool
          </div>
          <h1 className="text-[24px] font-bold text-[#F3F6FC] tracking-tight mt-0.5">
            {tool.name}
          </h1>
        </div>
      </div>

      {/* Main Grid: Overview & Steps Timeline | Status & Action Panel */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-4">
        {/* Left Column: Overview and Steps */}
        <section className="mahi-card flex flex-col gap-6">
          <div>
            <h2 className="text-[17px] font-semibold text-[#F3F6FC] tracking-tight">
              Overview
            </h2>
            <p className="text-[14px] text-[#9AA7C2] mt-1.5 leading-relaxed">
              {tool.description}
            </p>
          </div>

          <div>
            <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88] mb-4">
              Steps
            </div>

            <div className="flex flex-col">
              {steps.map((s, idx) => {
                let statusClass = '';
                let statusText = 'Pending';
                let iconText = `${idx + 1}`;

                if (isDone) {
                  statusClass = 'is-done';
                  statusText = 'Completed';
                  iconText = '✓';
                } else if (isRunning) {
                  if (idx < currentStepIdx) {
                    statusClass = 'is-done';
                    statusText = 'Completed';
                    iconText = '✓';
                  } else if (idx === currentStepIdx) {
                    statusClass = 'is-active';
                    statusText = 'In progress…';
                  }
                }

                return (
                  <div key={s.id} className={`step-item ${statusClass}`}>
                    <div className="step-num">{iconText}</div>
                    <div className="flex-1 min-w-0 pt-0.5">
                      <div className="text-[14px] font-medium text-[#F3F6FC]">
                        {s.name}
                      </div>
                      <div className="text-[12px] text-[#5E6B88] mt-0.5">
                        {statusText}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        {/* Right Column: Status Card & Action */}
        <section className="mahi-card flex flex-col justify-between gap-5">
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h2 className="text-[17px] font-semibold text-[#F3F6FC] tracking-tight">
                Status
              </h2>
              {isRunning ? (
                <span className="mahi-badge mahi-badge-run">
                  <span className="dot"></span>
                  Running
                </span>
              ) : isDone ? (
                <span className="mahi-badge mahi-badge-ok">
                  <span className="dot"></span>
                  Complete
                </span>
              ) : (
                <span className="mahi-badge">
                  <span className="dot"></span>
                  Idle
                </span>
              )}
            </div>

            {/* In Progress State */}
            {isRunning && (
              <div className="flex flex-col gap-3 py-2">
                <div className="h-[6px] rounded-[6px] bg-[#1A2540] overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-[#5B8CFF] to-[#8FB2FF] rounded-[6px] transition-all duration-300"
                    style={{ width: `${progressPct}%` }}
                  ></div>
                </div>
                <p className="text-[14px] text-[#9AA7C2] leading-relaxed">
                  Executing allowlisted action {Math.min(steps.length, currentStepIdx + 1)} of {steps.length}.
                </p>
                {runningStepName && (
                  <div className="text-[13px] font-mono text-[#5B8CFF] bg-[#121B2F] border border-[rgba(150,180,255,0.08)] rounded-[10px] p-2.5 truncate">
                    › {runningStepName}
                  </div>
                )}
              </div>
            )}

            {/* Done / Report State */}
            {isDone && (
              <div className="flex flex-col gap-4 py-1">
                <div className="w-[72px] h-[72px] rounded-full bg-[rgba(79,214,156,0.12)] grid place-items-center text-[#4FD69C] mx-auto mt-2 check-svg">
                  <svg
                    viewBox="0 0 24 24"
                    className="w-[34px] h-[34px] stroke-current fill-none stroke-[2.4] stroke-linecap-round stroke-linejoin-round"
                  >
                    <path d="M5 12.5l4.5 4.5L19 7.5" />
                  </svg>
                </div>

                <div className="text-center">
                  <div className="font-bold text-[18px] text-[#F3F6FC]">
                    Repair run complete
                  </div>
                  <p className="text-[13px] text-[#9AA7C2] mt-1">
                    {executionEvent.report.finalDiagnosis}
                  </p>
                </div>

                {/* Log excerpt */}
                <div className="rounded-[12px] bg-[#0D1424] border border-[rgba(150,180,255,0.08)] p-3 text-[11px] mono text-[#9AA7C2] leading-relaxed max-h-[160px] overflow-y-auto">
                  {executionEvent.report.stepRecords.map((rec) => (
                    <div key={rec.stepId} className="flex items-center gap-2 py-0.5">
                      <span
                        className={
                          rec.status === 'COMPLETED'
                            ? 'text-[#4FD69C]'
                            : rec.status === 'SKIPPED'
                            ? 'text-[#5E6B88]'
                            : 'text-[#FF5A6A]'
                        }
                      >
                        [{rec.status === 'COMPLETED' ? 'OK' : rec.status}]
                      </span>
                      <span className="truncate flex-1">{rec.title}</span>
                      <span className="text-[#5E6B88]">{rec.durationMs}ms</span>
                    </div>
                  ))}
                  <div className="text-[#4FD69C] pt-1">
                    Verdict: {executionEvent.report.result}
                  </div>
                </div>
              </div>
            )}

            {/* Idle State */}
            {!isRunning && !isDone && (
              <p className="text-[14px] text-[#9AA7C2] leading-relaxed py-2">
                Ready. Nothing runs until you confirm each step through the safety prompt.
              </p>
            )}
          </div>

          {/* Interactive User Prompt Modal Overlay if paused waiting for user */}
          {executionEvent.type === 'PromptUserAction' && (
            <div className="p-4 rounded-[14px] bg-[#1A2540] border border-[#5B8CFF]/40 shadow-lg flex flex-col gap-3 anim-rise">
              <div className="flex items-center gap-2 text-[#F2B866] font-semibold text-[14px]">
                <span>⚠</span>
                <span>{executionEvent.title}</span>
              </div>
              <p className="text-[13px] text-[#F3F6FC] leading-relaxed">
                {executionEvent.message}
              </p>

              {executionEvent.steps && executionEvent.steps.length > 0 && (
                <div className="flex flex-col gap-2 my-1">
                  {executionEvent.steps.map((st, i) => (
                    <div
                      key={i}
                      className="flex items-start gap-2 text-[12px] text-[#9AA7C2] bg-[#121B2F] p-2 rounded-lg"
                    >
                      <span className="w-5 h-5 rounded-full bg-[#5B8CFF] text-[#060A14] font-bold grid place-items-center flex-shrink-0 text-[10px]">
                        {i + 1}
                      </span>
                      <span>{st}</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex items-center gap-2 pt-2">
                <button
                  onClick={() => onSubmitUserAction(true)}
                  className="mahi-btn mahi-btn-primary mahi-btn-sm flex-1"
                >
                  Continue / Confirm
                </button>
                <button
                  onClick={() => onSubmitUserAction(false)}
                  className="mahi-btn mahi-btn-ghost mahi-btn-sm flex-1"
                >
                  Skip
                </button>
              </div>
            </div>
          )}

          {/* Primary Action Button */}
          <div className="mt-auto pt-3">
            {!isRunning && !isDone && (
              <button
                onClick={() => onRequestRun(tool)}
                className="mahi-btn mahi-btn-primary w-full"
              >
                Start Repair Run
              </button>
            )}

            {isRunning && (
              <button disabled className="mahi-btn mahi-btn-ghost w-full">
                Running…
              </button>
            )}

            {isDone && (
              <button
                onClick={onResetRun}
                className="mahi-btn mahi-btn-ghost w-full"
              >
                Run again
              </button>
            )}
          </div>
        </section>
      </div>
    </div>
  );
};
