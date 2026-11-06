"use client";

import { FormEvent, useEffect, useState } from 'react';
import { ShieldCheck, Cloud, ArrowRight } from 'lucide-react';
import { requestSignInLink } from '@/app/actions';

const RESEND_COOLDOWN_SECONDS = 60;

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const timer = window.setTimeout(() => {
      setCooldownSeconds((remaining) => Math.max(0, remaining - 1));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [cooldownSeconds]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || cooldownSeconds > 0) return;

    setBusy(true);
    setError('');
    try {
      const result = await requestSignInLink(email);
      if (!result.ok) {
        if (result.reason === 'rate_limited') {
          setCooldownSeconds(RESEND_COOLDOWN_SECONDS);
          setError('Supabase is temporarily limiting sign-in emails. Wait 60 seconds before trying again. If this keeps happening, the Owner should review the Supabase Auth email and SMTP limits.');
        } else if (result.reason === 'configuration') {
          setError('Secure sign-in is not configured for this deployment yet. Please contact the Owner.');
        } else {
          setError('Unable to send a sign-in link right now. Please check the address and try again later.');
        }
        return;
      }

      setSent(true);
      setCooldownSeconds(RESEND_COOLDOWN_SECONDS);
    } catch {
      setError('Unable to send a sign-in link right now. Please try again later.');
    } finally {
      setBusy(false);
    }
  }

  const cooldownLabel = `${String(Math.floor(cooldownSeconds / 60)).padStart(2, '0')}:${String(cooldownSeconds % 60).padStart(2, '0')}`;

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
          <p className="muted text-sm mt-2">Use the owner email already registered in Supabase. Member accounts cannot enter this console.</p>
        </div>

        {sent && (
          <div role="status" aria-live="polite" className="mb-4 rounded-xl border border-emerald-900 bg-emerald-950/30 p-4 text-sm text-emerald-200">
            Your request was accepted. If this address is eligible for owner sign-in, check your inbox and open the latest link on this device.
          </div>
        )}

        <form onSubmit={submit} className="space-y-4">
          <label className="block text-sm">
            Owner email
            <input
              className="field mt-2"
              type="email"
              autoComplete="email"
              required
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="owner@example.com"
            />
          </label>

          <button className="btn btn-primary w-full" disabled={busy || cooldownSeconds > 0}>
            {busy
              ? 'Sending…'
              : cooldownSeconds > 0
                ? `Resend available in ${cooldownLabel}`
                : sent
                  ? 'Resend sign-in link'
                  : 'Email secure sign-in link'}
            <ArrowRight size={16} />
          </button>
        </form>

        {error && <p role="alert" aria-live="polite" className="mt-4 text-sm text-red-400">{error}</p>}
        <p className="mt-3 text-xs muted">For security, sign-in requests are limited. Please do not repeatedly request new links.</p>
        <p className="mt-8 text-[11px] muted">Private administration · All access is verified server-side</p>
      </section>
    </main>
  );
}
