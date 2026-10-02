"use client";

import type { AuctionSummary, Order, Payment, SecondChanceOffer, SellerApplication, User } from "@/types";

import type {
  ApiAuctionSummary,
  ApiMyBid,
  ApiOffer,
  ApiOfferDetails,
  ApiOrder,
  ApiPayment,
  ApiUser,
} from "./api/dto";
import { api } from "./api/http";
import { toOffer, toOrder, toPayment, toSummary } from "./api/mappers";
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
      // EN: A second-chance buyer pays their own bid, not the hammer price. / VI: Người mua cơ hội thứ hai trả giá của chính họ, không phải giá chốt.
      winningBid: payment ? Number(payment.payment.amount) : auction.currentPrice,
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

/**
 * EN: `GET /api/users/me/seller-application` — the latest request to become a seller, or null if none was sent.
 * VI: `GET /api/users/me/seller-application` — yêu cầu trở thành người bán gần nhất, hoặc null nếu chưa gửi.
 */
export async function getSellerApplication(): Promise<SellerApplication | null> {
  return (await api<SellerApplication | undefined>("/api/users/me/seller-application")) ?? null;
}

/** EN: `POST /api/users/me/seller-application`. / VI: `POST /api/users/me/seller-application`. */
export async function applyToSell(note: string): Promise<SellerApplication> {
  return api<SellerApplication>("/api/users/me/seller-application", {
    method: "POST",
    body: { note: note.trim() },
  });
}

export interface OfferEntry {
  offer: SecondChanceOffer;
  auction: AuctionSummary | null;
}

/**
 * EN: `GET /api/users/me/offers` — second-chance offers made to the reader (spec §17), newest first.
 * VI: `GET /api/users/me/offers` — các đề nghị cơ hội thứ hai gửi tới người đọc (spec §17), mới nhất trước.
 */
export async function listMyOffers(): Promise<OfferEntry[]> {
  const rows = await api<ApiOffer[]>("/api/users/me/offers");
  return rows.map((row) => ({ offer: toOffer(row.offer), auction: row.auction ? toSummary(row.auction) : null }));
}

/** EN: The lot becomes the reader's and its payment opens. / VI: Lô thành của người đọc và khoản thanh toán được mở. */
export async function acceptOffer(offerId: string): Promise<SecondChanceOffer> {
  return toOffer(await api<ApiOfferDetails>(`/api/users/me/offers/${offerId}/accept`, { method: "POST" }));
}

export async function declineOffer(offerId: string): Promise<SecondChanceOffer> {
  return toOffer(await api<ApiOfferDetails>(`/api/users/me/offers/${offerId}/decline`, { method: "POST" }));
}
