import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { KNOWN_OWNER_EMAIL } from '@/lib/auth/require-owner';

export async function GET(request: Request) {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const next = url.searchParams.get('next') ?? '/';
  const error = url.searchParams.get('error');
  const errorDescription = url.searchParams.get('error_description');

  // Handle errors emitted by Supabase Auth (e.g. expired or already used OTP link)
  if (error || errorDescription) {
    const errorParam = encodeURIComponent(errorDescription || error || 'link_expired');
    return NextResponse.redirect(new URL(`/login?error=${errorParam}`, url.origin));
  }

  if (code) {
    const supabase = await createClient();
    const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);

    if (!exchangeError) {
      // Validate that the signed-in user is the authorized Owner
      const { data: { user } } = await supabase.auth.getUser();
      const userEmail = (user?.email ?? '').toLowerCase();

      if (user && userEmail === KNOWN_OWNER_EMAIL) {
        // Ensure redirect is an internal relative route to prevent open-redirect attacks
        const destination = next.startsWith('/') && !next.startsWith('//') ? next : '/';
        return NextResponse.redirect(new URL(destination, url.origin));
      }

      // If user is not the authorized owner, sign them out and reject
      await supabase.auth.signOut();
      return NextResponse.redirect(new URL('/login?error=forbidden', url.origin));
    }

    const failureReason = encodeURIComponent(exchangeError.message || 'code_exchange_failed');
    return NextResponse.redirect(new URL(`/login?error=${failureReason}`, url.origin));
  }

  return NextResponse.redirect(new URL('/login?error=missing_code', url.origin));
}
