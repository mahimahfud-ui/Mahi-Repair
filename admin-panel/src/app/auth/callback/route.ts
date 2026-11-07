import { NextResponse, type NextRequest } from 'next/server';
import { createClient } from '@/lib/supabase/server';

export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const origin = request.nextUrl.origin;
  const supabase = await createClient();
  const code = request.nextUrl.searchParams.get('code');

  const redirectTo = (path: string) => {
    const response = NextResponse.redirect(new URL(path, origin));
    response.headers.set('Cache-Control', 'no-store, max-age=0');
    response.headers.set('Referrer-Policy', 'no-referrer');
    return response;
  };

  if (!code) return redirectTo('/login?error=callback');

  const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
  if (exchangeError) {
    await supabase.auth.signOut({ scope: 'local' });
    return redirectTo('/login?error=callback');
  }

  // The callback is the account boundary: do not leave a valid member session
  // in the browser and rely only on a later page redirect.
  const { data: { user }, error: userError } = await supabase.auth.getUser();
  if (userError || !user) {
    await supabase.auth.signOut({ scope: 'local' });
    return redirectTo('/login?error=callback');
  }

  const { data: profile, error: profileError } = await supabase
    .from('profiles')
    .select('role,status')
    .eq('id', user.id)
    .maybeSingle();

  if (profileError || !profile || profile.role !== 'owner' || profile.status !== 'active') {
    await supabase.auth.signOut({ scope: 'local' });
    return redirectTo('/login?denied=1');
  }

  return redirectTo('/');
}
