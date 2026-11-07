import React, { useState } from 'react';
import { Cloud, X, Lock, CheckCircle2 } from 'lucide-react';
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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-[6px] p-4 overflow-y-auto anim-rise"
      onClick={(e) => {
        if (e.target === e.currentTarget) onDismiss();
      }}
    >
      <div
        className="w-full max-w-lg rounded-[22px] border border-[rgba(150,180,255,0.16)] bg-[#0D1424] p-6 sm:p-7 shadow-[0_24px_60px_rgba(0,0,0,0.6)] max-h-[90vh] flex flex-col"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[rgba(150,180,255,0.08)]">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-[12px] bg-[rgba(91,140,255,0.14)] text-[#5B8CFF] grid place-items-center flex-shrink-0">
              <Cloud size={20} />
            </div>
            <div>
              <h2 className="text-[17px] font-bold text-[#F3F6FC] tracking-tight">
                Mahi Cloud Backend
              </h2>
              <p className="text-[12px] text-[#9AA7C2]">
                Supabase API & Cryptographic Status
              </p>
            </div>
          </div>
          <button
            onClick={onDismiss}
            className="w-8 h-8 rounded-full bg-[#121B2F] border border-[rgba(150,180,255,0.08)] text-[#9AA7C2] hover:text-[#F3F6FC] grid place-items-center transition-colors"
            aria-label="Close"
          >
            <X size={16} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSave} className="overflow-y-auto py-4 space-y-4 flex-1">
          {/* Cryptographic Badge */}
          <div className="flex items-center gap-3 rounded-[14px] border border-[rgba(79,214,156,0.25)] bg-[rgba(79,214,156,0.08)] p-3.5 text-xs">
            <div className="w-8 h-8 rounded-lg bg-[rgba(79,214,156,0.15)] text-[#4FD69C] grid place-items-center shrink-0">
              <Lock size={16} />
            </div>
            <div>
              <div className="font-semibold text-[#F3F6FC]">
                RSA-SHA256 Signature Verification
              </div>
              <div className="text-[11px] text-[#4FD69C] mt-0.5">
                Owner public key active • Native JCA & WebCrypto enforcement
              </div>
            </div>
          </div>

          <div>
            <label className="block text-[12px] font-semibold text-[#9AA7C2] uppercase tracking-wider mb-1.5">
              SUPABASE_URL
            </label>
            <input
              type="text"
              value={supabaseUrl}
              onChange={(e) => setSupabaseUrl(e.target.value)}
              placeholder="https://xyzcompany.supabase.co"
              className="w-full h-11 rounded-[12px] border border-[rgba(150,180,255,0.16)] bg-[#121B2F] px-3.5 text-[13px] text-[#F3F6FC] focus:border-[#5B8CFF] focus:shadow-[0_0_0_4px_rgba(91,140,255,0.14)] mono"
            />
          </div>

          <div>
            <label className="block text-[12px] font-semibold text-[#9AA7C2] uppercase tracking-wider mb-1.5">
              SUPABASE_ANON_KEY
            </label>
            <textarea
              rows={3}
              value={supabaseAnonKey}
              onChange={(e) => setSupabaseAnonKey(e.target.value)}
              placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
              className="w-full rounded-[12px] border border-[rgba(150,180,255,0.16)] bg-[#121B2F] px-3.5 py-2.5 text-[12px] text-[#F3F6FC] focus:border-[#5B8CFF] focus:shadow-[0_0_0_4px_rgba(91,140,255,0.14)] mono resize-none"
            />
          </div>

          <div>
            <label className="block text-[12px] font-semibold text-[#9AA7C2] uppercase tracking-wider mb-1.5">
              TRUSTED OWNER PUBLIC KEY (PEM)
            </label>
            <div className="rounded-[12px] border border-[rgba(150,180,255,0.08)] bg-[#060A14] p-3 text-[10px] font-mono text-[#5E6B88] max-h-24 overflow-y-auto whitespace-pre">
              {config.publicKeyPem}
            </div>
          </div>

          <div className="pt-2">
            <button
              type="submit"
              className="mahi-btn mahi-btn-primary w-full flex items-center justify-center gap-2"
            >
              {savedNotice ? (
                <>
                  <CheckCircle2 size={16} />
                  <span>SAVED & APPLIED</span>
                </>
              ) : (
                <span>Save Configuration</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
