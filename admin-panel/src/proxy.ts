import { NextResponse, type NextRequest } from "next/server";
import { refreshFirebaseSession } from "@/lib/auth/firebase-rest";
import {
  FIREBASE_EXPIRES_AT_COOKIE,
  FIREBASE_ID_TOKEN_COOKIE,
  FIREBASE_REFRESH_TOKEN_COOKIE,
} from "@/lib/auth/cookies";

const REFRESH_SKEW_MS = 5 * 60 * 1000;
const REFRESH_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

export async function proxy(request: NextRequest) {
  const idToken = request.cookies.get(FIREBASE_ID_TOKEN_COOKIE)?.value;
  const refreshToken = request.cookies.get(FIREBASE_REFRESH_TOKEN_COOKIE)?.value;
  const expiresAt = Number(request.cookies.get(FIREBASE_EXPIRES_AT_COOKIE)?.value || 0);
  if (!idToken || !refreshToken || !Number.isFinite(expiresAt)) {
    return NextResponse.next();
  }

  if (expiresAt - Date.now() > REFRESH_SKEW_MS) {
    return NextResponse.next();
  }

  try {
    const refreshed = await refreshFirebaseSession(refreshToken);
    request.cookies.set(FIREBASE_ID_TOKEN_COOKIE, refreshed.idToken);
    request.cookies.set(FIREBASE_REFRESH_TOKEN_COOKIE, refreshed.refreshToken);
    request.cookies.set(FIREBASE_EXPIRES_AT_COOKIE, String(Date.now() + refreshed.expiresInSeconds * 1000));
    const response = NextResponse.next({ request });
    response.cookies.set(FIREBASE_ID_TOKEN_COOKIE, refreshed.idToken, cookieOptions(refreshed.expiresInSeconds));
    response.cookies.set(FIREBASE_REFRESH_TOKEN_COOKIE, refreshed.refreshToken, cookieOptions(REFRESH_MAX_AGE_SECONDS));
    response.cookies.set(
      FIREBASE_EXPIRES_AT_COOKIE,
      String(Date.now() + refreshed.expiresInSeconds * 1000),
      cookieOptions(refreshed.expiresInSeconds),
    );
    return response;
  } catch {
    const response = NextResponse.next();
    for (const name of [FIREBASE_ID_TOKEN_COOKIE, FIREBASE_REFRESH_TOKEN_COOKIE, FIREBASE_EXPIRES_AT_COOKIE]) {
      response.cookies.set(name, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 0 });
    }
    return response;
  }
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/auth/request-otp|api/auth/verify-otp).*)"],
};
