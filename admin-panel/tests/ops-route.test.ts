import { beforeEach, describe, expect, it, vi } from 'vitest';

const { createClientMock } = vi.hoisted(() => ({
  createClientMock: vi.fn(),
}));

vi.mock('@/lib/supabase/server', () => ({
  createClient: createClientMock,
}));

import { GET } from '../src/app/api/ops/route';

type TestProfile = { role: string; status: string } | null;
type TestQueryResult = { data: unknown; count: number | null; error: null };
type TestQuery = {
  select: (columns: string, options?: { count?: string; head?: boolean }) => TestQuery;
  eq: (column: string, value: unknown) => TestQuery;
  gte: (column: string, value: unknown) => TestQuery;
  not: (column: string, operator: string, value: unknown) => TestQuery;
  order: (...args: unknown[]) => TestQuery;
  limit: (...args: unknown[]) => TestQuery;
  maybeSingle: () => Promise<{ data: TestProfile; error: null }>;
  then: (
    resolve: (value: TestQueryResult) => unknown,
    reject?: (reason: unknown) => unknown,
  ) => Promise<unknown>;
};

function queryResult(
  table: string,
  headOnly: boolean,
  filters: Map<string, unknown>,
): TestQueryResult {
  if (headOnly) {
    if (table === 'profiles') {
      return { data: null, count: filters.get('status') === 'active' ? 3 : 4, error: null };
    }
    if (table === 'devices') {
      if (filters.has('not:revoked_at')) return { data: null, count: 1, error: null };
      if (filters.has('last_seen_at')) return { data: null, count: 2, error: null };
      return { data: null, count: 5, error: null };
    }
    if (table === 'repair_runs') return { data: null, count: 6, error: null };
    if (table === 'admin_audit_logs') {
      return { data: null, count: filters.has('action') ? 2 : 9, error: null };
    }
  }

  if (table === 'admin_audit_logs') {
    return {
      data: [{
        id: 'audit-1',
        action: 'member.invited',
        target_type: 'profile',
        created_at: '2026-11-07T10:00:00.000Z',
      }],
      count: null,
      error: null,
    };
  }

  if (table === 'repair_runs') {
    return {
      data: [{
        id: 'run-1',
        result: 'SUCCESS',
        tool_id: 'tool-1',
        started_at: '2026-11-07T11:00:00.000Z',
      }],
      count: null,
      error: null,
    };
  }

  return { data: [], count: null, error: null };
}

function createQuery(table: string, profile: TestProfile): TestQuery {
  let headOnly = false;
  const filters = new Map<string, unknown>();
  const query: TestQuery = {
    select: (_columns, options) => {
      headOnly = Boolean(options?.head);
      return query;
    },
    eq: (column, value) => {
      filters.set(String(column), value);
      return query;
    },
    gte: (column, value) => {
      filters.set(String(column), value);
      return query;
    },
    not: (column, operator, value) => {
      filters.set(`not:${String(column)}`, { operator, value });
      return query;
    },
    order: () => query,
    limit: () => query,
    maybeSingle: async () => ({ data: profile, error: null }),
    then: (resolve, reject) => Promise.resolve(queryResult(table, headOnly, filters)).then(resolve, reject),
  };
  return query;
}

function configureSupabase(user: { id: string } | null, profile: TestProfile) {
  const getUser = vi.fn().mockResolvedValue({
    data: { user },
    error: user ? null : { message: 'Invalid or expired session' },
  });
  const from = vi.fn((table: string) => createQuery(table, profile));
  createClientMock.mockResolvedValue({
    auth: { getUser },
    from,
  });
  return { getUser, from };
}

describe('GET /api/ops', () => {
  beforeEach(() => {
    createClientMock.mockReset();
  });

  it('rejects an anonymous request before reading profile or operational data', async () => {
    const { from } = configureSupabase(null, null);

    const response = await GET();

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: 'UNAUTHORIZED' });
    expect(from).not.toHaveBeenCalled();
    expect(response.headers.get('cache-control')).toContain('no-store');
  });

  it('rejects authenticated members', async () => {
    const { from } = configureSupabase(
      { id: 'member-1' },
      { role: 'member', status: 'active' },
    );

    const response = await GET();

    expect(response.status).toBe(403);
    expect(await response.json()).toEqual({ error: 'FORBIDDEN' });
    expect(from).toHaveBeenCalledTimes(1);
  });

  it('rejects inactive Owner profiles', async () => {
    const { from } = configureSupabase(
      { id: 'owner-1' },
      { role: 'owner', status: 'suspended' },
    );

    const response = await GET();

    expect(response.status).toBe(403);
    expect(await response.json()).toEqual({ error: 'FORBIDDEN' });
    expect(from).toHaveBeenCalledTimes(1);
  });

  it('returns real operational snapshot data only for an active Owner', async () => {
    configureSupabase(
      { id: 'owner-1' },
      { role: 'owner', status: 'active' },
    );

    const response = await GET();
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(response.headers.get('cache-control')).toContain('no-store');
    expect(payload.counts).toEqual({
      members: 4,
      active_members: 3,
      registered_devices: 5,
      revoked_devices: 1,
      seen_in_last_15m: 2,
      repair_runs_24h: 6,
      invitation_requests_24h: 2,
      audit_events_24h: 9,
    });
    expect(payload.activity).toHaveLength(2);
    expect(payload.activity[0].kind).toBe('repair_run');
    expect(payload.capabilities).toEqual({
      support_sessions: 'not_available',
      live_screen_stream: 'not_available',
      webrtc_signaling: 'not_available',
    });
  });
});
