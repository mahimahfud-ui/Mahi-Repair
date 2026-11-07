import { createServerClient } from '@supabase/ssr';
import { cookies } from 'next/headers';

const sessionCookieOptions = {
  path: '/',
  sameSite: 'lax' as const,
  secure: process.env.NODE_ENV === 'production',
  httpOnly: true,
  // A browser must re-authenticate after the cookie expires. Supabase Auth
  // timebox/inactivity limits must also be configured in the hosted project.
  maxAge: 60 * 60 * 8,
};

export async function createClient() {
  const cookieStore = await cookies();
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  if (!url || !key) {
    throw new Error('Supabase public environment variables are missing.');
  }

  return createServerClient(url, key, {
    cookieOptions: sessionCookieOptions,
    cookies: {
      getAll: () => cookieStore.getAll(),
      setAll: (values) => {
        try {
          values.forEach(({ name, value, options }) => {
            cookieStore.set(name, value, { ...options, ...sessionCookieOptions });
          });
        } catch {
          // Server Components cannot write cookies. proxy.ts refreshes and
          // persists the session before the protected route is rendered.
        }
      },
    },
  });
}
