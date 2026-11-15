"use client";

import { api } from "./api/http";

/** `GET /api/users/me/2fa` */
export async function getTwoFactorEnabled(): Promise<boolean> {
  return (await api<{ enabled: boolean }>("/api/users/me/2fa")).enabled;
}

/** `POST /api/users/me/2fa/setup` — a fresh key for the authenticator app, kept until enabled. */
export async function startTwoFactor(): Promise<{ secret: string; otpauthUri: string }> {
  return api<{ secret: string; otpauthUri: string }>("/api/users/me/2fa/setup", { method: "POST" });
}

/** `POST /api/users/me/2fa/enable` — the first code from the app turns it on; returns the recovery codes. */
export async function enableTwoFactor(code: string): Promise<string[]> {
  return (
    await api<{ recoveryCodes: string[] }>("/api/users/me/2fa/enable", { method: "POST", body: { code: code.trim() } })
  ).recoveryCodes;
}

/** `POST /api/users/me/2fa/disable` — a current code or a recovery code turns it off. */
export async function disableTwoFactor(code: string): Promise<void> {
  await api("/api/users/me/2fa/disable", { method: "POST", body: { code: code.trim() } });
}
