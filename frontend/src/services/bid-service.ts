"use client";

import {
  applyExtension,
  getMinimumNextBid,
  validateAutoBid,
  validateBid,
  willTriggerExtension,
  type BidRejection,
} from "@/lib/auction-rules";
import type { AutoBid, Bid, PlaceBidResult } from "@/types";

import {
  CURRENT_USER_ID,
  bidsForAuction,
  db,
  delay,
  findAuction,
  findUser,
  nextId,
  reconcileAuctionStatuses,
} from "./mock/db";
import { publish } from "./realtime-service";

/**
 * Bidding API (spec §7.8, §14).
 *
 * The client-side validation here mirrors the server's so the UI can explain a
 * refusal instantly, but the write path still re-reads current state before
 * committing — the same re-check the backend performs under a row lock.
 */

export type PlaceBidResponse =
  | { ok: true; result: PlaceBidResult }
  | ({ ok: false } & Omit<BidRejection, "ok">);

/** `POST /api/auctions/{id}/bids` */
export async function placeBid(
  auctionId: string,
  amount: number,
): Promise<PlaceBidResponse> {
  // Network latency, so the button's pending state is real.
  await delay(650);
  reconcileAuctionStatuses();

  const auction = findAuction(auctionId);
  if (!auction) {
    return { ok: false, code: "AUCTION_NOT_ACTIVE" };
  }

  const user = findUser(CURRENT_USER_ID) ?? null;
  const now = Date.now();

  // Re-validate against the *current* price: someone may have bid while this
  // request was in flight. This is the client-side twin of the server's
  // re-check inside the transaction.
  const validation = validateBid({
    auction: { ...auction, sellerId: auction.sellerId },
    user,
    amount,
    now,
  });

  if (!validation.ok) {
    return { ok: false, code: validation.code, minimumAmount: validation.minimumAmount };
  }

  const bid: Bid = {
    id: nextId("bid"),
    auctionId,
    bidderId: CURRENT_USER_ID,
    bidderDisplayName: user?.displayName ?? "you***",
    amount,
    automatic: false,
    createdAt: new Date().toISOString(),
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

  publish({
    type: "BID_PLACED",
    auctionId,
    currentPrice: amount,
    totalBids: auction.bidCount,
    bid,
    serverTime: bid.createdAt,
  });

  if (extends_) {
    publish({
      type: "AUCTION_EXTENDED",
      auctionId,
      endTime: auction.endTime,
      extensionSeconds: auction.antiSniping.extensionSeconds,
      serverTime: new Date().toISOString(),
    });
  }

  return { ok: true, result: { bid, auction: { ...auction } } };
}

/** `GET /api/auctions/{id}/bids` */
export async function listBids(auctionId: string): Promise<Bid[]> {
  await delay();
  return bidsForAuction(auctionId);
}

/** `POST|PUT /api/auctions/{id}/auto-bid` */
export async function saveAutoBid(
  auctionId: string,
  maxAmount: number,
): Promise<{ ok: true; autoBid: AutoBid } | ({ ok: false } & Omit<BidRejection, "ok">)> {
  await delay(450);

  const auction = findAuction(auctionId);
  if (!auction) return { ok: false, code: "AUCTION_NOT_ACTIVE" };

  const validation = validateAutoBid(auction, maxAmount);
  if (!validation.ok) {
    return { ok: false, code: validation.code, minimumAmount: validation.minimumAmount };
  }

  const existing = db.autoBids.find(
    (item) => item.auctionId === auctionId && item.userId === CURRENT_USER_ID,
  );
  const timestamp = new Date().toISOString();

  if (existing) {
    existing.maxAmount = maxAmount;
    existing.active = true;
    existing.updatedAt = timestamp;
    return { ok: true, autoBid: { ...existing } };
  }

  const autoBid: AutoBid = {
    id: nextId("autobid"),
    auctionId,
    userId: CURRENT_USER_ID,
    maxAmount,
    active: true,
    createdAt: timestamp,
    updatedAt: timestamp,
  };

  db.autoBids.push(autoBid);
  return { ok: true, autoBid };
}

/** `DELETE /api/auctions/{id}/auto-bid` */
export async function removeAutoBid(auctionId: string): Promise<void> {
  await delay(300);
  db.autoBids = db.autoBids.filter(
    (item) => !(item.auctionId === auctionId && item.userId === CURRENT_USER_ID),
  );
}

/** Convenience for the bid panel's suggested amounts. */
export function minimumNextBidFor(auctionId: string): number | null {
  const auction = findAuction(auctionId);
  return auction ? getMinimumNextBid(auction) : null;
}
