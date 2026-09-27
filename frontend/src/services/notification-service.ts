"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { AppNotification, Unsubscribe } from "@/types";

import type { ApiNotification, ApiNotificationInbox } from "./api/dto";
import { api } from "./api/http";
import { toNotification } from "./api/mappers";
import { subscribeToInbox } from "./realtime-service";
import { getCurrentUser, onSessionChange } from "./session-service";

/**
 * Notification centre (spec §18).
 *
 * EN: Backed by a store because the header bell and the notification page read and mutate the same list.
 *     While someone is signed in, new notices arrive on their private socket channel the moment they are
 *     written; the list is re-read on sign-in and after every reconnect, so nothing sent while offline is lost.
 * VI: Dựa trên một store vì cái chuông ở header và trang thông báo cùng đọc và sửa một danh sách. Khi có người
 *     đăng nhập, thông báo mới tới qua kênh socket riêng ngay khi được ghi; danh sách được đọc lại khi đăng nhập
 *     và sau mỗi lần kết nối lại, nên không mất gì được gửi trong lúc mất kết nối.
 */

interface Inbox {
  items: AppNotification[];
  unread: number;
}

const EMPTY: Inbox = { items: [], unread: 0 };
const PAGE_SIZE = 50;

const notificationStore = createStore<Inbox>(EMPTY);
const arrivals = new Set<(notification: AppNotification) => void>();

let started = false;
let leaveInbox: Unsubscribe | null = null;

function start(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  onSessionChange((user) => {
    if (user) {
      void listNotifications().catch(() => undefined);
      leaveInbox ??= subscribeToInbox({
        onNotice: receive,
        onReady: () => void listNotifications().catch(() => undefined),
      });
    } else {
      leaveInbox?.();
      leaveInbox = null;
      notificationStore.setState(EMPTY);
    }
  });
}

/** EN: A notice pushed by the server; a repeat of one already listed is ignored. / VI: Thông báo server đẩy tới; bản lặp của thông báo đã có thì bỏ qua. */
function receive(notice: ApiNotification): void {
  const item = toNotification(notice, getCurrentUser()?.id ?? "");
  let isNew = false;
  notificationStore.setState((inbox) => {
    if (inbox.items.some((existing) => existing.id === item.id)) return inbox;
    isNew = true;
    return {
      items: [item, ...inbox.items].slice(0, PAGE_SIZE),
      unread: inbox.unread + (item.isRead ? 0 : 1),
    };
  });
  if (isNew) for (const listener of arrivals) listener(item);
}

/**
 * EN: Called for each notice that arrives live — e.g. to show it as a toast.
 * VI: Được gọi cho mỗi thông báo tới trực tiếp — ví dụ để hiện dạng toast.
 */
export function onNotificationArrival(listener: (notification: AppNotification) => void): Unsubscribe {
  start();
  arrivals.add(listener);
  return () => arrivals.delete(listener);
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
async function listNotifications(): Promise<AppNotification[]> {
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
