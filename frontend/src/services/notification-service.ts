"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { AppNotification } from "@/types";

import { db, delay, nextId } from "./mock/db";

/**
 * Notification centre (spec §18).
 *
 * Backed by a store because the header bell, the notification page and live
 * auction events all read and mutate the same list.
 */

const notificationStore = createStore<AppNotification[]>([...db.notifications]);

export function useNotifications(): AppNotification[] {
  return useSyncExternalStore(
    notificationStore.subscribe,
    notificationStore.getSnapshot,
    notificationStore.getServerSnapshot,
  );
}

export function useUnreadNotificationCount(): number {
  return useNotifications().filter((item) => !item.isRead).length;
}

/** `GET /api/notifications` */
export async function listNotifications(): Promise<AppNotification[]> {
  await delay();
  return notificationStore.getSnapshot();
}

/** `PATCH /api/notifications/{id}/read` */
export async function markAsRead(notificationId: string): Promise<void> {
  notificationStore.setState((items) =>
    items.map((item) =>
      item.id === notificationId ? { ...item, isRead: true } : item,
    ),
  );
  await delay(120);
}

/** `PATCH /api/notifications/read-all` */
export async function markAllAsRead(): Promise<void> {
  notificationStore.setState((items) =>
    items.map((item) => (item.isRead ? item : { ...item, isRead: true })),
  );
  await delay(160);
}

export async function removeNotification(notificationId: string): Promise<void> {
  notificationStore.setState((items) =>
    items.filter((item) => item.id !== notificationId),
  );
  await delay(120);
}

/**
 * Pushes a notification locally. In production these arrive over the
 * WebSocket notification channel rather than being created client-side.
 */
export function pushNotification(
  notification: Omit<AppNotification, "id" | "userId" | "isRead" | "createdAt">,
): void {
  notificationStore.setState((items) => [
    {
      ...notification,
      id: nextId("notif"),
      userId: "user-pin",
      isRead: false,
      createdAt: new Date().toISOString(),
    },
    ...items,
  ]);
}
