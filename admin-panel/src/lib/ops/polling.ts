const BASE_DELAY_MS = 30_000;
const MAX_DELAY_MS = 5 * 60_000;

export function getLiveOpsPollDelay(consecutiveFailures: number): number {
  const failures = Number.isFinite(consecutiveFailures)
    ? Math.max(0, Math.floor(consecutiveFailures))
    : 0;
  const exponent = Math.min(failures, 4);
  return Math.min(BASE_DELAY_MS * 2 ** exponent, MAX_DELAY_MS);
}
