"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { IsoDateString, Unsubscribe } from "@/types";

import { api } from "./api/http";
import { subscribeToMessages } from "./realtime-service";
import { onSessionChange } from "./session-service";

/**
 * EN: Messages between the buyer and the seller of an order. The unread count is a store, shared by the account menu
 *     and the inbox; new messages arrive over the socket and bump it, and an open thread appends them.
 * VI: Tin nhắn giữa người mua và người bán của một đơn. Số tin chưa đọc là một store, dùng chung cho menu tài khoản và hộp
 *     thư; tin mới tới qua socket và tăng số đó, còn cuộc trò chuyện đang mở thì thêm tin vào.
 */
export interface OrderMessage {
  id: string;
  orderId: string;
  mine: boolean;
  /** EN: Masked handle, "lin***". / VI: Tên đã che, "lin***". */
  sender: string;
  body: string;
  createdAt: IsoDateString;
  readAt?: IsoDateString | null;
}

export interface MessageThread {
  orderId: string;
  auctionId: string;
  lotTitle: string;
  coverImageUrl: string | null;
  counterpart: string;
  asSeller: boolean;
  lastMessage: string | null;
  lastMine: boolean;
  lastAt: IsoDateString | null;
  unread: number;
  /** EN: False once the order is cancelled or long completed. / VI: False khi đơn đã huỷ hoặc hoàn tất lâu rồi. */
  open: boolean;
}

const unreadStore = createStore<number>(0);
const arrivals = new Set<(message: OrderMessage) => void>();
let started = false;
let leave: Unsubscribe | null = null;

function start(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  onSessionChange((user) => {
    if (user) {
      void refreshUnreadMessages();
      leave ??= subscribeToMessages((raw) => {
        const message = raw as OrderMessage;
        for (const listener of arrivals) listener(message);
        // EN: The server knows best: an open thread may already have marked it read. / VI: Server biết chính xác nhất: cuộc trò chuyện đang mở có thể đã đánh dấu đã đọc.
        void refreshUnreadMessages();
      });
    } else {
      leave?.();
      leave = null;
      unreadStore.setState(0);
    }
  });
}

/** `GET /api/messages/unread-count` */
async function refreshUnreadMessages(): Promise<void> {
  try {
    const { unread } = await api<{ unread: number }>("/api/messages/unread-count");
    unreadStore.setState(unread);
  } catch {
    // EN: The count is a hint; a failed read leaves it as it was. / VI: Con số chỉ để gợi ý; đọc lỗi thì giữ nguyên.
  }
}

export function useUnreadMessageCount(): number {
  start();
  return useSyncExternalStore(unreadStore.subscribe, unreadStore.getSnapshot, unreadStore.getServerSnapshot);
}

/** EN: Called for each message that arrives live, e.g. by the open thread. / VI: Được gọi cho mỗi tin tới trực tiếp, ví dụ bởi cuộc trò chuyện đang mở. */
export function onMessageArrival(listener: (message: OrderMessage) => void): Unsubscribe {
  start();
  arrivals.add(listener);
  return () => arrivals.delete(listener);
}

/** `GET /api/messages` — the caller's conversations, latest first. */
export async function listThreads(): Promise<MessageThread[]> {
  return api<MessageThread[]>("/api/messages");
}

/** `GET /api/orders/{id}/messages` — opens a thread and marks it read. */
export async function openThread(orderId: string): Promise<{ thread: MessageThread; messages: OrderMessage[] }> {
  const detail = await api<{ thread: MessageThread; messages: OrderMessage[] }>(`/api/orders/${orderId}/messages`);
  void refreshUnreadMessages();
  return detail;
}

/** `POST /api/orders/{id}/messages` */
export async function sendMessage(orderId: string, body: string): Promise<OrderMessage> {
  return api<OrderMessage>(`/api/orders/${orderId}/messages`, { method: "POST", body: { body: body.trim() } });
}
