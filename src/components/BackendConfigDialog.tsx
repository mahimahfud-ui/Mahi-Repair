import React, { useState } from 'react';
import { Cloud, X, Lock, RefreshCw, CheckCircle2 } from 'lucide-react';
import { BackendConfig } from '../types';

interface BackendConfigDialogProps {
  config: BackendConfig;
  onSave: (newConfig: BackendConfig) => void;
  onDismiss: () => void;
}

export const BackendConfigDialog: React.FC<BackendConfigDialogProps> = ({
  config,
  onSave,
  onDismiss,
}) => {
  const [supabaseUrl, setSupabaseUrl] = useState(config.supabaseUrl);
  const [supabaseAnonKey, setSupabaseAnonKey] = useState(
    config.supabaseAnonKey
  );
  const [savedNotice, setSavedNotice] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      ...config,
      supabaseUrl,
      supabaseAnonKey,
    });
    setSavedNotice(true);
    setTimeout(() => {
      setSavedNotice(false);
      onDismiss();
    }, 800);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget) onDismiss();
      }}
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-[#252834] bg-[#11131a] p-6 shadow-2xl shadow-black/80 max-h-[90vh] flex flex-col"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#20232e]">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#1e212b] text-red-400">
              <Cloud size={18} />
            </div>
            <div>
              <h2 className="text-sm font-bold text-gray-100 tracking-tight">
                Mahi Cloud Backend
              </h2>
              <p className="text-[11px] text-gray-400">
                Supabase API & Cryptographic Status
              </p>
            </div>
          </div>
          <button
            onClick={onDismiss}
            className="rounded-lg p-1 text-gray-400 hover:bg-[#20232e] hover:text-gray-200 transition-colors"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSave} className="overflow-y-auto py-4 space-y-4 flex-1">
          {/* Cryptographic Badge */}
          <div className="flex items-center gap-3 rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-3.5 text-xs">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400 shrink-0">
              <Lock size={16} />
            </div>
            <div>
              <div className="font-semibold text-gray-100">
                RSA-SHA256 Signature Verification
              </div>
              <div className="text-[11px] text-emerald-400">
                Owner public key active • Native JCA & WebCrypto enforcement
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              SUPABASE_URL
            </label>
            <input
              type="text"
              value={supabaseUrl}
              onChange={(e) => setSupabaseUrl(e.target.value)}
              placeholder="https://xyzcompany.supabase.co"
              className="w-full rounded-xl border border-[#2b2e3c] bg-[#14161f] px-3.5 py-2.5 text-xs text-gray-200 focus:border-red-500 focus:outline-none font-mono"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              SUPABASE_ANON_KEY
            </label>
            <textarea
              rows={3}
              value={supabaseAnonKey}
              onChange={(e) => setSupabaseAnonKey(e.target.value)}
              placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
              className="w-full rounded-xl border border-[#2b2e3c] bg-[#14161f] px-3.5 py-2.5 text-xs text-gray-200 focus:border-red-500 focus:outline-none font-mono resize-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
              TRUSTED OWNER PUBLIC KEY (PEM)
            </label>
            <div className="rounded-xl border border-[#222530] bg-[#0c0e14] p-3 text-[10px] font-mono text-gray-400 max-h-24 overflow-y-auto whitespace-pre">
              {config.publicKeyPem}
            </div>
          </div>

          <button
            type="submit"
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 py-3 text-xs font-bold text-white shadow-lg shadow-red-950/40 transition-colors"
          >
            {savedNotice ? (
              <>
                <CheckCircle2 size={15} />
                <span>SAVED & APPLIED</span>
              </>
            ) : (
              <>
                <RefreshCw size={15} />
                <span>SAVE & SYNCHRONIZE</span>
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
