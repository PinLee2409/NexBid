"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { AppNotification } from "@/types";

import type { ApiNotificationInbox } from "./api/dto";
import { api } from "./api/http";
import { toNotification } from "./api/mappers";
import { getCurrentUser, onSessionChange } from "./session-service";

/**
 * Notification centre (spec §18).
 *
 * Backed by a store because the header bell and the notification page read and
 * mutate the same list. Until the per-user WebSocket channel exists (see
 * docs/NexBid_Backlog.md), new notices arrive by polling.
 */

interface Inbox {
  items: AppNotification[];
  unread: number;
}

const EMPTY: Inbox = { items: [], unread: 0 };
const POLL_MS = 30_000;
const PAGE_SIZE = 50;

const notificationStore = createStore<Inbox>(EMPTY);

let started = false;

function start(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  onSessionChange((user) => {
    if (user) void listNotifications().catch(() => undefined);
    else notificationStore.setState(EMPTY);
  });
  window.setInterval(() => {
    if (getCurrentUser() && document.visibilityState === "visible") {
      void listNotifications().catch(() => undefined);
    }
  }, POLL_MS);
}

export function useNotifications(): AppNotification[] {
  start();
  return useSyncExternalStore(
    notificationStore.subscribe,
    () => notificationStore.getSnapshot().items,
    () => notificationStore.getServerSnapshot().items,
  );
}

export function useUnreadNotificationCount(): number {
  start();
  return useSyncExternalStore(
    notificationStore.subscribe,
    () => notificationStore.getSnapshot().unread,
    () => notificationStore.getServerSnapshot().unread,
  );
}

/** `GET /api/notifications` */
export async function listNotifications(): Promise<AppNotification[]> {
  const inbox = await api<ApiNotificationInbox>("/api/notifications", { query: { size: PAGE_SIZE } });
  const userId = getCurrentUser()?.id ?? "";
  const items = inbox.notifications.items.map((item) => toNotification(item, userId));
  notificationStore.setState({ items, unread: inbox.unreadCount });
  return items;
}

/** `PATCH /api/notifications/{id}/read` */
export async function markAsRead(notificationId: string): Promise<void> {
  notificationStore.setState((inbox) => {
    const target = inbox.items.find((item) => item.id === notificationId);
    if (!target || target.isRead) return inbox;
    return {
      items: inbox.items.map((item) => (item.id === notificationId ? { ...item, isRead: true } : item)),
      unread: Math.max(0, inbox.unread - 1),
    };
  });
  try {
    await api(`/api/notifications/${notificationId}/read`, { method: "PATCH" });
  } catch {
    await listNotifications().catch(() => undefined);
  }
}

/** `PATCH /api/notifications/read-all` */
export async function markAllAsRead(): Promise<void> {
  notificationStore.setState((inbox) => ({
    items: inbox.items.map((item) => (item.isRead ? item : { ...item, isRead: true })),
    unread: 0,
  }));
  try {
    await api("/api/notifications/read-all", { method: "PATCH" });
  } catch {
    await listNotifications().catch(() => undefined);
  }
}
