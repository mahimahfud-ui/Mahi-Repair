import { NextResponse } from "next/server";
import { CloudRunApiClient, CloudRunApiError } from "@/lib/api/cloud-run";
import { CloudRunAuthClient } from "@/lib/auth/firebase-rest";
import {
  FIREBASE_EXPIRES_AT_COOKIE,
  FIREBASE_ID_TOKEN_COOKIE,
  FIREBASE_REFRESH_TOKEN_COOKIE,
  OWNER_EMAIL,
} from "@/lib/auth/require-owner";

const REFRESH_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

function sameOrigin(request: Request): boolean {
  const origin = request.headers.get("origin");
  return Boolean(origin && origin === new URL(request.url).origin);
}

function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

export async function POST(request: Request) {
  if (!sameOrigin(request)) {
    return NextResponse.json({ error: { code: "INVALID_ORIGIN", message: "Request origin is not allowed." } }, { status: 403 });
  }

  try {
    const body = await request.json() as { email?: unknown; challengeId?: unknown; code?: unknown };
    if (typeof body.email !== "string" || typeof body.challengeId !== "string" || typeof body.code !== "string") {
      return NextResponse.json({ error: { code: "INVALID_INPUT", message: "Enter the email and 6-digit verification code." } }, { status: 400 });
    }

    const tokens = await new CloudRunAuthClient().verifyOtp(body.email, body.challengeId, body.code);
    const identity = await new CloudRunApiClient(tokens.idToken).verifyIdentity();

    // A Member may authenticate in the Android app but must never obtain an Admin website session.
    if (
      identity.email.trim().toLowerCase() !== OWNER_EMAIL ||
      identity.role !== "owner" ||
      identity.status !== "active" ||
      identity.isOwner !== true
    ) {
      return NextResponse.json({ error: { code: "FORBIDDEN_OWNER_REQUIRED", message: "This account is not authorized for the Owner Admin console." } }, { status: 403 });
    }

    const response = NextResponse.json({ data: { authenticated: true, redirectTo: "/" } }, { status: 200, headers: { "Cache-Control": "no-store" } });
    response.cookies.set(FIREBASE_ID_TOKEN_COOKIE, tokens.idToken, cookieOptions(tokens.expiresInSeconds));
    response.cookies.set(FIREBASE_REFRESH_TOKEN_COOKIE, tokens.refreshToken, cookieOptions(REFRESH_MAX_AGE_SECONDS));
    response.cookies.set(
      FIREBASE_EXPIRES_AT_COOKIE,
      String(Date.now() + tokens.expiresInSeconds * 1000),
      cookieOptions(tokens.expiresInSeconds),
    );
    return response;
  } catch (error) {
    const apiError = error instanceof CloudRunApiError ? error : null;
    const status = apiError?.status && apiError.status >= 400 && apiError.status <= 599 ? apiError.status : 503;
    return NextResponse.json(
      { error: { code: apiError?.code || "OTP_VERIFY_FAILED", message: apiError?.message || "The verification service is unavailable. Try again later." } },
      { status, headers: { "Cache-Control": "no-store" } },
    );
  }
}
