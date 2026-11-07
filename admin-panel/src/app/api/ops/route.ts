import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';

export const dynamic = 'force-dynamic';

function jsonError(status: number, code: string) {
  const response = NextResponse.json({ error: code }, { status });
  response.headers.set('Cache-Control', 'no-store, max-age=0');
  return response;
}

export async function GET() {
  try {
    const supabase = await createClient();
    const { data: { user }, error: authError } = await supabase.auth.getUser();

    if (authError || !user) return jsonError(401, 'UNAUTHORIZED');

    const { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('role,status')
      .eq('id', user.id)
      .maybeSingle();

    if (profileError || !profile || profile.role !== 'owner' || profile.status !== 'active') {
      return jsonError(403, 'FORBIDDEN');
    }

    const now = Date.now();
    const dayAgo = new Date(now - 24 * 60 * 60 * 1000).toISOString();
    const fifteenMinutesAgo = new Date(now - 15 * 60 * 1000).toISOString();

    const [
      members,
      activeMembers,
      devices,
      revokedDevices,
      recentlySeenDevices,
      executions,
      invitations,
      auditEvents,
      recentAudit,
      recentRuns,
    ] = await Promise.all([
      supabase.from('profiles').select('id', { count: 'exact', head: true }).eq('role', 'member'),
      supabase.from('profiles').select('id', { count: 'exact', head: true }).eq('role', 'member').eq('status', 'active'),
      supabase.from('devices').select('id', { count: 'exact', head: true }),
      supabase.from('devices').select('id', { count: 'exact', head: true }).not('revoked_at', 'is', null),
      supabase.from('devices').select('id', { count: 'exact', head: true }).gte('last_seen_at', fifteenMinutesAgo),
      supabase.from('repair_runs').select('id', { count: 'exact', head: true }).gte('started_at', dayAgo),
      supabase.from('admin_audit_logs').select('id', { count: 'exact', head: true }).eq('action', 'member.invited').gte('created_at', dayAgo),
      supabase.from('admin_audit_logs').select('id', { count: 'exact', head: true }).gte('created_at', dayAgo),
      supabase.from('admin_audit_logs').select('id,action,target_type,created_at').order('created_at', { ascending: false }).limit(8),
      supabase.from('repair_runs').select('id,result,tool_id,started_at').order('started_at', { ascending: false }).limit(8),
    ]);

    const errors = [
      members.error,
      activeMembers.error,
      devices.error,
      revokedDevices.error,
      recentlySeenDevices.error,
      executions.error,
      invitations.error,
      auditEvents.error,
      recentAudit.error,
      recentRuns.error,
    ].filter(Boolean);

    if (errors.length) {
      console.error('Live operations snapshot query failed.');
      return jsonError(503, 'SNAPSHOT_UNAVAILABLE');
    }

    const activity = [
      ...(recentAudit.data ?? []).map((item) => ({
        id: `audit:${item.id}`,
        kind: 'audit' as const,
        label: item.action,
        target: item.target_type,
        created_at: item.created_at,
      })),
      ...(recentRuns.data ?? []).map((item) => ({
        id: `run:${item.id}`,
        kind: 'repair_run' as const,
        label: `Repair run · ${item.result}`,
        target: item.tool_id,
        created_at: item.started_at,
      })),
    ]
      .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at))
      .slice(0, 10);

    const response = NextResponse.json({
      generated_at: new Date(now).toISOString(),
      poll_interval_ms: 30_000,
      counts: {
        members: members.count ?? 0,
        active_members: activeMembers.count ?? 0,
        registered_devices: devices.count ?? 0,
        revoked_devices: revokedDevices.count ?? 0,
        seen_in_last_15m: recentlySeenDevices.count ?? 0,
        repair_runs_24h: executions.count ?? 0,
        invitation_requests_24h: invitations.count ?? 0,
        audit_events_24h: auditEvents.count ?? 0,
      },
      activity,
      capabilities: {
        support_sessions: 'not_available',
        live_screen_stream: 'not_available',
        webrtc_signaling: 'not_available',
      },
    });
    response.headers.set('Cache-Control', 'no-store, max-age=0');
    response.headers.set('Pragma', 'no-cache');
    return response;
  } catch {
    console.error('Live operations snapshot failed.');
    return jsonError(503, 'SNAPSHOT_UNAVAILABLE');
  }
}
