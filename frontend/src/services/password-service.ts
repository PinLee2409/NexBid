"use client";

import { api } from "./api/http";

/** `POST /api/auth/password/forgot` — the same answer whether or not the email has an account. */
export async function requestPasswordReset(email: string): Promise<void> {
  await api("/api/auth/password/forgot", { method: "POST", body: { email } });
}

/** `POST /api/auth/password/reset` — sets a new password from an emailed link. */
export async function resetPassword(token: string, password: string): Promise<void> {
  await api("/api/auth/password/reset", { method: "POST", body: { token, password } });
}

/** `POST /api/users/me/password` — this device stays signed in; every other one is signed out. */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api("/api/users/me/password", { method: "POST", body: { currentPassword, newPassword } });
}
