"use client";

import { useActionState } from "react";
import { Cloud, ShieldCheck, LockKeyhole, ArrowRight, AlertTriangle } from "lucide-react";
import { signInOwnerWithPin, initialOwnerPinLoginState } from "./actions";

export default function LoginPage() {
  const [state, formAction, pending] = useActionState(signInOwnerWithPin, initialOwnerPinLoginState);

  return (
    <main className="min-h-screen grid place-items-center p-5 bg-[radial-gradient(ellipse_at_top,#321214_0%,#090a0d_48%)]">
      <section className="panel w-full max-w-[440px] p-8 shadow-2xl shadow-black/50">
        <div className="flex items-center gap-3 mb-8">
          <div className="rounded-xl bg-red-500/15 p-3 text-red-400"><Cloud size={24} /></div>
          <div>
            <div className="font-semibold tracking-tight text-white">Mahi Repair Cloud</div>
            <div className="text-[11px] font-mono text-red-400/90 tracking-wider">OWNER CONTROL CENTER</div>
          </div>
        </div>

        <div className="mb-6">
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-red-400 uppercase tracking-wider">
            <ShieldCheck size={16} /> Private owner access
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Enter your Owner PIN</h1>
          <p className="muted text-xs mt-2 leading-relaxed">
            The PIN authenticates the existing Owner account through Supabase Auth. Member accounts cannot enter this console.
          </p>
        </div>

        <form action={formAction} className="space-y-4">
          <label className="block text-xs font-medium text-gray-300">
            Owner PIN
            <input
              className="field mt-2 text-center text-xl tracking-[0.35em] font-mono"
              type="password"
              name="pin"
              inputMode="numeric"
              pattern="[0-9]{8,12}"
              minLength={8}
              maxLength={12}
              autoComplete="current-password"
              placeholder="••••••••"
              required
              disabled={pending}
            />
          </label>

          {state.error && (
            <div role="alert" className="rounded-lg border border-red-900/50 bg-red-950/20 p-3 text-xs text-red-300 flex items-start gap-2">
              <AlertTriangle size={15} className="text-red-400 shrink-0 mt-0.5" />
              <span>{state.error}</span>
            </div>
          )}

          <button className="btn btn-primary w-full text-sm font-semibold" disabled={pending}>
            {pending ? "Verifying Owner PIN…" : <span className="flex items-center justify-center gap-2"><LockKeyhole size={15} /> Unlock Owner Console <ArrowRight size={16} /></span>}
          </button>
        </form>

        <div className="mt-6 rounded-xl border border-[#252830] bg-[#0c0e12] p-3 text-[11px] leading-relaxed text-gray-500">
          Use an 8–12 digit PIN configured as the password of the existing Owner Auth account in Supabase. The PIN is never stored in this app, browser storage, or source control.
        </div>

        <div className="mt-8 border-t border-[#252830] pt-4 text-center">
          <p className="text-[11px] text-gray-500">Mahi Repair Cloud Security Boundary · Supabase Auth + Server-Enforced Owner RBAC</p>
        </div>
      </section>
    </main>
  );
}
