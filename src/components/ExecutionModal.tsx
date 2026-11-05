import React from 'react';
import {
  CheckCircle,
  XCircle,
  AlertTriangle,
  Lock,
  Shield,
  X,
  Info,
  Clock,
  Check,
} from 'lucide-react';
import { ExecutionEvent, RepairRunReport, StepExecutionRecord } from '../types';

interface ExecutionModalProps {
  event: ExecutionEvent;
  onSubmitUserAction: (approved: boolean) => void;
  onDismiss: () => void;
}

export const ExecutionModal: React.FC<ExecutionModalProps> = ({
  event,
  onSubmitUserAction,
  onDismiss,
}) => {
  if (event.type === 'Idle') return null;

  const isFinished = event.type === 'Finished';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget && isFinished) onDismiss();
      }}
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-[#252834] bg-[#11131a] p-6 shadow-2xl shadow-black/80 max-h-[92vh] flex flex-col"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#20232e]">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-red-500/15 border border-red-500/30 text-red-400">
              <Shield size={18} />
            </div>
            <div>
              <h2 className="text-sm font-bold text-gray-100 tracking-tight">
                Mahi Repair Engine
              </h2>
              <p className="text-[11px] text-gray-400">
                Verified Native Execution
              </p>
            </div>
          </div>
          {isFinished && (
            <button
              onClick={onDismiss}
              className="rounded-lg p-1 text-gray-400 hover:bg-[#20232e] hover:text-gray-200 transition-colors"
              aria-label="Close"
            >
              <X size={18} />
            </button>
          )}
        </div>

        {/* Content Body */}
        <div className="overflow-y-auto py-5 space-y-4 flex-1 pr-1">
          {event.type === 'VerifyingSignature' && (
            <div className="py-8 flex flex-col items-center text-center">
              <div className="h-10 w-10 animate-spin rounded-full border-3 border-red-500 border-t-transparent" />
              <h3 className="mt-4 text-sm font-semibold text-gray-100">
                Verifying Digital Signature...
              </h3>
              <p className="mt-2 text-xs text-gray-400 max-w-sm">
                Validating RSA-SHA256 signature for{' '}
                <span className="text-gray-200 font-medium">
                  '{event.toolName}'
                </span>{' '}
                against the Owner's trusted public key.
              </p>
            </div>
          )}

          {event.type === 'SignatureFailed' && (
            <div className="py-6 flex flex-col items-center text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-500/15 text-red-400">
                <Lock size={24} />
              </div>
              <h3 className="mt-4 text-base font-bold text-red-400">
                Signature Verification Failed
              </h3>
              <p className="mt-2 text-xs text-gray-400 max-w-sm">
                {event.reason}
              </p>
              <button
                onClick={onDismiss}
                className="mt-6 rounded-xl bg-red-600 hover:bg-red-500 px-5 py-2 text-xs font-semibold text-white transition-colors"
              >
                Close
              </button>
            </div>
          )}

          {event.type === 'IncompatibleDevice' && (
            <div className="py-6 flex flex-col items-center text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-500/15 text-amber-400">
                <AlertTriangle size={24} />
              </div>
              <h3 className="mt-4 text-base font-bold text-amber-400">
                Device Incompatible
              </h3>
              <p className="mt-2 text-xs text-gray-400 max-w-sm">
                This recipe requires Android API {event.requiredSdk}+, but your
                environment is API {event.currentSdk}.
              </p>
              <button
                onClick={onDismiss}
                className="mt-6 rounded-xl bg-[#252834] hover:bg-[#303444] px-5 py-2 text-xs font-semibold text-gray-200 transition-colors"
              >
                Close
              </button>
            </div>
          )}

          {event.type === 'Running' && (
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between">
                  <h3 className="text-base font-bold text-gray-100">
                    {event.tool.name}
                  </h3>
                  <span className="text-xs font-mono text-gray-400">
                    Step {event.currentStepIndex + 1} of {event.totalSteps}
                  </span>
                </div>

                {/* Progress bar */}
                <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-[#20232e]">
                  <div
                    className="h-full bg-red-500 transition-all duration-300"
                    style={{
                      width: `${
                        ((event.currentStepIndex + 1) / event.totalSteps) * 100
                      }%`,
                    }}
                  />
                </div>
              </div>

              {/* Step records list */}
              <div className="space-y-2 mt-4">
                {event.stepRecords.map((record) => (
                  <StepRecordItem key={record.stepId} record={record} />
                ))}

                {/* Active step */}
                <div className="flex items-center gap-3 rounded-xl border border-red-500/40 bg-[#1c1f2a] p-3 shadow-sm">
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-red-500 border-t-transparent shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="text-xs font-semibold text-gray-100">
                      {event.currentStep.name}
                    </div>
                    <div className="text-[11px] text-red-400 truncate">
                      {event.currentStep.description ||
                        'Executing native diagnosis...'}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {event.type === 'PromptUserAction' && (
            <div className="space-y-4 animate-scale-in">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-amber-500/15 text-amber-400">
                  {event.actionType === 'GUIDE' ? (
                    <Info size={18} />
                  ) : (
                    <AlertTriangle size={18} />
                  )}
                </div>
                <h3 className="text-base font-bold text-gray-100">
                  {event.title}
                </h3>
              </div>

              <p className="text-xs text-gray-300 leading-relaxed">
                {event.message}
              </p>

              {event.steps && event.steps.length > 0 && (
                <div className="space-y-2 pt-2">
                  {event.steps.map((stepDesc, idx) => (
                    <div
                      key={idx}
                      className="flex items-start gap-3 rounded-xl border border-[#252834] bg-[#14161f] p-3 text-xs"
                    >
                      <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-red-600 text-[10px] font-bold text-white">
                        {idx + 1}
                      </div>
                      <span className="text-gray-200 leading-snug">
                        {stepDesc}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3 pt-3">
                <button
                  onClick={() => onSubmitUserAction(false)}
                  className="rounded-xl border border-[#2b2e3c] bg-[#161822] hover:bg-[#1f2230] py-2.5 text-xs font-medium text-gray-300 transition-colors"
                >
                  Skip / Cancel
                </button>
                <button
                  onClick={() => onSubmitUserAction(true)}
                  className="rounded-xl bg-red-600 hover:bg-red-500 py-2.5 text-xs font-bold text-white transition-colors"
                >
                  {event.actionType === 'GUIDE'
                    ? "I've Done This"
                    : 'Allow & Continue'}
                </button>
              </div>
            </div>
          )}

          {event.type === 'Finished' && (
            <FinishedReportSection report={event.report} onDismiss={onDismiss} />
          )}
        </div>
      </div>
    </div>
  );
};

const StepRecordItem: React.FC<{ record: StepExecutionRecord }> = ({
  record,
}) => {
  const getIcon = () => {
    switch (record.status) {
      case 'COMPLETED':
        return <CheckCircle size={16} className="text-emerald-400 shrink-0" />;
      case 'FAILED':
        return <XCircle size={16} className="text-red-400 shrink-0" />;
      case 'CANCELLED':
        return <X size={16} className="text-gray-500 shrink-0" />;
      default:
        return <Info size={16} className="text-gray-400 shrink-0" />;
    }
  };

  return (
    <div className="flex items-center gap-3 rounded-xl border border-[#20232e] bg-[#14161f] p-3 text-xs">
      {getIcon()}
      <div className="flex-1 min-w-0">
        <div className="font-medium text-gray-200">{record.title}</div>
        <div className="text-[11px] text-gray-400 truncate">
          {record.message}
        </div>
      </div>
      <div className="flex items-center gap-1 text-[10px] text-gray-500 font-mono">
        <Clock size={11} />
        {record.durationMs}ms
      </div>
    </div>
  );
};

const FinishedReportSection: React.FC<{
  report: RepairRunReport;
  onDismiss: () => void;
}> = ({ report, onDismiss }) => {
  const isSuccess = report.result === 'SUCCESS';
  const isPartial = report.result === 'PARTIAL';
  const isFailed = report.result === 'FAILED';

  const bannerColor = isSuccess
    ? 'border-emerald-500/40 bg-emerald-950/20 text-emerald-400'
    : isPartial
    ? 'border-amber-500/40 bg-amber-950/20 text-amber-400'
    : isFailed
    ? 'border-red-500/40 bg-red-950/20 text-red-400'
    : 'border-gray-600/40 bg-gray-900/30 text-gray-400';

  const IconComponent = isSuccess
    ? CheckCircle
    : isPartial
    ? AlertTriangle
    : isFailed
    ? XCircle
    : X;

  return (
    <div className="space-y-4">
      {/* Banner */}
      <div className={`flex items-center gap-3 rounded-xl border p-4 ${bannerColor}`}>
        <IconComponent size={24} className="shrink-0" />
        <div>
          <div className="text-xs font-bold tracking-wider uppercase">
            {report.result === 'SUCCESS'
              ? 'REPAIR SUCCESSFUL'
              : report.result === 'PARTIAL'
              ? 'PARTIALLY RESOLVED'
              : report.result === 'FAILED'
              ? 'DIAGNOSTIC DETECTED ISSUE'
              : 'REPAIR CANCELLED'}
          </div>
          <div className="text-[11px] opacity-80 mt-0.5">
            {report.toolName} (v{report.toolVersion})
          </div>
        </div>
      </div>

      {/* Diagnosis Verdict */}
      <div className="rounded-xl border border-[#20232e] bg-[#14161f] p-4">
        <h4 className="text-xs font-semibold text-gray-200 uppercase tracking-wider text-[11px]">
          Final Diagnosis & Verdict
        </h4>
        <p className="mt-1.5 text-xs text-gray-300 leading-relaxed">
          {report.finalDiagnosis}
        </p>
      </div>

      {/* Steps Breakdown */}
      <div>
        <div className="flex items-center justify-between text-xs text-gray-400 mb-2">
          <span>Step Results ({report.stepRecords.length})</span>
          <span>Duration: {report.endTime - report.startTime}ms</span>
        </div>
        <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
          {report.stepRecords.map((record) => (
            <StepRecordItem key={record.stepId} record={record} />
          ))}
        </div>
      </div>

      <button
        onClick={onDismiss}
        className="w-full rounded-xl bg-red-600 hover:bg-red-500 py-3 text-xs font-bold text-white transition-colors"
      >
        DONE
      </button>
    </div>
  );
};
