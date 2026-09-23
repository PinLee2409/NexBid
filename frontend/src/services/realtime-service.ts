"use client";

import { applyExtension, willTriggerExtension } from "@/lib/auction-rules";
import type { AuctionEvent, Bid, Unsubscribe } from "@/types";

import { db, findAuction, nextId } from "./mock/db";

/**
 * Mock realtime transport.
 *
 * The real implementation subscribes to `/topic/auctions/{id}` over WebSocket
 * (spec §10). This one drives the same `AuctionEvent` union from a timer so the
 * bidding UI can be built, reviewed and demoed before the backend exists.
 *
 * Consumers only ever see `subscribeToAuction` and `publish`, so swapping in a
 * STOMP/WebSocket client is a change to this file alone.
 */

type Listener = (event: AuctionEvent) => void;

interface Room {
  listeners: Set<Listener>;
  timer: number | null;
}

const rooms = new Map<string, Room>();

/** Rival bidders used by the simulation. Never includes the current user. */
const RIVAL_BIDDER_IDS = ["user-alex", "user-john", "user-sara", "user-mika"];

function emit(auctionId: string, event: AuctionEvent): void {
  const room = rooms.get(auctionId);
  if (!room) return;
  for (const listener of room.listeners) listener(event);
}

/**
 * Publishes an event to every subscriber of an auction. Used by the bid
 * service after a successful write, mirroring the backend's
 * "commit, then broadcast" ordering (spec §7.8).
 */
export function publish(event: AuctionEvent): void {
  emit(event.auctionId, event);
}

/**
 * Simulates another bidder acting. Applies exactly the rules the server would:
 * price + increment, anti-sniping extension, bid count.
 */
function simulateRivalBid(auctionId: string): void {
  const auction = findAuction(auctionId);
  if (!auction || auction.status !== "ACTIVE") return;

  const now = Date.now();
  if (new Date(auction.endTime).getTime() <= now) return;

  const bidderId =
    RIVAL_BIDDER_IDS[Math.floor(Math.random() * RIVAL_BIDDER_IDS.length)];
  const bidder = db.users.find((user) => user.id === bidderId);
  if (!bidder || auction.sellerId === bidderId) return;

  const amount = auction.currentPrice + auction.minimumIncrement;

  const bid: Bid = {
    id: nextId("bid"),
    auctionId,
    bidderId,
    bidderDisplayName: bidder.displayName,
    amount,
    automatic: false,
    createdAt: new Date(now).toISOString(),
  };

  const extends_ = willTriggerExtension(auction, now);

  db.bids.push(bid);
  auction.currentPrice = amount;
  auction.bidCount += 1;
  auction.updatedAt = bid.createdAt;

  if (extends_) {
    auction.endTime = applyExtension(
      auction.endTime,
      auction.antiSniping.extensionSeconds,
    );
    auction.extensionCount += 1;
  }

  emit(auctionId, {
    type: "BID_PLACED",
    auctionId,
    currentPrice: amount,
    totalBids: auction.bidCount,
    bid,
    serverTime: bid.createdAt,
  });

  if (extends_) {
    emit(auctionId, {
      type: "AUCTION_EXTENDED",
      auctionId,
      endTime: auction.endTime,
      extensionSeconds: auction.antiSniping.extensionSeconds,
      serverTime: new Date().toISOString(),
    });
  }
}

function simulateViewerChange(auctionId: string): void {
  const auction = findAuction(auctionId);
  if (!auction || auction.status !== "ACTIVE") return;

  emit(auctionId, {
    type: "VIEWERS_CHANGED",
    auctionId,
    viewerCount: Math.max(
      8,
      Math.round(40 + auction.bidCount * 4 + (Math.random() - 0.5) * 24),
    ),
    serverTime: new Date().toISOString(),
  });
}

/** Rival activity speeds up as the close approaches, like a real room. */
function nextTickDelay(auctionId: string): number {
  const auction = findAuction(auctionId);
  if (!auction) return 20_000;

  const remaining = new Date(auction.endTime).getTime() - Date.now();
  if (remaining <= 60_000) return 6_000 + Math.random() * 6_000;
  if (remaining <= 10 * 60_000) return 10_000 + Math.random() * 10_000;
  return 18_000 + Math.random() * 22_000;
}

function startRoomTimer(auctionId: string): void {
  const room = rooms.get(auctionId);
  if (!room || room.timer !== null) return;

  const schedule = () => {
    const current = rooms.get(auctionId);
    if (!current || current.listeners.size === 0) return;

    current.timer = window.setTimeout(() => {
      // Most ticks are just viewers moving; bids are the rarer, louder event.
      if (Math.random() < 0.55) {
        simulateRivalBid(auctionId);
      } else {
        simulateViewerChange(auctionId);
      }
      schedule();
    }, nextTickDelay(auctionId));
  };

  schedule();
}

/**
 * Joins an auction room. Returns an unsubscribe function — the last subscriber
 * leaving stops the simulation, the same way closing a socket would.
 */
export function subscribeToAuction(
  auctionId: string,
  listener: Listener,
): Unsubscribe {
  let room = rooms.get(auctionId);

  if (!room) {
    room = { listeners: new Set(), timer: null };
    rooms.set(auctionId, room);
  }

  room.listeners.add(listener);
  startRoomTimer(auctionId);

  return () => {
    const current = rooms.get(auctionId);
    if (!current) return;

    current.listeners.delete(listener);

    if (current.listeners.size === 0) {
      if (current.timer !== null) window.clearTimeout(current.timer);
      rooms.delete(auctionId);
    }
  };
}
