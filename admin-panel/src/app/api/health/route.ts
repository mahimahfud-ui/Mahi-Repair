import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

export async function GET() {
  return NextResponse.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    service: 'mahi-repair-admin-panel',
    environment: process.env.NODE_ENV || 'production',
  });
}
