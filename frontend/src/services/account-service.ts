"use client";

import type { AuctionSummary, Order, Payment, User } from "@/types";

import type { ApiAuctionSummary, ApiMyBid, ApiOrder, ApiPayment, ApiUser } from "./api/dto";
import { api } from "./api/http";
import { toOrder, toPayment, toSummary } from "./api/mappers";
import { applyAccount } from "./session-service";
import { listWatchlist } from "./watchlist-service";

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

/** `GET /api/users/me/bids` */
export async function listMyBids(): Promise<MyBidEntry[]> {
  const rows = await api<ApiMyBid[]>("/api/users/me/bids");
  return rows.map((row) => ({
    auction: toSummary(row.auction),
    yourBid: Number(row.yourBid),
    yourLastBidAt: row.yourLastBidAt,
    standing: row.standing,
  }));
}

export interface MyWinEntry {
  auction: AuctionSummary;
  winningBid: number;
  payment: Payment | null;
  order: Order | null;
}

/** `GET /api/users/me/wins`, joined with the payment and order of each win. */
export async function listMyWins(): Promise<MyWinEntry[]> {
  const [wins, payments, orders] = await Promise.all([
    api<ApiAuctionSummary[]>("/api/users/me/wins"),
    api<ApiPayment[]>("/api/users/me/payments"),
    api<ApiOrder[]>("/api/users/me/orders"),
  ]);

  return wins.map((win) => {
    const auction = toSummary(win);
    const payment = payments.find((item) => item.payment.auctionId === auction.id);
    const order = orders.find((item) => item.order.auctionId === auction.id);
    return {
      auction,
      winningBid: auction.currentPrice,
      payment: payment ? toPayment(payment.payment) : null,
      order: order ? toOrder(order.order) : null,
    };
  });
}

export interface AccountStats {
  activeBids: number;
  won: number;
  watching: number;
  totalSpent: number;
}

export async function getAccountStats(): Promise<AccountStats> {
  const [bids, wins, watching, payments] = await Promise.all([
    listMyBids(),
    api<ApiAuctionSummary[]>("/api/users/me/wins"),
    listWatchlist(),
    api<ApiPayment[]>("/api/users/me/payments"),
  ]);

  return {
    activeBids: bids.filter((entry) => entry.standing === "WINNING" || entry.standing === "OUTBID").length,
    won: wins.length,
    watching: watching.length,
    totalSpent: payments
      .filter((entry) => entry.payment.status === "SUCCESS")
      .reduce((total, entry) => total + Number(entry.payment.amount), 0),
  };
}

/**
 * `PUT /api/users/me` — only the name can change. The public handle is
 * derived from it by the server, so it follows on its own.
 */
export async function updateProfile(input: { fullName: string }): Promise<User> {
  const me = await api<ApiUser>("/api/users/me", {
    method: "PUT",
    body: { fullName: input.fullName.trim() },
  });
  return applyAccount(me);
}
