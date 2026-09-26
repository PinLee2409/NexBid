"use client";

import type { BidRejection } from "@/lib/auction-rules";
import type { AutoBid, Bid, PlaceBidResult } from "@/types";

import type { ApiAuctionDetail, ApiAutoBid, ApiBid, ApiPage, ApiPlacedBid } from "./api/dto";
import { ApiError, api } from "./api/http";
import { toBid } from "./api/mappers";
import { getCurrentUser } from "./session-service";

/**
 * Bidding API (spec §7.8, §14).
 *
 * The client-side validation in `auction-rules` explains a refusal instantly;
 * the server re-checks everything under a row lock, and its answer is final.
 */

export type PlaceBidResponse =
  | { ok: true; result: PlaceBidResult }
  | ({ ok: false } & Omit<BidRejection, "ok">);

type Rejection = { ok: false } & Omit<BidRejection, "ok">;

/**
 * EN: Turns a refusal into the terminal's codes. "Too low" re-reads the lot, because the floor moved
 *     while the request was in flight.
 * VI: Đổi lời từ chối sang mã của terminal. "Quá thấp" thì đọc lại lô, vì mức sàn đã đổi trong lúc
 *     request đang bay.
 */
async function rejectionFor(auctionId: string, error: unknown): Promise<Rejection> {
  if (!(error instanceof ApiError)) return { ok: false, code: "BID_FAILED" };

  switch (error.code) {
    case "BID_TOO_LOW":
    case "AUTO_BID_INVALID": {
      let minimumAmount: number | undefined;
      try {
        const lot = await api<ApiAuctionDetail>(`/api/auctions/${auctionId}`);
        minimumAmount = Number(lot.minimumNextBid);
      } catch {
        minimumAmount = undefined;
      }
      return { ok: false, code: error.code, minimumAmount };
    }
    case "NOT_AUTHENTICATED":
    case "ACCOUNT_BLOCKED":
    case "AUCTION_NOT_ACTIVE":
    case "AUCTION_ALREADY_ENDED":
    case "SELLER_CANNOT_BID":
    case "BID_CONFLICT":
    case "BID_RATE_LIMITED":
      return { ok: false, code: error.code };
    default:
      return { ok: false, code: "BID_FAILED" };
  }
}

/** `POST /api/auctions/{id}/bids` */
export async function placeBid(auctionId: string, amount: number): Promise<PlaceBidResponse> {
  try {
    const placed = await api<ApiPlacedBid>(`/api/auctions/${auctionId}/bids`, {
      method: "POST",
      body: { amount },
    });
    return {
      ok: true,
      result: {
        bid: toBid(placed.bid, getCurrentUser()?.id ?? null),
        currentPrice: Number(placed.currentPrice),
        bidCount: placed.bidCount,
        minimumNextBid: Number(placed.minimumNextBid),
        endTime: placed.endTime,
        leading: placed.leading,
      },
    };
  } catch (error) {
    return rejectionFor(auctionId, error);
  }
}

/** `GET /api/auctions/{id}/bids` — the caller's own bids are marked when signed in. */
export async function listBids(auctionId: string, size = 20): Promise<Bid[]> {
  const page = await api<ApiPage<ApiBid>>(`/api/auctions/${auctionId}/bids`, { query: { size } });
  const viewerId = getCurrentUser()?.id ?? null;
  return page.items.map((bid) => toBid(bid, viewerId));
}

function toAutoBid(view: ApiAutoBid): AutoBid {
  return {
    id: view.auctionId,
    auctionId: view.auctionId,
    userId: getCurrentUser()?.id ?? "",
    maxAmount: Number(view.maxAmount),
    active: view.active,
    createdAt: view.updatedAt,
    updatedAt: view.updatedAt,
  };
}

/** `GET /api/auctions/{id}/auto-bid` — null when the caller has none. */
export async function getAutoBid(auctionId: string): Promise<AutoBid | null> {
  try {
    const view = await api<ApiAutoBid>(`/api/auctions/${auctionId}/auto-bid`);
    return view.active ? toAutoBid(view) : null;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/** `POST|PUT /api/auctions/{id}/auto-bid` */
export async function saveAutoBid(
  auctionId: string,
  maxAmount: number,
): Promise<{ ok: true; autoBid: AutoBid } | Rejection> {
  const path = `/api/auctions/${auctionId}/auto-bid`;
  try {
    return { ok: true, autoBid: toAutoBid(await api<ApiAutoBid>(path, { method: "POST", body: { maxAmount } })) };
  } catch (error) {
    // EN: One already set: raise or lower its ceiling instead. / VI: Đã có sẵn: nâng hoặc hạ mức trần của nó.
    if (error instanceof ApiError && error.code === "AUTO_BID_EXISTS") {
      try {
        return { ok: true, autoBid: toAutoBid(await api<ApiAutoBid>(path, { method: "PUT", body: { maxAmount } })) };
      } catch (retryError) {
        return rejectionFor(auctionId, retryError);
      }
    }
    return rejectionFor(auctionId, error);
  }
}

/** `DELETE /api/auctions/{id}/auto-bid` */
export async function removeAutoBid(auctionId: string): Promise<void> {
  try {
    await api<ApiAutoBid>(`/api/auctions/${auctionId}/auto-bid`, { method: "DELETE" });
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) throw error;
  }
}
