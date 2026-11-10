import { FileKey2, KeyRound, LockKeyhole, ShieldCheck, type LucideIcon } from "lucide-react";
import { ConsoleShell } from "@/components/console-shell";

const securityCards: Array<{ Icon: LucideIcon; title: string; description: string }> = [
  {
    Icon: ShieldCheck,
    title: "Owner-only console",
    description: "The Admin server verifies the Firebase ID token and checks the active Owner profile through Cloud Run on every protected page and mutation. Member sessions are denied.",
  },
  {
    Icon: KeyRound,
    title: "Recipe signing private key",
    description: "The RSA-3072 signing private key belongs only in Google Secret Manager and the server runtime. Android and the browser receive only the public verification key.",
  },
  {
    Icon: LockKeyhole,
    title: "Firebase session and service credentials",
    description: "The Admin website stores Firebase ID and refresh tokens in HttpOnly cookies. Firebase Admin credentials, App Check secrets and Gemini keys must never enter browser or Android bundles.",
  },
  {
    Icon: FileKey2,
    title: "Audit and immutable releases",
    description: "Owner mutations must create append-only audit events. Published tool versions are immutable; rollback publishes a new signed version rather than overwriting history.",
  },
];

export default function SecurityPage() {
  return (
    <ConsoleShell active="/security" title="Security & Settings" subtitle="Trust boundaries and operational controls">
      <div className="grid gap-4 md:grid-cols-2">
        {securityCards.map(({ Icon, title, description }) => (
          <div key={title} className="panel p-5">
            <Icon className="text-red-400" />
            <h2 className="mt-4 text-sm font-semibold">{title}</h2>
            <p className="muted mt-2 text-xs leading-5">{description}</p>
          </div>
        ))}
      </div>
      <div className="panel mt-4 p-5">
        <h2 className="text-sm font-semibold">Required production controls</h2>
        <ul className="muted mt-3 list-disc space-y-2 pl-5 text-xs">
          <li>Enable MFA for the Owner&apos;s Google account and protect Firebase project administration.</li>
          <li>Enforce Firebase App Check on supported Android-facing services and separate debug from production providers.</li>
          <li>Configure and verify the OTP email sender, challenge expiry, per-account/IP limits and abuse monitoring.</li>
          <li>Rotate any credential that has ever been committed or shared; keep server secrets in Secret Manager.</li>
          <li>Back up signing public keys and maintain key IDs, key rotation and recovery procedures in the Android app.</li>
          <li>Monitor Cloud Run errors, authentication failures, unusual device access and immutable audit events.</li>
        </ul>
      </div>
    </ConsoleShell>
  );
}
