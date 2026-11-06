"use client";

import { FormEvent, useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { ShieldCheck, Cloud, ArrowRight, AlertTriangle, CheckCircle2, Clock, RotateCcw } from 'lucide-react';
import { createClient } from '@/lib/supabase/browser';

const COOLDOWN_SECONDS = 60;
const SESSION_STORAGE_COOLDOWN_KEY = 'mahi_owner_login_cooldown_until';

function getAuthCallbackUrl(): string {
  // If running in browser and on a real domain (not localhost), use current origin
  if (typeof window !== 'undefined' && window.location.origin) {
    const origin = window.location.origin;
    if (!origin.includes('localhost') && !origin.includes('127.0.0.1')) {
      return `${origin}/auth/callback`;
    }
  }

  // Fallback to configured production URL if defined
  const configuredUrl = process.env.NEXT_PUBLIC_APP_URL || process.env.NEXT_PUBLIC_SITE_URL;
  if (configuredUrl && configuredUrl.trim()) {
    return `${configuredUrl.trim().replace(/\/$/, '')}/auth/callback`;
  }

  if (typeof window !== 'undefined') {
    return `${window.location.origin}/auth/callback`;
  }
  return '/auth/callback';
}

function LoginForm() {
  const searchParams = useSearchParams();
  const urlError = searchParams.get('error');

  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  // Restore cooldown from sessionStorage on mount
  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(SESSION_STORAGE_COOLDOWN_KEY);
      if (stored) {
        const expiry = parseInt(stored, 10);
        const remaining = Math.max(0, Math.ceil((expiry - Date.now()) / 1000));
        if (remaining > 0) setCooldown(remaining);
      }
    } catch {
      // Ignore
    }
  }, []);

  // Cooldown interval ticker
  useEffect(() => {
    if (cooldown <= 0) return;
    const interval = setInterval(() => {
      setCooldown((prev) => {
        if (prev <= 1) {
          try {
            sessionStorage.removeItem(SESSION_STORAGE_COOLDOWN_KEY);
          } catch {
            // Ignore
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldown]);

  const startCooldown = (seconds: number = COOLDOWN_SECONDS) => {
    setCooldown(seconds);
    try {
      sessionStorage.setItem(
        SESSION_STORAGE_COOLDOWN_KEY,
        String(Date.now() + seconds * 1000)
      );
    } catch {
      // Ignore
    }
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || cooldown > 0) return;

    setBusy(true);
    setError(null);

    try {
      const client = createClient();
      const redirectTo = getAuthCallbackUrl();

      const { error: signInError } = await client.auth.signInWithOtp({
        email: email.trim(),
        options: {
          shouldCreateUser: false,
          emailRedirectTo: redirectTo,
        },
      });

      if (signInError) {
        throw signInError;
      }

      setSent(true);
      startCooldown(COOLDOWN_SECONDS);
    } catch (err) {
      const rawMessage = err instanceof Error ? err.message : 'Unable to send sign-in link.';

      // Check for rate limit conditions
      if (
        rawMessage.toLowerCase().includes('rate limit') ||
        rawMessage.toLowerCase().includes('too many requests') ||
        (err as { status?: number })?.status === 429
      ) {
        setError(
          'Email rate limit reached. Supabase protects against email flooding by limiting how frequently sign-in emails can be sent. Please wait for the cooldown timer below before requesting another link.'
        );
        startCooldown(COOLDOWN_SECONDS);
      } else {
        setError(rawMessage);
      }
    } finally {
      setBusy(false);
    }
  }

  const getUrlErrorMessage = (errCode: string): string => {
    switch (errCode) {
      case 'forbidden':
        return 'Access denied: Only the authorized Owner account (mahinmhfd@gmail.com) can access this administration console. Member accounts are prohibited.';
      case 'unauthorized':
        return 'Your administrative session has expired or is invalid. Please sign in with your Owner email to continue.';
      case 'unconfirmed_email':
        return 'Your email address is not yet confirmed in Supabase Auth. Please confirm your email before signing in.';
      case 'missing_code':
        return 'The authentication link was missing required parameters. Please request a fresh sign-in link.';
      default:
        try {
          return decodeURIComponent(errCode);
        } catch {
          return errCode;
        }
    }
  };

  return (
    <section className="panel w-full max-w-[440px] p-8 shadow-2xl shadow-black/50">
      <div className="flex items-center gap-3 mb-8">
        <div className="rounded-xl bg-red-500/15 p-3 text-red-400">
          <Cloud size={24} />
        </div>
        <div>
          <div className="font-semibold tracking-tight text-white">Mahi Repair Cloud</div>
          <div className="text-[11px] font-mono text-red-400/90 tracking-wider">
            OWNER CONTROL CENTER
          </div>
        </div>
      </div>

      <div className="mb-6">
        <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-red-400 uppercase tracking-wider">
          <ShieldCheck size={16} /> Private owner access
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Sign in securely</h1>
        <p className="muted text-xs mt-2 leading-relaxed">
          Access is restricted exclusively to the authorized Owner (<span className="text-gray-300 font-mono">mahinmhfd@gmail.com</span>). Member accounts cannot enter this console.
        </p>
      </div>

      {urlError && (
        <div className="mb-5 rounded-xl border border-red-900/60 bg-red-950/30 p-4 text-xs text-red-300 flex items-start gap-2.5">
          <AlertTriangle size={16} className="text-red-400 shrink-0 mt-0.5" />
          <div className="leading-relaxed">{getUrlErrorMessage(urlError)}</div>
        </div>
      )}

      {sent ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-emerald-900/80 bg-emerald-950/30 p-4 text-xs text-emerald-200">
            <div className="flex items-center gap-2 font-semibold text-emerald-300 mb-1">
              <CheckCircle2 size={16} /> Secure sign-in link sent
            </div>
            <p className="mt-1 leading-relaxed">
              We sent a secure magic link to <strong className="text-white">{email}</strong>. Open it on this device to enter the Owner Console.
            </p>
          </div>

          <div className="p-3 rounded-lg border border-[#252830] bg-[#0c0e12] text-xs text-gray-400 flex items-center justify-between">
            <span className="flex items-center gap-1.5">
              <Clock size={14} className="text-gray-500" />
              Resend cooldown:
            </span>
            <span className="font-mono tabular-nums text-white">
              {cooldown > 0 ? `${cooldown}s` : 'Ready'}
            </span>
          </div>

          <button
            type="button"
            onClick={() => {
              setSent(false);
              setError(null);
            }}
            disabled={cooldown > 0}
            className="btn btn-secondary w-full text-xs"
          >
            <RotateCcw size={14} />
            {cooldown > 0 ? `Wait ${cooldown}s to send again` : 'Send another link or use different email'}
          </button>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <label className="block text-xs font-medium text-gray-300">
            Owner Email Address
            <input
              className="field mt-2 text-sm"
              type="email"
              autoComplete="email"
              required
              disabled={busy || cooldown > 0}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="mahinmhfd@gmail.com"
            />
          </label>

          {error && (
            <div className="rounded-lg border border-red-900/50 bg-red-950/20 p-3 text-xs text-red-300 flex items-start gap-2">
              <AlertTriangle size={15} className="text-red-400 shrink-0 mt-0.5" />
              <div className="leading-relaxed">{error}</div>
            </div>
          )}

          <button
            className="btn btn-primary w-full text-sm font-semibold"
            disabled={busy || cooldown > 0 || !email.trim()}
          >
            {busy ? (
              'Sending secure link…'
            ) : cooldown > 0 ? (
              <span className="flex items-center gap-2">
                <Clock size={15} /> Rate limited — wait {cooldown}s
              </span>
            ) : (
              <span className="flex items-center justify-center gap-2">
                Email secure sign-in link <ArrowRight size={16} />
              </span>
            )}
          </button>
        </form>
      )}

      <div className="mt-8 border-t border-[#252830] pt-4 text-center">
        <p className="text-[11px] text-gray-500">
          Mahi Repair Cloud Security Boundary · Server-Enforced RBAC
        </p>
      </div>
    </section>
  );
}

export default function LoginPage() {
  return (
    <main className="min-h-screen grid place-items-center p-5 bg-[radial-gradient(ellipse_at_top,#321214_0%,#090a0d_48%)]">
      <Suspense fallback={<div className="panel p-8 text-xs text-gray-400">Loading console authentication…</div>}>
        <LoginForm />
      </Suspense>
    </main>
  );
}
