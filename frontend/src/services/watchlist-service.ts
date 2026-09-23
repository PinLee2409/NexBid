"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { AuctionSummary, WatchlistItem } from "@/types";

import {
  CURRENT_USER_ID,
  db,
  delay,
  findAuction,
  reconcileAuctionStatuses,
  toAuctionSummary,
} from "./mock/db";

/**
 * Watchlist state (spec §15).
 *
 * Toggling is optimistic and published through a store so every `WatchButton`
 * for the same auction stays in sync, wherever it is rendered.
 */

const watchStore = createStore<ReadonlySet<string>>(
  new Set(db.watchedAuctionIds),
);

export function useWatchedAuctionIds(): ReadonlySet<string> {
  return useSyncExternalStore(
    watchStore.subscribe,
    watchStore.getSnapshot,
    watchStore.getServerSnapshot,
  );
}

export function useIsWatched(auctionId: string): boolean {
  return useWatchedAuctionIds().has(auctionId);
}

/** `POST /api/auctions/{id}/watch` and `DELETE /api/auctions/{id}/watch` */
export async function toggleWatch(auctionId: string): Promise<boolean> {
  const next = new Set(watchStore.getSnapshot());
  const willWatch = !next.has(auctionId);

  if (willWatch) {
    next.add(auctionId);
  } else {
    next.delete(auctionId);
  }

  // Optimistic: update the UI first, then reconcile with the "server".
  watchStore.setState(next);
  await delay(180);
  db.watchedAuctionIds = new Set(next);

  return willWatch;
}

/** `GET /api/users/me/watchlist` */
export async function listWatchlist(): Promise<WatchlistItem[]> {
  await delay();
  reconcileAuctionStatuses();

  return [...watchStore.getSnapshot()]
    .map((auctionId, index): WatchlistItem | null => {
      const auction = findAuction(auctionId);
      if (!auction) return null;

      return {
        id: `watch-${auctionId}`,
        userId: CURRENT_USER_ID,
        auctionId,
        createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
        auction: toAuctionSummary(auction),
      };
    })
    .filter((item): item is WatchlistItem => item !== null);
}

export async function listWatchedAuctions(): Promise<AuctionSummary[]> {
  const items = await listWatchlist();
  return items.map((item) => item.auction);
}
