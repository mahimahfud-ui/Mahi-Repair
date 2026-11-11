'use client';

import * as React from 'react';
import { cn } from '@/lib/utils';

export interface TiltCardProps extends React.HTMLAttributes<HTMLDivElement> {
  maxTiltDeg?: number;
  glow?: boolean;
}

export function TiltCard({
  className,
  children,
  maxTiltDeg = 8,
  glow = true,
  onPointerMove,
  onPointerLeave,
  style,
  ...props
}: TiltCardProps) {
  const cardRef = React.useRef<HTMLDivElement>(null);
  const [coords, setCoords] = React.useState({ x: 50, y: 50, tiltX: 0, tiltY: 0, active: false });

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const el = cardRef.current;
    if (!el) return;

    // Honor reduced-motion preference
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const rect = el.getBoundingClientRect();
    const px = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1);
    const py = Math.min(Math.max((e.clientY - rect.top) / rect.height, 0), 1);

    const tiltX = (py - 0.5) * -2 * maxTiltDeg;
    const tiltY = (px - 0.5) * 2 * maxTiltDeg;

    setCoords({
      x: px * 100,
      y: py * 100,
      tiltX,
      tiltY,
      active: true
    });

    onPointerMove?.(e);
  };

  const handlePointerLeave = (e: React.PointerEvent<HTMLDivElement>) => {
    setCoords((prev) => ({ ...prev, tiltX: 0, tiltY: 0, active: false }));
    onPointerLeave?.(e);
  };

  return (
    <div
      ref={cardRef}
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
      data-active={coords.active ? 'true' : undefined}
      style={
        {
          perspective: 1000,
          transform: `perspective(1000px) rotateX(${coords.tiltX}deg) rotateY(${coords.tiltY}deg)`,
          transition: coords.active ? 'transform 100ms ease-out' : 'transform 400ms cubic-bezier(0.16, 1, 0.3, 1)',
          '--pointer-x': `${coords.x}%`,
          '--pointer-y': `${coords.y}%`,
          ...style
        } as React.CSSProperties
      }
      className={cn(
        'group relative rounded-2xl border border-[#252830] bg-[#101216] p-6 text-gray-100 will-change-transform',
        glow &&
          'before:pointer-events-none before:absolute before:inset-0 before:rounded-2xl before:opacity-0 before:transition-opacity before:duration-300 before:content-[""] hover:before:opacity-100 before:bg-[radial-gradient(circle_at_var(--pointer-x)_var(--pointer-y),rgba(235,16,42,0.14),transparent_60%)]',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
