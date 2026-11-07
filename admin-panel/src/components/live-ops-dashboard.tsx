"use client";

import { useEffect, useState } from 'react';
import { Activity, Clock3, RefreshCw, Smartphone, Users } from 'lucide-react';
import { getLiveOpsPollDelay } from '@/lib/ops/polling';

type OpsActivity = {
  id: string;
  kind: 'audit' | 'repair_run';
  label: string;
  target: string;
  created_at: string;
};

type OpsSnapshot = {
  generated_at: string;
  poll_interval_ms: number;
  counts: {
    members: number;
    active_members: number;
    registered_devices: number;
    revoked_devices: number;
    seen_in_last_15m: number;
    repair_runs_24h: number;
    invitation_requests_24h: number;
    audit_events_24h: number;
  };
  activity: OpsActivity[];
  capabilities: {
    support_sessions: 'not_available';
    live_screen_stream: 'not_available';
    webrtc_signaling: 'not_available';
  };
};

function timeLabel(value: string) {
  return new Date(value).toLocaleString();
}

export function LiveOpsDashboard() {
  const [snapshot, setSnapshot] = useState<OpsSnapshot | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(true);

  useEffect(() => {
    let active = true;
    let timeout: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;

    const schedule = (delay: number) => {
      if (active) timeout = setTimeout(() => void refresh(), delay);
    };

    const refresh = async () => {
      if (!active) return;
      if (document.visibilityState === 'hidden') {
        schedule(60_000);
        return;
      }

      setRefreshing(true);
      try {
        const response = await fetch('/api/ops', {
          method: 'GET',
          cache: 'no-store',
          headers: { Accept: 'application/json' },
        });
        if (!response.ok) throw new Error(`Snapshot request failed: ${response.status}`);
        const value = await response.json() as OpsSnapshot;
        if (!active) return;
        setSnapshot(value);
        setLastUpdated(new Date().toISOString());
        setError('');
        failures = 0;
        schedule(30_000);
      } catch {
        if (!active) return;
        failures += 1;
        setError('Live refresh is temporarily unavailable. Existing server-rendered data remains unchanged.');
        schedule(getLiveOpsPollDelay(failures));
      } finally {
        if (active) setRefreshing(false);
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        if (timeout) clearTimeout(timeout);
        void refresh();
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    void refresh();

    return () => {
      active = false;
      if (timeout) clearTimeout(timeout);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, []);

  const counts = snapshot?.counts;
  const metrics = [
    { label: 'Active members', value: counts?.active_members, detail: `${counts?.members ?? '—'} total member profiles`, Icon: Users },
    { label: 'Registered devices', value: counts?.registered_devices, detail: `${counts?.revoked_devices ?? '—'} revoked`, Icon: Smartphone },
    { label: 'Seen in last 15 min', value: counts?.seen_in_last_15m, detail: 'Recent check-in only; not a live online signal', Icon: Clock3 },
    { label: 'Repair runs · 24h', value: counts?.repair_runs_24h, detail: 'Submitted Android execution reports', Icon: Activity },
    { label: 'Invite requests · 24h', value: counts?.invitation_requests_24h, detail: 'Requests accepted by the Auth invite API', Icon: Users },
  ];

  return (
    <section className="panel mt-5 p-5" aria-live="polite">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[10px] font-semibold tracking-[.18em] text-red-300">LIVE OPERATIONS</div>
          <h2 className="mt-2 text-sm font-semibold">Backend activity snapshot</h2>
          <p className="mt-1 text-xs muted">Owner-authenticated polling from Supabase · 30 seconds when healthy, backed off on errors</p>
        </div>
        <div className="flex items-center gap-2 text-[11px] muted">
          <RefreshCw size={13} className={refreshing ? 'animate-spin' : ''} />
          {refreshing ? 'Refreshing' : 'Refresh scheduled'}
        </div>
      </div>

      {error && <p className="mt-4 rounded-lg border border-amber-900/60 bg-amber-950/20 p-3 text-xs text-amber-200" role="status">{error}</p>}

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {metrics.map(({ label, value, detail, Icon }) => (
          <div key={label} className="rounded-xl border border-[#292d36] bg-[#0b0d11] p-4">
            <div className="flex items-center justify-between gap-2 text-xs muted">
              <span>{label}</span>
              <Icon size={15} className="text-red-400" />
            </div>
            <div className="mt-3 text-2xl font-semibold tabular-nums">{value?.toLocaleString() ?? '—'}</div>
            <p className="mt-1 text-[10px] leading-4 muted">{detail}</p>
          </div>
        ))}
      </div>

      <div className="mt-5 grid gap-4 xl:grid-cols-[1.25fr_1fr]">
        <div>
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-xs font-semibold">Recent backend events</h3>
            <span className="text-[10px] muted">{counts?.audit_events_24h ?? '—'} audit events in 24h</span>
          </div>
          {snapshot?.activity.length ? (
            <div className="mt-2 divide-y divide-[#252830]">
              {snapshot.activity.map((item) => (
                <div key={item.id} className="flex items-start justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <div className="text-xs">{item.label}</div>
                    <div className="mt-1 truncate text-[10px] muted">{item.kind === 'audit' ? `Audit · ${item.target}` : `Tool · ${item.target}`}</div>
                  </div>
                  <time className="shrink-0 text-[10px] muted">{timeLabel(item.created_at)}</time>
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-xs muted">{snapshot ? 'No recent audit or repair-run events.' : 'Loading current backend events…'}</p>
          )}
        </div>

        <div className="rounded-xl border border-[#292d36] bg-[#0b0d11] p-4">
          <h3 className="text-xs font-semibold">Remote support availability</h3>
          <p className="mt-2 text-xs leading-5 muted">
            The current backend exposes no support-session, WebRTC signaling or live-screen stream API. This console does not fabricate a remote screen or connection status.
          </p>
          <div className="mt-3 space-y-2 text-[11px]">
            <div className="flex justify-between gap-2"><span className="muted">Support sessions</span><span className="text-amber-300">Not provided by backend</span></div>
            <div className="flex justify-between gap-2"><span className="muted">WebRTC stream</span><span className="text-amber-300">Not implemented</span></div>
            <div className="flex justify-between gap-2"><span className="muted">Last data refresh</span><span>{lastUpdated ? new Date(lastUpdated).toLocaleTimeString() : '—'}</span></div>
          </div>
        </div>
      </div>
    </section>
  );
}
