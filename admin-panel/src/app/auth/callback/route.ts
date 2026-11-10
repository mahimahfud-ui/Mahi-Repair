import { NextResponse } from "next/server";

export async function GET(request: Request) {
  // Firebase OTP sessions are established only by the same-origin /api/auth/verify-otp route.
  // Legacy Supabase magic links must not create or restore an Admin session.
  const url = new URL(request.url);
  return NextResponse.redirect(new URL("/login?error=legacy_link", url.origin));
}
