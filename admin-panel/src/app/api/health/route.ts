import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

export async function GET() {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const publishableKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  let configured = Boolean(supabaseUrl && publishableKey);
  if (configured) {
    try {
      const parsed = new URL(supabaseUrl as string);
      configured =
        (parsed.protocol === 'https:' || process.env.NODE_ENV !== 'production') &&
        Boolean(parsed.hostname);
    } catch {
      configured = false;
    }
  }

  const response = NextResponse.json(
    { status: configured ? 'ok' : 'not_ready' },
    { status: configured ? 200 : 503 },
  );
  response.headers.set('Cache-Control', 'no-store, max-age=0');
  response.headers.set('Pragma', 'no-cache');
  return response;
}
