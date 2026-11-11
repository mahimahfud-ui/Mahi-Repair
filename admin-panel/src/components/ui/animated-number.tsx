'use client';

import * as React from 'react';

export interface AnimatedNumberProps {
  value: number;
  durationMs?: number;
  formatter?: (n: number) => string;
  className?: string;
}

const easeOutExpo = (t: number): number => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t));

export function AnimatedNumber({
  value,
  durationMs = 900,
  formatter = (n) => Math.round(n).toLocaleString(),
  className
}: AnimatedNumberProps) {
  const [current, setCurrent] = React.useState<number>(value);
  const startVal = React.useRef<number>(value);
  const targetVal = React.useRef<number>(value);
  const startTime = React.useRef<number | null>(null);

  React.useEffect(() => {
    // Check reduced motion
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setCurrent(value);
      targetVal.current = value;
      return;
    }

    startVal.current = current;
    targetVal.current = value;
    startTime.current = null;

    let animId: number;
    const step = (now: number) => {
      if (startTime.current === null) startTime.current = now;
      const elapsed = now - startTime.current;
      const progress = Math.min(elapsed / durationMs, 1);
      const eased = easeOutExpo(progress);
      const next = startVal.current + (targetVal.current - startVal.current) * eased;
      setCurrent(next);

      if (progress < 1) {
        animId = requestAnimationFrame(step);
      } else {
        setCurrent(targetVal.current);
      }
    };

    animId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(animId);
  }, [value, durationMs]);

  return <span className={className}>{formatter(current)}</span>;
}
