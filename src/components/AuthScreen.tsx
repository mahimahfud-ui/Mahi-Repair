import React from 'react';
import { Sparkles, ShieldCheck } from 'lucide-react';
import { AuthSession } from '../types';

interface AuthScreenProps {
  onLogin: (session: AuthSession) => void;
}

/** Local demo entry only. The root Vite preview does not authenticate users. */
export const AuthScreen: React.FC<AuthScreenProps> = ({ onLogin }) => {
  const continueToPreview = () => onLogin({
    isAuthenticated: true,
    email: 'demo.member@mahi.local',
    role: 'member',
    isOfflineMode: true,
  });

  return (
    <main className="min-h-screen grid place-items-center bg-[#090a0d] p-5 text-gray-200">
      <section className="w-full max-w-md rounded-2xl border border-[#252834] bg-[#101218] p-8 text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-500/15 text-red-400"><ShieldCheck size={26} /></div>
        <h1 className="mt-5 text-xl font-semibold">Mahi Repair local preview</h1>
        <p className="mt-3 text-sm leading-6 text-gray-400">
          This retired web prototype has no Supabase sign-in and no Owner or Admin Console access. Continue only to view the local member preview.
        </p>
        <button onClick={continueToPreview} className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-red-600 px-4 py-3 text-sm font-semibold text-white hover:bg-red-500">
          <Sparkles size={16} /> Continue to local preview
        </button>
      </section>
    </main>
  );
};
