"use client";

import { useSyncExternalStore } from "react";

import { createStore } from "@/lib/create-store";
import type { AuctionSummary, WatchlistItem } from "@/types";

import type { ApiAuctionSummary } from "./api/dto";
import { api } from "./api/http";
import { toSummary } from "./api/mappers";
import { getCurrentUser, onSessionChange } from "./session-service";

/**
 * Watchlist state (spec §15).
 *
 * Toggling is optimistic and published through a store so every `WatchButton`
 * for the same auction stays in sync, wherever it is rendered. The set follows
 * whoever is signed in.
 */

const watchStore = createStore<ReadonlySet<string>>(new Set());

let started = false;

function start(): void {
  if (started || typeof window === "undefined") return;
  started = true;
  onSessionChange((user) => {
    if (user) void listWatchlist().catch(() => undefined);
    else watchStore.setState(new Set());
  });
}

export function useWatchedAuctionIds(): ReadonlySet<string> {
  start();
  return useSyncExternalStore(
    watchStore.subscribe,
    watchStore.getSnapshot,
    watchStore.getServerSnapshot,
  );
}

export function useIsWatched(auctionId: string): boolean {
  return useWatchedAuctionIds().has(auctionId);
}

/**
 * `POST /api/auctions/{id}/watch` and `DELETE /api/auctions/{id}/watch`.
 * Returns whether the lot is watched afterwards; signed out, nothing changes.
 */
export async function toggleWatch(auctionId: string): Promise<boolean> {
  const before = watchStore.getSnapshot();
  const willWatch = !before.has(auctionId);
  if (!getCurrentUser()) return !willWatch;

  const next = new Set(before);
  if (willWatch) next.add(auctionId);
  else next.delete(auctionId);
  // Optimistic: update the UI first, then confirm with the server.
  watchStore.setState(next);

  try {
    await api(`/api/auctions/${auctionId}/watch`, { method: willWatch ? "POST" : "DELETE" });
    return willWatch;
  } catch {
    watchStore.setState((current) => {
      const reverted = new Set(current);
      if (willWatch) reverted.delete(auctionId);
      else reverted.add(auctionId);
      return reverted;
    });
    return !willWatch;
  }
}

/** `GET /api/users/me/watchlist` */
export async function listWatchlist(): Promise<WatchlistItem[]> {
  const lots = await api<ApiAuctionSummary[]>("/api/users/me/watchlist");
  const userId = getCurrentUser()?.id ?? "";
  const items = lots.map((lot) => {
    const auction = toSummary(lot, true);
    return {
      id: `watch-${auction.id}`,
      userId,
      auctionId: auction.id,
      createdAt: auction.createdAt,
      auction,
    };
  });
  watchStore.setState(new Set(items.map((item) => item.auctionId)));
  return items;
}

export async function listWatchedAuctions(): Promise<AuctionSummary[]> {
  const items = await listWatchlist();
  return items.map((item) => item.auction);
}
