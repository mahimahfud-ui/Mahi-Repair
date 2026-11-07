"use client";

import { FormEvent, useState, useTransition } from 'react';
import { ShieldCheck, Cloud, ArrowRight } from 'lucide-react';
import { requestOwnerSignInLink } from '@/app/actions';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, startTransition] = useTransition();
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setMessage('');

    startTransition(async () => {
      const result = await requestOwnerSignInLink(email);
      if (result.ok) {
        setSent(true);
        setMessage(result.message);
      } else {
        setError(result.message);
      }
    });
  }

  return (
    <main className="min-h-screen grid place-items-center p-5 bg-[radial-gradient(ellipse_at_top,#321214_0%,#090a0d_48%)]">
      <section className="panel w-full max-w-[420px] p-8 shadow-2xl shadow-black/40">
        <div className="flex items-center gap-3 mb-8">
          <div className="rounded-xl bg-red-500/15 p-3 text-red-400"><Cloud size={22} /></div>
          <div>
            <div className="font-semibold tracking-tight">Mahi Repair Cloud</div>
            <div className="text-xs muted">OWNER CONTROL CENTER</div>
          </div>
        </div>

        <div className="mb-6">
          <div className="mb-2 flex items-center gap-2 text-sm text-red-300"><ShieldCheck size={16} /> Private owner access</div>
          <h1 className="text-2xl font-semibold tracking-tight">Sign in securely</h1>
          <p className="muted text-sm mt-2">
            Use the owner email already registered in Supabase. Member accounts cannot enter this console.
          </p>
        </div>

        {sent ? (
          <div className="rounded-xl border border-emerald-900 bg-emerald-950/30 p-4 text-sm text-emerald-200" role="status">
            {message}
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            <label className="block text-sm">
              Owner email
              <input
                className="field mt-2"
                type="email"
                autoComplete="email"
                inputMode="email"
                maxLength={254}
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="owner@example.com"
              />
            </label>
            <button className="btn btn-primary w-full" disabled={busy}>
              {busy ? 'Sending…' : 'Email secure sign-in link'}
              <ArrowRight size={16} />
            </button>
          </form>
        )}

        {error && <p className="mt-4 text-sm text-red-400" role="alert">{error}</p>}
        <p className="mt-8 text-[11px] muted">
          Private administration · Supabase verifies identity and the server verifies Owner status
        </p>
      </section>
    </main>
  );
}
