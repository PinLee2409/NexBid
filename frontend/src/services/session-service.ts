"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { User, UserRole } from "@/types";

import type { ApiLogin, ApiUser } from "./api/dto";
import { api } from "./api/http";
import { toUser } from "./api/mappers";
import { keepSessionFresh, refreshSession } from "./api/session-refresh";
import { clearToken, getToken, hasSession, onTokenChange, rolesOf, setToken } from "./api/token-store";

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
    await reloadAccount();
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

/** EN: A failure carries the error itself; the form puts it in the reader's language. / VI: Lỗi mang theo chính nó; form tự diễn đạt theo ngôn ngữ người đọc. */
export type AuthResult =
  | { ok: true; user: User }
  | { ok: false; error: unknown };

function failure(error: unknown): AuthResult {
  return { ok: false, error };
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

/**
 * EN: Roles travel inside the access token, so one granted after it was issued (an approved seller request) is
 *     refused by the API until the token is replaced. The account is read from the database, so it knows first.
 * VI: Vai trò nằm trong access token, nên vai trò được cấp sau khi token ra đời (yêu cầu bán hàng được duyệt) bị
 *     API từ chối cho tới khi token được thay. Tài khoản đọc từ database, nên biết trước.
 */
async function catchUpRoles(user: User): Promise<void> {
  const token = getToken();
  const issued = rolesOf(token);
  if (token && user.roles.some((role) => !issued.includes(role))) await refreshSession(token);
}

/**
 * EN: Re-reads the account, renewing the token first if a role was granted meanwhile.
 * VI: Đọc lại tài khoản, gia hạn token trước nếu trong lúc đó có vai trò mới được cấp.
 */
export async function reloadAccount(): Promise<User> {
  const user = toUser(await api<ApiUser>("/api/users/me"));
  await catchUpRoles(user);
  sessionStore.setState({ user, hydrated: true });
  return user;
}

/** EN: Re-reads the account, e.g. after renaming it. / VI: Đọc lại tài khoản, ví dụ sau khi đổi tên. */
export function applyAccount(me: ApiUser): User {
  const user = toUser(me);
  sessionStore.setState({ user, hydrated: true });
  return user;
}
