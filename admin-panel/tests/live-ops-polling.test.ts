import { describe, expect, it } from 'vitest';
import { getLiveOpsPollDelay } from '../src/lib/ops/polling';

describe('getLiveOpsPollDelay', () => {
  it('starts with a 30 second polling interval', () => {
    expect(getLiveOpsPollDelay(0)).toBe(30_000);
  });

  it('backs off after consecutive failures', () => {
    expect(getLiveOpsPollDelay(1)).toBe(60_000);
    expect(getLiveOpsPollDelay(2)).toBe(120_000);
    expect(getLiveOpsPollDelay(3)).toBe(240_000);
  });

  it('caps the delay at five minutes and handles invalid inputs', () => {
    expect(getLiveOpsPollDelay(4)).toBe(300_000);
    expect(getLiveOpsPollDelay(12)).toBe(300_000);
    expect(getLiveOpsPollDelay(-5)).toBe(30_000);
    expect(getLiveOpsPollDelay(Number.NaN)).toBe(30_000);
  });
});
