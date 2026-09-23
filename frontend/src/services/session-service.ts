"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { User, UserRole } from "@/types";

import { CURRENT_USER_ID, db, delay } from "./mock/db";

/**
 * Client-side session.
 *
 * Today it is backed by the mock user table and `localStorage`. When JWT auth
 * lands, only `signIn`/`signUp`/`signOut` and `readPersistedSession` change —
 * every consumer keeps using `useSession()`.
 */

const STORAGE_KEY = "nexbid.session";

export interface SessionState {
  user: User | null;
  /** False until the persisted session has been read on the client. */
  hydrated: boolean;
}

function demoUser(): User | null {
  return db.users.find((user) => user.id === CURRENT_USER_ID) ?? null;
}

/**
 * The portfolio build opens signed in so the full experience is reachable
 * without a login step. A persisted "signed out" choice overrides this.
 */
const sessionStore = createStore<SessionState>({
  user: demoUser(),
  hydrated: false,
});

let initialised = false;

function readPersistedSession(): void {
  if (initialised || typeof window === "undefined") return;
  initialised = true;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      sessionStore.setState((state) => ({ ...state, hydrated: true }));
      return;
    }

    const parsed = JSON.parse(raw) as { userId: string | null };
    const user = parsed.userId
      ? (db.users.find((item) => item.id === parsed.userId) ?? null)
      : null;

    sessionStore.setState({ user, hydrated: true });
  } catch {
    sessionStore.setState((state) => ({ ...state, hydrated: true }));
  }
}

function persist(user: User | null): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ userId: user?.id ?? null }),
    );
  } catch {
    // Storage can be unavailable (private mode); the session still works
    // for the current page view.
  }
}

export function useSession(): SessionState {
  readPersistedSession();

  return useSyncExternalStore(
    sessionStore.subscribe,
    sessionStore.getSnapshot,
    sessionStore.getServerSnapshot,
  );
}

/** Convenience helper for role-gated UI. */
export function hasRole(user: User | null, role: UserRole): boolean {
  return user?.roles.includes(role) ?? false;
}

export function getCurrentUser(): User | null {
  return sessionStore.getSnapshot().user;
}

export interface Credentials {
  email: string;
  password: string;
}

export interface SignUpInput extends Credentials {
  fullName: string;
}

export type AuthResult =
  | { ok: true; user: User }
  | { ok: false; message: string };

/** `POST /api/auth/login` */
export async function signIn({ email }: Credentials): Promise<AuthResult> {
  await delay(500);

  const user = db.users.find(
    (item) => item.email.toLowerCase() === email.trim().toLowerCase(),
  );

  // Any password is accepted while the backend is mocked, but the blocked and
  // unknown-account paths behave exactly as specified (§7.2).
  if (!user) {
    return { ok: false, message: "Incorrect email or password." };
  }

  if (user.status === "BLOCKED") {
    return { ok: false, message: "This account has been suspended." };
  }

  sessionStore.setState({ user, hydrated: true });
  persist(user);
  return { ok: true, user };
}

/** `POST /api/auth/register` */
export async function signUp({ fullName, email }: SignUpInput): Promise<AuthResult> {
  await delay(600);

  const normalizedEmail = email.trim().toLowerCase();
  if (db.users.some((item) => item.email.toLowerCase() === normalizedEmail)) {
    return { ok: false, message: "An account with this email already exists." };
  }

  const user: User = {
    id: `user-${Date.now().toString(36)}`,
    fullName: fullName.trim(),
    email: normalizedEmail,
    displayName: `${fullName.trim().slice(0, 3).toLowerCase()}***`,
    roles: ["BUYER"],
    status: "ACTIVE",
    createdAt: new Date().toISOString(),
  };

  db.users.push(user);
  sessionStore.setState({ user, hydrated: true });
  persist(user);
  return { ok: true, user };
}

export function signOut(): void {
  sessionStore.setState({ user: null, hydrated: true });
  persist(null);
}
