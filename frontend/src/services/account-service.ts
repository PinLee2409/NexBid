"use client";

import type { AuctionSummary, Bid, Order, Payment, User } from "@/types";

import {
  CURRENT_USER_ID,
  bidsForAuction,
  db,
  delay,
  findAuction,
  reconcileAuctionStatuses,
  toAuctionSummary,
} from "./mock/db";

/**
 * Buyer account APIs (spec §27 — `/api/users/me/*`).
 */

/** Where the signed-in user stands in one auction they have bid on. */
export type BidStanding = "WINNING" | "OUTBID" | "WON" | "LOST";

export interface MyBidEntry {
  auction: AuctionSummary;
  /** The user's own highest bid on this lot. */
  yourBid: number;
  yourLastBidAt: string;
  standing: BidStanding;
}

function standingFor(
  auctionId: string,
  status: AuctionSummary["status"],
): BidStanding {
  const bids = bidsForAuction(auctionId);
  const leading = bids[0]?.bidderId === CURRENT_USER_ID;
  const finished = status === "ENDED" || status === "COMPLETED";

  if (finished) return leading ? "WON" : "LOST";
  return leading ? "WINNING" : "OUTBID";
}

/** `GET /api/users/me/bids` */
export async function listMyBids(): Promise<MyBidEntry[]> {
  await delay();
  reconcileAuctionStatuses();

  const byAuction = new Map<string, Bid[]>();

  for (const bid of db.bids) {
    if (bid.bidderId !== CURRENT_USER_ID) continue;
    const existing = byAuction.get(bid.auctionId) ?? [];
    existing.push(bid);
    byAuction.set(bid.auctionId, existing);
  }

  const entries: MyBidEntry[] = [];

  for (const [auctionId, bids] of byAuction) {
    const auction = findAuction(auctionId);
    if (!auction) continue;

    const highest = bids.reduce((best, bid) =>
      bid.amount > best.amount ? bid : best,
    );

    entries.push({
      auction: toAuctionSummary(auction),
      yourBid: highest.amount,
      yourLastBidAt: highest.createdAt,
      standing: standingFor(auctionId, auction.status),
    });
  }

  return entries.sort(
    (a, b) =>
      new Date(b.yourLastBidAt).getTime() - new Date(a.yourLastBidAt).getTime(),
  );
}

export interface MyWinEntry {
  auction: AuctionSummary;
  winningBid: number;
  payment: Payment | null;
  order: Order | null;
}

/** `GET /api/users/me/wins` */
export async function listMyWins(): Promise<MyWinEntry[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.auctions
    .filter((auction) => auction.winnerId === CURRENT_USER_ID)
    .map((auction) => ({
      auction: toAuctionSummary(auction),
      winningBid: auction.currentPrice,
      payment:
        db.payments.find((item) => item.auctionId === auction.id) ?? null,
      order: db.orders.find((item) => item.auctionId === auction.id) ?? null,
    }))
    .sort(
      (a, b) =>
        new Date(b.auction.endTime).getTime() -
        new Date(a.auction.endTime).getTime(),
    );
}

export interface AccountStats {
  activeBids: number;
  won: number;
  watching: number;
  totalSpent: number;
}

export async function getAccountStats(): Promise<AccountStats> {
  await delay();
  reconcileAuctionStatuses();

  const bids = await listMyBids();
  const wins = await listMyWins();

  return {
    activeBids: bids.filter(
      (entry) => entry.standing === "WINNING" || entry.standing === "OUTBID",
    ).length,
    won: wins.length,
    watching: db.watchedAuctionIds.size,
    totalSpent: db.payments
      .filter((payment) => payment.status === "SUCCESS")
      .reduce((total, payment) => total + payment.amount, 0),
  };
}

/** `GET /api/users/me` */
export async function getProfile(): Promise<User | null> {
  await delay();
  return db.users.find((user) => user.id === CURRENT_USER_ID) ?? null;
}

/** `PUT /api/users/me` */
export async function updateProfile(input: {
  fullName: string;
  displayName: string;
}): Promise<User | null> {
  await delay(500);

  const user = db.users.find((item) => item.id === CURRENT_USER_ID);
  if (!user) return null;

  user.fullName = input.fullName.trim();
  user.displayName = input.displayName.trim();
  return { ...user };
}
