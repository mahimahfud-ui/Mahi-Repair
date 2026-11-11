'use client';

import * as React from 'react';
import { cn } from '@/lib/utils';

export interface RevealProps extends React.HTMLAttributes<HTMLDivElement> {
  delayMs?: number;
  direction?: 'up' | 'down' | 'left' | 'right' | 'none';
  distancePx?: number;
  once?: boolean;
}

export function Reveal({
  children,
  className,
  delayMs = 0,
  direction = 'up',
  distancePx = 20,
  once = true,
  style,
  ...props
}: RevealProps) {
  const ref = React.useRef<HTMLDivElement>(null);
  const [visible, setVisible] = React.useState(false);

  React.useEffect(() => {
    // Respect reduced-motion settings without a synchronous effect state update.
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const frameId = window.requestAnimationFrame(() => setVisible(true));
      return () => window.cancelAnimationFrame(frameId);
    }

    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setVisible(true);
            if (once) observer.unobserve(entry.target);
          } else if (!once) {
            setVisible(false);
          }
        });
      },
      { threshold: 0.15 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [once]);

  const offsetTransform = React.useMemo(() => {
    if (visible) return 'translate3d(0, 0, 0)';
    switch (direction) {
      case 'up':
        return `translate3d(0, ${distancePx}px, 0)`;
      case 'down':
        return `translate3d(0, -${distancePx}px, 0)`;
      case 'left':
        return `translate3d(${distancePx}px, 0, 0)`;
      case 'right':
        return `translate3d(-${distancePx}px, 0, 0)`;
      case 'none':
      default:
        return 'translate3d(0, 0, 0)';
    }
  }, [visible, direction, distancePx]);

  return (
    <div
      ref={ref}
      style={
        {
          opacity: visible ? 1 : 0,
          transform: offsetTransform,
          transitionProperty: 'opacity, transform',
          transitionDuration: '500ms',
          transitionTimingFunction: 'cubic-bezier(0.16, 1, 0.3, 1)',
          transitionDelay: `${delayMs}ms`,
          willChange: 'opacity, transform',
          ...style
        } as React.CSSProperties
      }
      className={cn(className)}
      {...props}
    >
      {children}
    </div>
  );
}
