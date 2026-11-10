"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ShieldCheck, Cloud, ArrowRight, AlertTriangle, CheckCircle2, Clock, RotateCcw, KeyRound } from "lucide-react";

const COOLDOWN_SECONDS = 60;
const SESSION_STORAGE_COOLDOWN_KEY = "mahi_owner_login_cooldown_until";

async function readError(response: Response, fallback: string): Promise<string> {
  const payload = await response.json().catch(() => null) as { error?: { message?: string } } | null;
  return payload?.error?.message || fallback;
}

function LoginForm() {
  const searchParams = useSearchParams();
  const urlError = searchParams.get("error");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [challengeId, setChallengeId] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    let restoreTimer: ReturnType<typeof setTimeout> | undefined;
    try {
      const stored = sessionStorage.getItem(SESSION_STORAGE_COOLDOWN_KEY);
      if (stored) {
        const expiry = Number.parseInt(stored, 10);
        const remaining = Number.isFinite(expiry) ? Math.max(0, Math.ceil((expiry - Date.now()) / 1000)) : 0;
        if (remaining > 0) restoreTimer = setTimeout(() => setCooldown(remaining), 0);
      }
    } catch { /* Browser storage is optional. */ }
    return () => { if (restoreTimer !== undefined) clearTimeout(restoreTimer); };
  }, []);

  useEffect(() => {
    if (cooldown <= 0) return;
    const interval = setInterval(() => {
      setCooldown((previous) => {
        if (previous <= 1) {
          try { sessionStorage.removeItem(SESSION_STORAGE_COOLDOWN_KEY); } catch { /* Ignore. */ }
          return 0;
        }
        return previous - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldown]);

  function startCooldown(seconds = COOLDOWN_SECONDS) {
    setCooldown(seconds);
    try { sessionStorage.setItem(SESSION_STORAGE_COOLDOWN_KEY, String(Date.now() + seconds * 1000)); } catch { /* Ignore. */ }
  }

  async function requestCode(event: FormEvent) {
    event.preventDefault();
    if (busy || cooldown > 0) return;
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/auth/request-otp", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
        cache: "no-store",
      });
      if (!response.ok) throw new Error(await readError(response, "Unable to send a verification code."));
      const payload = await response.json() as { data?: { challenge_id?: string; resend_after_seconds?: number } };
      if (!payload.data?.challenge_id) throw new Error("The sign-in service returned an invalid challenge.");
      setChallengeId(payload.data.challenge_id);
      setSent(true);
      setCode("");
      startCooldown(Math.max(1, payload.data.resend_after_seconds || COOLDOWN_SECONDS));
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Unable to send a verification code.";
      setError(message);
      if (/rate|too many|429/i.test(message)) startCooldown(COOLDOWN_SECONDS);
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(event: FormEvent) {
    event.preventDefault();
    if (busy || !challengeId || !/^[0-9]{6}$/.test(code)) return;
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/auth/verify-otp", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase(), challengeId, code }),
        cache: "no-store",
      });
      if (!response.ok) throw new Error(await readError(response, "The verification code was rejected."));
      window.location.assign("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to verify this code.");
    } finally {
      setBusy(false);
    }
  }

  const getUrlErrorMessage = (value: string): string => {
    switch (value) {
      case "forbidden": return "Access denied. Only the active Owner account may enter this console. Member accounts are prohibited.";
      case "unauthorized": return "Your Owner session has expired or is invalid. Request a new sign-in code.";
      case "unconfirmed_email": return "The Owner email is not verified in Firebase Authentication.";
      case "missing_code": return "The sign-in link is no longer supported. Request a fresh verification code.";
      default:
        try { return decodeURIComponent(value); } catch { return value; }
    }
  };

  return (
    <section className="panel w-full max-w-[440px] p-8 shadow-2xl shadow-black/50">
      <div className="mb-8 flex items-center gap-3">
        <div className="rounded-xl bg-red-500/15 p-3 text-red-400"><Cloud size={24} /></div>
        <div>
          <div className="font-semibold tracking-tight text-white">Mahi Repair Cloud</div>
          <div className="font-mono text-[11px] tracking-wider text-red-400/90">OWNER CONTROL CENTER</div>
        </div>
      </div>

      <div className="mb-6">
        <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-red-400"><ShieldCheck size={16} /> Private owner access</div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Sign in securely</h1>
        <p className="muted mt-2 text-xs leading-relaxed">
          Access is restricted to the authorized Owner (<span className="font-mono text-gray-300">mahinmhfd@gmail.com</span>). Member accounts cannot enter this console.
        </p>
      </div>

      {urlError && <div className="mb-5 flex items-start gap-2.5 rounded-xl border border-red-900/60 bg-red-950/30 p-4 text-xs text-red-300"><AlertTriangle size={16} className="mt-0.5 shrink-0 text-red-400" /><div className="leading-relaxed">{getUrlErrorMessage(urlError)}</div></div>}

      {!sent ? (
        <form onSubmit={requestCode} className="space-y-4">
          <label className="block text-xs font-medium text-gray-300">
            Owner Email Address
            <input className="field mt-2 text-sm" type="email" autoComplete="email" required maxLength={254} disabled={busy || cooldown > 0} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="mahinmhfd@gmail.com" />
          </label>
          {error && <div className="flex items-start gap-2 rounded-lg border border-red-900/50 bg-red-950/20 p-3 text-xs text-red-300"><AlertTriangle size={15} className="mt-0.5 shrink-0 text-red-400" /><div className="leading-relaxed">{error}</div></div>}
          <button className="btn btn-primary w-full text-sm font-semibold" disabled={busy || cooldown > 0 || !email.trim()}>
            {busy ? "Sending verification code…" : cooldown > 0 ? <span className="flex items-center gap-2"><Clock size={15} /> Wait {cooldown}s</span> : <span className="flex items-center justify-center gap-2">Email 6-digit code <ArrowRight size={16} /></span>}
          </button>
        </form>
      ) : (
        <form onSubmit={verifyCode} className="space-y-4">
          <div className="rounded-xl border border-emerald-900/80 bg-emerald-950/30 p-4 text-xs text-emerald-200">
            <div className="mb-1 flex items-center gap-2 font-semibold text-emerald-300"><CheckCircle2 size={16} /> Verification code sent</div>
            <p className="leading-relaxed">A sign-in code was requested for <strong className="text-white">{email}</strong>. Enter the 6-digit code to continue.</p>
          </div>
          <label className="block text-xs font-medium text-gray-300">
            Verification code
            <input className="field mt-2 text-center font-mono text-xl tracking-[0.45em]" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required disabled={busy} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="••••••" />
          </label>
          {error && <div className="flex items-start gap-2 rounded-lg border border-red-900/50 bg-red-950/20 p-3 text-xs text-red-300"><AlertTriangle size={15} className="mt-0.5 shrink-0 text-red-400" /><div className="leading-relaxed">{error}</div></div>}
          <button className="btn btn-primary w-full text-sm font-semibold" disabled={busy || code.length !== 6}>
            {busy ? "Verifying Owner session…" : <span className="flex items-center justify-center gap-2"><KeyRound size={15} /> Verify and open console <ArrowRight size={16} /></span>}
          </button>
          <div className="flex items-center justify-between rounded-lg border border-[#252830] bg-[#0c0e12] p-3 text-xs text-gray-400">
            <span className="flex items-center gap-1.5"><Clock size={14} className="text-gray-500" /> Resend cooldown</span>
            <span className="font-mono tabular-nums text-white">{cooldown > 0 ? `${cooldown}s` : "Ready"}</span>
          </div>
          <button type="button" onClick={requestCode as unknown as () => void} disabled={busy || cooldown > 0} className="btn btn-secondary w-full text-xs">
            <RotateCcw size={14} /> {cooldown > 0 ? `Wait ${cooldown}s to resend` : "Resend code"}
          </button>
          <button type="button" onClick={() => { setSent(false); setChallengeId(""); setCode(""); setError(null); }} disabled={busy} className="w-full text-center text-xs text-gray-500 hover:text-gray-300">Use a different email address</button>
        </form>
      )}

      <div className="mt-8 border-t border-[#252830] pt-4 text-center"><p className="text-[11px] text-gray-500">Mahi Repair Cloud Security Boundary · Firebase ID Tokens · Server-Enforced RBAC</p></div>
    </section>
  );
}

export default function LoginPage() {
  return (
    <main className="grid min-h-screen place-items-center bg-[radial-gradient(ellipse_at_top,#321214_0%,#090a0d_48%)] p-5">
      <Suspense fallback={<div className="panel p-8 text-xs text-gray-400">Loading console authentication…</div>}>
        <LoginForm />
      </Suspense>
    </main>
  );
}
