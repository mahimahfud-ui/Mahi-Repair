import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';

export const KNOWN_OWNER_EMAIL = (process.env.OWNER_EMAIL || 'mahinmhfd@gmail.com').toLowerCase();

export async function requireOwner() {
  const supabase = await createClient();
  const { data: { user }, error } = await supabase.auth.getUser();
  if (error || !user) {
    redirect('/login?error=unauthorized');
  }

  // Verify email is confirmed in production
  if (!user.email_confirmed_at && process.env.NODE_ENV === 'production') {
    redirect('/login?error=unconfirmed_email');
  }

  // Verify user email matches the authorized Owner email allowlist
  const userEmail = (user.email ?? '').toLowerCase();
  if (userEmail !== KNOWN_OWNER_EMAIL) {
    redirect('/login?error=forbidden');
  }

  const { data: profile, error: profileError } = await supabase
    .from('profiles')
    .select('role,status,display_name')
    .eq('id', user.id)
    .maybeSingle();

  if (profileError || !profile || profile.role !== 'owner' || profile.status !== 'active') {
    redirect('/login?error=forbidden');
  }

  return { supabase, user, profile };
}
