import React, { useState } from 'react';
import { Mail, Key, ShieldCheck, ArrowRight, Sparkles } from 'lucide-react';
import { AuthSession } from '../types';

interface AuthScreenProps {
  onLogin: (session: AuthSession) => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState<'email' | 'otp'>('email');
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSendCode = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setMessage('Please enter a valid email address.');
      return;
    }

    setIsLoading(true);
    setMessage(null);
    setTimeout(() => {
      setIsLoading(false);
      setStep('otp');
      setMessage(`Access code sent to ${email}. Check your inbox.`);
    }, 600);
  };

  const handleVerifyOtp = (e: React.FormEvent) => {
    e.preventDefault();
    if (code.length < 4) {
      setMessage('Please enter at least 4 digits.');
      return;
    }

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      onLogin({
        isAuthenticated: true,
        email: email.trim(),
        role: email.toLowerCase().includes('owner') ? 'owner' : 'member',
        isOfflineMode: false,
      });
    }, 500);
  };

  const handleContinueOffline = () => {
    onLogin({
      isAuthenticated: true,
      email: 'offline.device@mahi.local',
      role: 'member',
      isOfflineMode: true,
    });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#090a0d] p-4 bg-[radial-gradient(ellipse_at_top,#2a1012_0%,#090a0d_60%)]">
      <div className="w-full max-w-md rounded-2xl border border-[#252834] bg-[#101218] p-8 shadow-2xl shadow-black/80">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center">
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-[#161822] border-2 border-red-500/50 shadow-inner overflow-hidden p-3">
            <img
              src="/ic_launcher_logo.png"
              alt="Mahi Repair"
              className="h-full w-full object-contain"
              onError={(e) => {
                // Fallback if image fails to load
                (e.target as HTMLElement).style.display = 'none';
              }}
            />
          </div>

          <h1 className="mt-4 text-2xl font-bold tracking-tight text-white">
            Mahi Repair
          </h1>
          <p className="mt-1 text-xs font-semibold text-red-500 uppercase tracking-widest">
            Personal Android Repair Toolkit
          </p>

          <div className="mt-3 flex items-center gap-1.5 rounded-full border border-[#252834] bg-[#14161f] px-3 py-1 text-[11px] text-gray-400">
            <ShieldCheck size={13} className="text-red-400" />
            <span>Private Toolkit for Invited Family & Friends</span>
          </div>
        </div>

        {/* Auth Box */}
        <div className="mt-8 rounded-xl border border-[#222530] bg-[#14161f] p-5">
          {step === 'email' ? (
            <form onSubmit={handleSendCode} className="space-y-4">
              <div>
                <h2 className="text-sm font-bold text-gray-100">
                  Sign In with Invitation
                </h2>
                <p className="mt-1 text-[11px] text-gray-400 leading-relaxed">
                  Enter the email address invited by the Owner to receive a
                  one-time access code.
                </p>
              </div>

              <div>
                <label className="block text-[11px] font-medium text-gray-400 mb-1">
                  Invited Email Address
                </label>
                <div className="relative">
                  <Mail
                    size={15}
                    className="absolute left-3 top-3 text-gray-500"
                  />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="family@example.com"
                    className="w-full rounded-xl border border-[#2c303e] bg-[#0c0e14] pl-9 pr-3 py-2.5 text-xs text-gray-100 placeholder-gray-600 focus:border-red-500 focus:outline-none"
                  />
                </div>
              </div>

              {message && (
                <p className="text-xs text-red-400 text-center">{message}</p>
              )}

              <button
                type="submit"
                disabled={isLoading}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 py-3 text-xs font-bold text-white shadow-md shadow-red-950/40 transition-colors disabled:opacity-50"
              >
                {isLoading ? (
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <>
                    <span>SEND ACCESS CODE</span>
                    <ArrowRight size={14} />
                  </>
                )}
              </button>
            </form>
          ) : (
            <form onSubmit={handleVerifyOtp} className="space-y-4">
              <div>
                <h2 className="text-sm font-bold text-gray-100">
                  Enter 6-Digit Code
                </h2>
                <p className="mt-1 text-[11px] text-gray-400">
                  We sent an access code to{' '}
                  <span className="text-gray-200 font-medium">{email}</span>.
                </p>
              </div>

              <div>
                <label className="block text-[11px] font-medium text-gray-400 mb-1">
                  6-Digit Access Code
                </label>
                <div className="relative">
                  <Key
                    size={15}
                    className="absolute left-3 top-3 text-gray-500"
                  />
                  <input
                    type="text"
                    maxLength={6}
                    required
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                    placeholder="123456"
                    className="w-full rounded-xl border border-[#2c303e] bg-[#0c0e14] pl-9 pr-3 py-2.5 text-xs text-gray-100 font-mono tracking-widest placeholder-gray-600 focus:border-red-500 focus:outline-none"
                  />
                </div>
              </div>

              {message && (
                <p className="text-xs text-gray-300 text-center">{message}</p>
              )}

              <button
                type="submit"
                disabled={isLoading}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 py-3 text-xs font-bold text-white shadow-md shadow-red-950/40 transition-colors disabled:opacity-50"
              >
                {isLoading ? (
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <span>VERIFY & LOAD TOOLS</span>
                )}
              </button>

              <button
                type="button"
                onClick={() => setStep('email')}
                className="w-full py-1.5 text-center text-[11px] text-gray-400 hover:text-gray-200 transition-colors"
              >
                Use a different email
              </button>
            </form>
          )}
        </div>

        {/* Offline Demo Launch */}
        <div className="mt-6">
          <button
            onClick={handleContinueOffline}
            className="flex w-full items-center justify-center gap-2 rounded-xl border border-[#282b37] bg-[#14161f] hover:bg-[#1a1c26] py-3 text-xs font-medium text-gray-300 transition-colors"
          >
            <Sparkles size={14} className="text-amber-400" />
            <span>Launch Verified Offline Toolkit</span>
          </button>
          <p className="mt-2 text-center text-[11px] text-gray-500">
            Instant execution of 5 bundled Owner-signed recipes without server auth.
          </p>
        </div>
      </div>
    </div>
  );
};
