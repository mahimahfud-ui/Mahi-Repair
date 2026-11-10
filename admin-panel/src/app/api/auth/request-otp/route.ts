import { NextResponse } from "next/server";
import { CloudRunAuthClient } from "@/lib/auth/firebase-rest";
import { CloudRunApiError } from "@/lib/api/cloud-run";

function sameOrigin(request: Request): boolean {
  const origin = request.headers.get("origin");
  if (!origin) return false;
  return origin === new URL(request.url).origin;
}

export async function POST(request: Request) {
  if (!sameOrigin(request)) {
    return NextResponse.json({ error: { code: "INVALID_ORIGIN", message: "Request origin is not allowed." } }, { status: 403 });
  }

  try {
    const body = await request.json() as { email?: unknown };
    if (typeof body.email !== "string") {
      return NextResponse.json({ error: { code: "INVALID_INPUT", message: "Enter a valid email address." } }, { status: 400 });
    }
    const accepted = await new CloudRunAuthClient().requestOtp(body.email);
    return NextResponse.json({ data: accepted }, { status: 200, headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const apiError = error instanceof CloudRunApiError ? error : null;
    return NextResponse.json(
      { error: { code: apiError?.code || "OTP_REQUEST_FAILED", message: apiError?.message || "Unable to send a sign-in code. Try again later." } },
      { status: apiError?.status && apiError.status >= 400 && apiError.status <= 599 ? apiError.status : 503, headers: { "Cache-Control": "no-store" } },
    );
  }
}
