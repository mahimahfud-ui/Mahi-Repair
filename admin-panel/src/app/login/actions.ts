"use server";

import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

export type OwnerPinLoginState = { error: string | null };
export const initialOwnerPinLoginState: OwnerPinLoginState = { error: null };

const OWNER_EMAIL = (process.env.OWNER_EMAIL || "mahinmhfd@gmail.com").trim().toLowerCase();

export async function signInOwnerWithPin(
  _previousState: OwnerPinLoginState,
  formData: FormData,
): Promise<OwnerPinLoginState> {
  const pin = String(formData.get("pin") ?? "").trim();
  if (!/^\d{8,12}$/.test(pin)) {
    return { error: "Enter the 8–12 digit Owner PIN." };
  }

  const supabase = await createClient();
  const { data, error } = await supabase.auth.signInWithPassword({
    email: OWNER_EMAIL,
    password: pin,
  });

  if (error || !data.user) {
    await supabase.auth.signOut();
    return { error: "Incorrect PIN, or the Owner Auth account is not configured. Please try again." };
  }

  const { data: profile, error: profileError } = await supabase
    .from("profiles")
    .select("role,status")
    .eq("id", data.user.id)
    .maybeSingle();

  if (
    profileError ||
    !profile ||
    profile.role !== "owner" ||
    profile.status !== "active" ||
    (data.user.email || "").toLowerCase() !== OWNER_EMAIL
  ) {
    await supabase.auth.signOut();
    return { error: "Access denied. Only the active configured Owner account can enter this console." };
  }

  redirect("/");
}
