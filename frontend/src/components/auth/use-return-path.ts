"use client";

import { useSyncExternalStore } from "react";

import { safeRedirect } from "@/lib/redirect";
import { hasRole } from "@/services/session-service";
import type { User } from "@/types";

const noSubscription = () => () => {};

function readRedirect(): string | null {
  return safeRedirect(new URLSearchParams(window.location.search).get("redirect"));
}

/**
 * EN: The page a sign-in form should return to, read from `?redirect=` on the client only, so the auth
 *     pages stay static. Null on the server and when there is none.
 * VI: Trang mà form đăng nhập cần quay về, đọc từ `?redirect=` chỉ ở client để trang auth vẫn tĩnh.
 *     Null trên server và khi không có.
 */
export function useReturnPath(): string | null {
  return useSyncExternalStore(noSubscription, readRedirect, () => null);
}

/** EN: Where a freshly signed-in user lands. / VI: Nơi người vừa đăng nhập được đưa tới. */
export function landingFor(user: User, returnPath: string | null): string {
  return returnPath ?? (hasRole(user, "ADMIN") ? "/admin" : "/");
}

/** EN: A link to the other auth form that keeps the return path. / VI: Link sang form auth kia, giữ nguyên trang cần quay về. */
export function withReturnPath(href: "/login" | "/register", returnPath: string | null): string {
  return returnPath ? `${href}?redirect=${encodeURIComponent(returnPath)}` : href;
}
