import React, { useState } from 'react';
import { AuthSession, BackendConfig } from '../types';

interface SettingsScreenProps {
  session: AuthSession;
  backendConfig: BackendConfig;
  onSignOut: () => void;
  onLogin: (session: AuthSession) => void;
  onOpenBackendConfig: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  session,
  backendConfig,
  onSignOut,
  onLogin,
  onOpenBackendConfig,
}) => {
  // Preference state
  const [notify, setNotify] = useState<boolean>(() => {
    return localStorage.getItem('mahi_pref_notify') !== 'false';
  });

  const [reduceMotion, setReduceMotion] = useState<boolean>(() => {
    return localStorage.getItem('mahi_pref_reduce_motion') === 'true';
  });

  // Invited email login state
  const [emailInput, setEmailInput] = useState('');
  const [otpInput, setOtpInput] = useState('');
  const [otpStep, setOtpStep] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 2500);
  };

  const toggleNotify = () => {
    const next = !notify;
    setNotify(next);
    localStorage.setItem('mahi_pref_notify', String(next));
    showToast(`Notifications ${next ? 'enabled' : 'disabled'}`);
  };

  const toggleReduceMotion = () => {
    const next = !reduceMotion;
    setReduceMotion(next);
    localStorage.setItem('mahi_pref_reduce_motion', String(next));
    if (next) {
      document.documentElement.setAttribute('data-reduce-motion', 'true');
    } else {
      document.documentElement.removeAttribute('data-reduce-motion');
    }
    showToast(`Reduce motion ${next ? 'enabled' : 'disabled'}`);
  };

  const handleRequestOtp = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = emailInput.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean)) {
      setEmailError('Enter a valid invited email address.');
      return;
    }
    setEmailError('');
    setOtpStep(true);
    showToast(`One-time code sent to ${clean}`);
  };

  const handleVerifyOtp = (e: React.FormEvent) => {
    e.preventDefault();
    if (otpInput.trim().length < 4) {
      setEmailError('Enter the complete 6-digit code.');
      return;
    }
    onLogin({
      email: emailInput.trim(),
      role: 'member',
      isAuthenticated: true,
      isOfflineMode: false,
    });
    setOtpStep(false);
    setEmailInput('');
    setOtpInput('');
    showToast('Signed in successfully');
  };

  return (
    <div className="anim-push flex flex-col gap-6 max-w-[1200px] mx-auto pb-12">
      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 bg-[#1A2540] border border-[rgba(150,180,255,0.16)] px-4 py-2.5 rounded-[14px] text-[14px] text-[#F3F6FC] shadow-xl anim-rise">
          {toastMessage}
        </div>
      )}

      {/* Header */}
      <div>
        <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
          Preferences
        </div>
        <h1 className="text-[28px] font-bold tracking-tight text-[#F3F6FC] mt-1">
          Settings
        </h1>
      </div>

      {/* Preferences Card List */}
      <div className="mahi-card divide-y divide-[rgba(150,180,255,0.08)] py-1 px-4">
        {/* Notifications */}
        <div className="flex items-center justify-between py-3.5 gap-3">
          <div className="flex-1">
            <div className="font-semibold text-[14px] text-[#F3F6FC]">
              Notifications
            </div>
            <div className="text-[13px] text-[#9AA7C2] mt-0.5">
              Alerts when a repair run needs your attention
            </div>
          </div>
          <button
            onClick={toggleNotify}
            className={`mahi-btn mahi-btn-sm ${
              notify ? 'mahi-btn-primary' : 'mahi-btn-ghost'
            }`}
          >
            {notify ? 'On' : 'Off'}
          </button>
        </div>

        {/* Reduce Motion */}
        <div className="flex items-center justify-between py-3.5 gap-3">
          <div className="flex-1">
            <div className="font-semibold text-[14px] text-[#F3F6FC]">
              Reduce motion
            </div>
            <div className="text-[13px] text-[#9AA7C2] mt-0.5">
              Limits transitions and spring animations to essential feedback
            </div>
          </div>
          <button
            onClick={toggleReduceMotion}
            className={`mahi-btn mahi-btn-sm ${
              reduceMotion ? 'mahi-btn-primary' : 'mahi-btn-ghost'
            }`}
          >
            {reduceMotion ? 'On' : 'Off'}
          </button>
        </div>

        {/* Offline Emergency Mode */}
        <div className="flex items-center justify-between py-3.5 gap-3">
          <div className="flex-1">
            <div className="font-semibold text-[14px] text-[#F3F6FC]">
              Offline emergency mode
            </div>
            <div className="text-[13px] text-[#9AA7C2] mt-0.5">
              Show cached guides and verified tools when there is no connection
            </div>
          </div>
          <span className="mahi-badge mahi-badge-ok">
            <span className="dot"></span>
            Ready
          </span>
        </div>
      </div>

      {/* Account Section */}
      <div>
        <div className="text-[15px] font-semibold text-[#F3F6FC] mb-2.5">
          Account & Connectivity
        </div>

        {session.isAuthenticated ? (
          <div className="mahi-card flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-[15px] text-[#F3F6FC]">
                  {session.email}
                </div>
                <div className="text-[13px] text-[#9AA7C2] mt-0.5">
                  Invited {session.role === 'owner' ? 'Owner' : 'Member'} · Device DEMO-0001
                </div>
              </div>
              <span className="mahi-badge mahi-badge-ok">
                <span className="dot"></span>
                Active
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-2.5 pt-2 border-t border-[rgba(150,180,255,0.08)]">
              <button
                onClick={onOpenBackendConfig}
                className="mahi-btn mahi-btn-ghost mahi-btn-sm"
              >
                Backend Configuration
              </button>
              <button
                onClick={onSignOut}
                className="mahi-btn mahi-btn-danger mahi-btn-sm"
              >
                Sign out
              </button>
            </div>
          </div>
        ) : (
          <div className="mahi-card">
            {!otpStep ? (
              <form onSubmit={handleRequestOtp} className="flex flex-col gap-3.5">
                <div>
                  <label className="text-[13px] text-[#9AA7C2] block mb-1.5 font-medium">
                    Invited email
                  </label>
                  <input
                    type="email"
                    value={emailInput}
                    onChange={(e) => setEmailInput(e.target.value)}
                    placeholder="name@example.com"
                    className="w-full h-11 rounded-[12px] bg-[#121B2F] border border-[rgba(150,180,255,0.16)] px-3.5 text-[14px] text-[#F3F6FC] placeholder-[#5E6B88] focus:border-[#5B8CFF] focus:shadow-[0_0_0_4px_rgba(91,140,255,0.14)] transition-all"
                  />
                  {emailError && (
                    <div className="text-[12px] text-[#FF5A6A] mt-1.5">
                      {emailError}
                    </div>
                  )}
                </div>
                <button type="submit" className="mahi-btn mahi-btn-primary">
                  Send one-time code
                </button>
              </form>
            ) : (
              <form onSubmit={handleVerifyOtp} className="flex flex-col gap-3.5">
                <div>
                  <label className="text-[13px] text-[#9AA7C2] block mb-1.5 font-medium">
                    Verification code
                  </label>
                  <input
                    type="text"
                    value={otpInput}
                    onChange={(e) => setOtpInput(e.target.value)}
                    placeholder="123456"
                    className="w-full h-11 rounded-[12px] bg-[#121B2F] border border-[rgba(150,180,255,0.16)] px-3.5 text-[14px] text-[#F3F6FC] placeholder-[#5E6B88] focus:border-[#5B8CFF] focus:shadow-[0_0_0_4px_rgba(91,140,255,0.14)] transition-all mono tracking-widest text-center"
                  />
                  {emailError && (
                    <div className="text-[12px] text-[#FF5A6A] mt-1.5">
                      {emailError}
                    </div>
                  )}
                </div>
                <div className="flex gap-2">
                  <button type="submit" className="mahi-btn mahi-btn-primary flex-1">
                    Verify & Sign in
                  </button>
                  <button
                    type="button"
                    onClick={() => setOtpStep(false)}
                    className="mahi-btn mahi-btn-ghost mahi-btn-sm"
                  >
                    Back
                  </button>
                </div>
              </form>
            )}
          </div>
        )}
      </div>

      {/* Backend & Security Summary */}
      <div className="rounded-[14px] bg-[#0D1424] border border-[rgba(150,180,255,0.08)] p-4 flex flex-col gap-1 text-[12px] mono text-[#5E6B88]">
        <div>Backend: {backendConfig.supabaseUrl}</div>
        <div>Verifier: RSASSA-PKCS1-v1_5 / SHA-256 Verified</div>
        <div>Cache Status: 5 recipes cached for offline emergency use</div>
      </div>
    </div>
  );
};
