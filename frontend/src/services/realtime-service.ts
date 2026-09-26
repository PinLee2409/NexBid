"use client";

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import type { AuctionEvent, Unsubscribe } from "@/types";

import type { ApiAuctionMessage } from "./api/dto";

/**
 * Realtime transport: STOMP over WebSocket to `/topic/auctions/{id}` (spec §10).
 *
 * EN: One connection is shared by every open lot page; each lot is a subscription on it. Messages are
 *     translated into the `AuctionEvent` union the UI already understands.
 * VI: Một kết nối dùng chung cho mọi trang lô đang mở; mỗi lô là một subscription trên kết nối đó. Bản tin
 *     được dịch sang union `AuctionEvent` mà UI vốn đã hiểu.
 */

// EN: Straight to the backend: the Next.js rewrites only carry HTTP. / VI: Nối thẳng tới backend: rewrite của Next.js chỉ chuyển HTTP.
const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";

type Listener = (event: AuctionEvent) => void;

interface Room {
  listeners: Set<Listener>;
  subscription: StompSubscription | null;
}

const rooms = new Map<string, Room>();
let client: Client | null = null;

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

function ensureClient(): void {
  if (client) return;
  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    // EN: On every (re)connect, re-join the rooms still open. / VI: Mỗi lần (kết nối lại), vào lại các phòng còn mở.
    onConnect: () => {
      for (const [auctionId, room] of rooms) room.subscription = subscribeRoom(auctionId);
    },
  });
  client.activate();
}

/**
 * Joins an auction room. Returns an unsubscribe function — the last room
 * closing also closes the socket.
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
    if (rooms.size === 0 && client) {
      void client.deactivate();
      client = null;
    }
  };
}
