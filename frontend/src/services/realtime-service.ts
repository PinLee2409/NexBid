"use client";

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import type { AuctionEvent, Unsubscribe } from "@/types";

import type { ApiAuctionMessage, ApiNotification } from "./api/dto";
import { getToken, onTokenChange, subjectOf } from "./api/token-store";

/**
 * Realtime transport: STOMP over WebSocket (spec §10, §18).
 *
 * EN: One connection is shared by every open lot page and by the bell. Each lot is a subscription on
 *     `/topic/auctions/{id}`; a signed-in browser also listens on its own `/user/queue/notifications`.
 *     The token travels with CONNECT, so signing in or out reconnects under the new identity.
 * VI: Một kết nối dùng chung cho mọi trang lô đang mở và cho cái chuông. Mỗi lô là một subscription trên
 *     `/topic/auctions/{id}`; trình duyệt đã đăng nhập còn nghe hộp thư riêng `/user/queue/notifications`.
 *     Token đi kèm CONNECT, nên đăng nhập hay đăng xuất sẽ kết nối lại dưới danh tính mới.
 */

// EN: Straight to the backend: the Next.js rewrites only carry HTTP. / VI: Nối thẳng tới backend: rewrite của Next.js chỉ chuyển HTTP.
const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";
const INBOX = "/user/queue/notifications";

type Listener = (event: AuctionEvent) => void;

interface Room {
  listeners: Set<Listener>;
  subscription: StompSubscription | null;
}

/** EN: What the bell needs from the socket. / VI: Những gì cái chuông cần từ socket. */
export interface InboxListener {
  /** EN: A notice was just written for the signed-in user. / VI: Một thông báo vừa được ghi cho người đang đăng nhập. */
  onNotice: (notice: ApiNotification) => void;
  /** EN: The inbox is live again; re-read whatever arrived while it was not. / VI: Hộp thư sống lại; đọc lại những gì tới trong lúc mất kết nối. */
  onReady: () => void;
}

const rooms = new Map<string, Room>();
const inboxListeners = new Set<InboxListener>();
let client: Client | null = null;
let inbox: StompSubscription | null = null;
// EN: The token the current connection carried — only with one is there an inbox to join.
// VI: Token mà kết nối hiện tại mang theo — chỉ khi có token thì mới có hộp thư để vào.
let connectedAs: string | null = null;
// EN: A token whose inbox the server refused (e.g. a blocked account): not retried until the token changes.
// VI: Token bị server từ chối hộp thư (ví dụ tài khoản bị khoá): không thử lại cho tới khi token đổi.
let refusedToken: string | null = null;
let followingToken = false;

function toEvent(message: ApiAuctionMessage): AuctionEvent | null {
  switch (message.type) {
    case "BID_PLACED":
      return {
        type: "BID_PLACED",
        auctionId: message.auctionId,
        currentPrice: Number(message.currentPrice),
        totalBids: message.bidCount,
        bid: {
          // EN: The broadcast carries no bid id; this one only has to be unique on the page.
          // VI: Bản tin không có id lượt trả giá; id này chỉ cần là duy nhất trên trang.
          id: `live:${message.createdAt}:${message.currentPrice}`,
          auctionId: message.auctionId,
          bidderId: `bidder:${message.bidder}`,
          bidderDisplayName: message.bidder,
          amount: Number(message.currentPrice),
          automatic: false,
          createdAt: message.createdAt,
        },
        serverTime: message.createdAt,
      };
    case "AUCTION_EXTENDED":
      // EN: 0 means "work it out from the old end time" (see useAuctionRoom). / VI: 0 nghĩa là "tự tính từ giờ đóng cũ" (xem useAuctionRoom).
      return { type: "AUCTION_EXTENDED", auctionId: message.auctionId, endTime: message.endTime, extensionSeconds: 0, serverTime: message.serverTime };
    case "AUCTION_STARTED":
      return { type: "AUCTION_STARTED", auctionId: message.auctionId, serverTime: message.serverTime };
    case "AUCTION_ENDED":
      // EN: The winner is never broadcast; the price is whatever the page last saw.
      // VI: Người thắng không bao giờ được phát công khai; giá là mức trang thấy gần nhất.
      return { type: "AUCTION_ENDED", auctionId: message.auctionId, winnerId: null, finalPrice: 0, serverTime: message.serverTime };
    case "VIEWER_COUNT":
      return { type: "VIEWERS_CHANGED", auctionId: message.auctionId, viewerCount: message.viewerCount, serverTime: new Date().toISOString() };
    default:
      return null;
  }
}

function deliver(auctionId: string, message: IMessage): void {
  let event: AuctionEvent | null;
  try {
    event = toEvent(JSON.parse(message.body) as ApiAuctionMessage);
  } catch {
    return;
  }
  if (!event) return;
  for (const listener of rooms.get(auctionId)?.listeners ?? []) listener(event);
}

function subscribeRoom(auctionId: string): StompSubscription | null {
  if (!client?.connected) return null;
  return client.subscribe(`/topic/auctions/${auctionId}`, (message) => deliver(auctionId, message));
}

function joinInbox(): void {
  if (inbox || !client?.connected || !connectedAs || connectedAs === refusedToken || inboxListeners.size === 0) {
    return;
  }
  inbox = client.subscribe(INBOX, (message) => {
    let notice: ApiNotification;
    try {
      notice = JSON.parse(message.body) as ApiNotification;
    } catch {
      return;
    }
    for (const listener of inboxListeners) listener.onNotice(notice);
  });
  for (const listener of inboxListeners) listener.onReady();
}

/** EN: Connects again right away, e.g. after signing in. / VI: Kết nối lại ngay, ví dụ sau khi đăng nhập. */
async function reconnect(): Promise<void> {
  const current = client;
  if (!current) return;
  await current.deactivate();
  if (client === current) current.activate();
}

function ensureClient(): void {
  if (client) return;
  if (!followingToken) {
    followingToken = true;
    // EN: Only a change of who is signed in: a renewed token for the same person keeps the socket.
    // VI: Chỉ khi người đăng nhập thay đổi: token được gia hạn cho cùng một người thì giữ nguyên socket.
    onTokenChange(() => {
      if (subjectOf(connectedAs) !== subjectOf(getToken())) void reconnect();
    });
  }

  const instance = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    beforeConnect: (self) => {
      connectedAs = getToken();
      self.connectHeaders = connectedAs ? { Authorization: `Bearer ${connectedAs}` } : {};
    },
    // EN: On every (re)connect, re-join the rooms still open and the inbox. / VI: Mỗi lần (kết nối lại), vào lại các phòng còn mở và hộp thư.
    onConnect: () => {
      inbox = null;
      for (const [auctionId, room] of rooms) room.subscription = subscribeRoom(auctionId);
      joinInbox();
    },
    onStompError: () => {
      if (inbox) refusedToken = connectedAs;
    },
  });
  client = instance;
  instance.activate();
}

/** EN: The last listener leaving closes the socket. / VI: Người nghe cuối cùng rời đi thì đóng socket. */
function closeIfIdle(): void {
  if (rooms.size === 0 && inboxListeners.size === 0 && client) {
    void client.deactivate();
    client = null;
  }
}

/**
 * Joins an auction room. Returns an unsubscribe function — the last listener
 * leaving also closes the socket.
 */
export function subscribeToAuction(auctionId: string, listener: Listener): Unsubscribe {
  let room = rooms.get(auctionId);
  if (!room) {
    room = { listeners: new Set(), subscription: null };
    rooms.set(auctionId, room);
    ensureClient();
    room.subscription = subscribeRoom(auctionId);
  }
  room.listeners.add(listener);

  return () => {
    const current = rooms.get(auctionId);
    if (!current) return;
    current.listeners.delete(listener);
    if (current.listeners.size > 0) return;

    current.subscription?.unsubscribe();
    rooms.delete(auctionId);
    closeIfIdle();
  };
}

/**
 * EN: Listens to the signed-in user's notices (spec §18). Signed out, nothing arrives until someone signs in.
 * VI: Nghe thông báo của người đang đăng nhập (spec §18). Khi chưa đăng nhập thì không có gì tới cho tới khi có người đăng nhập.
 */
export function subscribeToInbox(listener: InboxListener): Unsubscribe {
  inboxListeners.add(listener);
  ensureClient();
  joinInbox();

  return () => {
    inboxListeners.delete(listener);
    if (inboxListeners.size === 0) {
      inbox?.unsubscribe();
      inbox = null;
    }
    closeIfIdle();
  };
}
