import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { CloudRunApiClient, CloudRunApiError } from "@/lib/api/cloud-run";

import { OWNER_EMAIL, FIREBASE_ID_TOKEN_COOKIE } from "@/lib/auth/cookies";

export async function requireOwner() {
  const cookieStore = await cookies();
  const idToken = cookieStore.get(FIREBASE_ID_TOKEN_COOKIE)?.value;
  if (!idToken) redirect("/login?error=unauthorized");

  const api = new CloudRunApiClient(idToken);
  let identity: Awaited<ReturnType<CloudRunApiClient["verifyIdentity"]>>;
  try {
    identity = await api.verifyIdentity();
  } catch (error) {
    const status = error instanceof CloudRunApiError ? error.status : 0;
    if (status === 401) redirect("/login?error=unauthorized");
    if (status === 403) redirect("/login?error=forbidden");
    throw error;
  }

  if (
    identity.email.trim().toLowerCase() !== OWNER_EMAIL ||
    identity.role !== "owner" ||
    identity.status !== "active" ||
    identity.isOwner !== true
  ) {
    redirect("/login?error=forbidden");
  }

  return { api, idToken, identity };
}
