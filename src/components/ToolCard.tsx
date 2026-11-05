import React, { useState } from 'react';
import {
  Wrench,
  ChevronDown,
  ChevronUp,
  Package,
  ShieldCheck,
  Code2,
} from 'lucide-react';
import { RepairTool } from '../types';

interface ToolCardProps {
  tool: RepairTool;
  onRun: (tool: RepairTool) => void;
}

export const ToolCard: React.FC<ToolCardProps> = ({ tool, onRun }) => {
  const [showDetails, setShowDetails] = useState(false);

  return (
    <div className="group rounded-2xl border border-[#222530] bg-[#14161f] p-5 shadow-lg transition-all duration-200 hover:border-[#353948] hover:bg-[#161822]">
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-bold text-gray-100 group-hover:text-white">
              {tool.name}
            </h3>
            <span className="rounded-md border border-[#2e3240] bg-[#1c1f2b] px-2 py-0.5 text-[10px] font-mono font-medium text-gray-300">
              v{tool.version}
            </span>
            {tool.isNew && (
              <span className="rounded-md bg-red-500/20 px-2 py-0.5 text-[10px] font-semibold text-red-400 border border-red-500/30">
                NEW
              </span>
            )}
            <span className="rounded-md bg-blue-500/10 px-2 py-0.5 text-[10px] font-medium text-blue-400 border border-blue-500/20">
              {tool.category}
            </span>
          </div>

          <p className="mt-2 text-xs text-gray-300 leading-relaxed">
            {tool.description}
          </p>

          <div className="mt-3 rounded-lg border border-[#222530] bg-[#0e1017] p-2.5 text-[11px] text-gray-400">
            <span className="font-semibold text-gray-300">Symptoms: </span>
            {tool.problemDescription}
          </div>
        </div>

        <div className="sm:self-center shrink-0">
          <button
            onClick={() => onRun(tool)}
            className="flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 active:scale-98 px-5 py-2.5 text-xs font-bold text-white shadow-md shadow-red-950/40 transition-all cursor-pointer"
          >
            <Wrench size={14} />
            <span>RUN REPAIR</span>
          </button>
        </div>
      </div>

      {/* Footer tags and details toggle */}
      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-[#1d202a] pt-3 text-[11px] text-gray-500">
        <div className="flex items-center gap-3">
          {tool.targetPackage && (
            <div className="flex items-center gap-1 font-mono text-[10px]">
              <Package size={12} className="text-gray-400" />
              <span>{tool.targetPackage}</span>
            </div>
          )}
          <div className="flex items-center gap-1 text-[10px] text-emerald-400">
            <ShieldCheck size={12} />
            <span>Signed by Owner</span>
          </div>
        </div>

        <button
          onClick={() => setShowDetails(!showDetails)}
          className="flex items-center gap-1 text-[11px] text-gray-400 hover:text-gray-200 transition-colors"
        >
          <Code2 size={12} />
          <span>{showDetails ? 'Hide Recipe' : 'View Recipe'}</span>
          {showDetails ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </button>
      </div>

      {/* Expandable recipe payload preview */}
      {showDetails && (
        <div className="mt-3 rounded-xl border border-[#252834] bg-[#0c0e14] p-3 animate-fade-in">
          <div className="flex items-center justify-between pb-2 mb-2 border-b border-[#1c1f2b] text-[10px] text-gray-400">
            <span className="font-mono">DSL Payload & RSA-SHA256 Signature</span>
            <span className="text-emerald-400 font-mono">Status: Verified</span>
          </div>
          <pre className="max-h-48 overflow-y-auto font-mono text-[10px] text-gray-300 whitespace-pre-wrap leading-tight">
            {tool.rawSignedPayload || '// No payload available'}
          </pre>
        </div>
      )}
    </div>
  );
};
