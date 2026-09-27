"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { User, UserRole } from "@/types";

import type { ApiLogin, ApiUser } from "./api/dto";
import { ApiError, api } from "./api/http";
import { toUser } from "./api/mappers";
import { keepSessionFresh } from "./api/session-refresh";
import { clearToken, getToken, hasSession, onTokenChange, setToken } from "./api/token-store";

/**
 * Client-side session, backed by the JWT from `POST /api/auth/login`.
 *
 * EN: The token is the truth: with one, the account comes from `GET /api/users/me`; without one (or
 *     once the server refuses both it and the refresh cookie) the reader is signed out.
 * VI: Token là sự thật: có token thì thông tin tài khoản lấy từ `GET /api/users/me`; không có (hoặc server
 *     từ chối cả nó lẫn cookie refresh) thì người đọc đã đăng xuất.
 */

export interface SessionState {
  user: User | null;
  /** False until the persisted session has been read on the client. */
  hydrated: boolean;
}

const sessionStore = createStore<SessionState>({ user: null, hydrated: false });

let started = false;

async function loadAccount(): Promise<void> {
  // EN: An expired token still means the refresh cookie is worth a try; api() renews before asking.
  // VI: Token hết hạn vẫn có nghĩa là đáng thử cookie refresh; api() sẽ gia hạn trước khi hỏi.
  if (!getToken() && !hasSession()) {
    sessionStore.setState({ user: null, hydrated: true });
    return;
  }
  try {
    const me = await api<ApiUser>("/api/users/me");
    sessionStore.setState({ user: toUser(me), hydrated: true });
  } catch {
    // EN: A rejected token was already cleared by the HTTP client. / VI: Token bị từ chối đã được HTTP client xoá.
    sessionStore.setState({ user: null, hydrated: true });
  }
}

function start(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  onTokenChange(() => {
    if (!hasSession()) sessionStore.setState({ user: null, hydrated: true });
  });
  keepSessionFresh();
  void loadAccount();
}

export function useSession(): SessionState {
  start();
  return useSyncExternalStore(
    sessionStore.subscribe,
    sessionStore.getSnapshot,
    sessionStore.getServerSnapshot,
  );
}

/**
 * EN: For stores that must follow who is signed in. Also called once straight away when someone is already
 *     signed in, e.g. a store first used on the page the sign-in form redirected to.
 * VI: Cho các store cần đi theo người đang đăng nhập. Cũng được gọi ngay một lần nếu đã có người đăng nhập,
 *     ví dụ store được dùng lần đầu ở trang mà form đăng nhập chuyển tới.
 */
export function onSessionChange(listener: (user: User | null) => void): () => void {
  start();
  const current = sessionStore.getSnapshot().user;
  let previous = current?.id ?? null;
  if (current) queueMicrotask(() => listener(current));
  return sessionStore.subscribe(() => {
    const user = sessionStore.getSnapshot().user;
    if ((user?.id ?? null) === previous) return;
    previous = user?.id ?? null;
    listener(user);
  });
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
  /** EN: Stay signed in after the browser closes. Defaults to yes. / VI: Vẫn đăng nhập sau khi đóng trình duyệt. Mặc định là có. */
  remember?: boolean;
}

export interface SignUpInput extends Credentials {
  fullName: string;
}

export type AuthResult =
  | { ok: true; user: User }
  | { ok: false; message: string };

function failure(error: unknown): AuthResult {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "INVALID_CREDENTIALS":
        return { ok: false, message: "Incorrect email or password." };
      case "ACCOUNT_BLOCKED":
        return { ok: false, message: "This account has been suspended." };
      case "EMAIL_ALREADY_EXISTS":
        return { ok: false, message: "An account with this email already exists." };
      case "VALIDATION_ERROR": {
        const first = error.details ? Object.values(error.details)[0] : undefined;
        return { ok: false, message: first ?? error.message };
      }
      case "BID_RATE_LIMITED":
        return { ok: false, message: "Too many attempts. Wait a moment and try again." };
      default:
        break;
    }
  }
  return { ok: false, message: "Something went wrong. Please try again." };
}

/** `POST /api/auth/login` */
export async function signIn({ email, password, remember = true }: Credentials): Promise<AuthResult> {
  try {
    const login = await api<ApiLogin>("/api/auth/login", {
      method: "POST",
      body: { email: email.trim(), password, rememberMe: remember },
    });
    setToken(login.accessToken, login.expiresAt);
    // EN: The login answer has no join date; the profile page shows one. / VI: Kết quả đăng nhập không có ngày tham gia; trang hồ sơ cần nó.
    const me = await api<ApiUser>("/api/users/me");
    const user = toUser(me);
    sessionStore.setState({ user, hydrated: true });
    return { ok: true, user };
  } catch (error) {
    return failure(error);
  }
}

/** `POST /api/auth/register`, then signs straight in. */
export async function signUp({ fullName, email, password }: SignUpInput): Promise<AuthResult> {
  try {
    await api<ApiUser>("/api/auth/register", {
      method: "POST",
      body: { fullName: fullName.trim(), email: email.trim(), password },
    });
  } catch (error) {
    return failure(error);
  }
  return signIn({ email, password });
}

/** EN: `POST /api/auth/logout` ends the session on the server too. / VI: `POST /api/auth/logout` kết thúc cả phiên trên server. */
export async function signOut(): Promise<void> {
  try {
    await api<void>("/api/auth/logout", { method: "POST" });
  } catch {
    // EN: Offline: the cookie expires on its own; this device is signed out regardless.
    // VI: Mất mạng: cookie tự hết hạn; thiết bị này vẫn được đăng xuất.
  }
  clearToken();
  sessionStore.setState({ user: null, hydrated: true });
}

/** EN: Re-reads the account, e.g. after renaming it. / VI: Đọc lại tài khoản, ví dụ sau khi đổi tên. */
export function applyAccount(me: ApiUser): User {
  const user = toUser(me);
  sessionStore.setState({ user, hydrated: true });
  return user;
}
