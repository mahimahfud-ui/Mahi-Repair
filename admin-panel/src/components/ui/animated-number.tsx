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
  const currentVal = React.useRef<number>(value);
  const startTime = React.useRef<number | null>(null);

  React.useEffect(() => {
    targetVal.current = value;
    startVal.current = currentVal.current;
    startTime.current = null;

    // Reduced-motion updates are deferred to the animation frame, avoiding a
    // synchronous state update inside the effect while still skipping animation.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const frame = requestAnimationFrame(() => {
        currentVal.current = value;
        setCurrent(value);
      });
      return () => cancelAnimationFrame(frame);
    }

    let animId = 0;
    const step = (now: number) => {
      if (startTime.current === null) startTime.current = now;
      const elapsed = now - startTime.current;
      const progress = Math.min(elapsed / durationMs, 1);
      const eased = easeOutExpo(progress);
      const next = startVal.current + (targetVal.current - startVal.current) * eased;
      currentVal.current = next;
      setCurrent(next);

      if (progress < 1) {
        animId = requestAnimationFrame(step);
      } else {
        currentVal.current = targetVal.current;
        setCurrent(targetVal.current);
      }
    };

    animId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(animId);
  }, [value, durationMs]);

  return <span className={className}>{formatter(current)}</span>;
}
