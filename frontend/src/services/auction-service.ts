import { AUCTION_CONFIG } from "@/constants/auction";
import { getMsRemaining, isEndingSoon } from "@/lib/auction-rules";
import type {
  AuctionDetail,
  AuctionQuery,
  AuctionSort,
  AuctionSummary,
  Category,
  Page,
} from "@/types";

import {
  CURRENT_USER_ID,
  bidsForAuction,
  db,
  delay,
  findAuction,
  publicAuctions,
  reconcileAuctionStatuses,
  serverTime,
  toAuctionSummary,
} from "./mock/db";

/**
 * Read APIs for the public marketplace.
 *
 * Each function maps to one REST endpoint from spec §27. Replacing the mock
 * body with `fetch()` should be the only change needed to go live.
 */

export interface AuctionListResult extends Page<AuctionSummary> {
  /** Server clock, used to drive countdowns without trusting the browser. */
  serverTime: string;
}

function sortAuctions(
  auctions: AuctionSummary[],
  sort: AuctionSort,
  now: number,
): AuctionSummary[] {
  const sorted = [...auctions];

  switch (sort) {
    case "ENDING_SOON":
      return sorted.sort((a, b) => {
        // Live lots first, then upcoming, then finished.
        const rank = (auction: AuctionSummary) =>
          auction.status === "ACTIVE" ? 0 : auction.status === "SCHEDULED" ? 1 : 2;
        const rankDiff = rank(a) - rank(b);
        if (rankDiff !== 0) return rankDiff;
        return getMsRemaining(a, now) - getMsRemaining(b, now);
      });
    case "NEWEST":
      return sorted.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      );
    case "MOST_BIDS":
      return sorted.sort((a, b) => b.bidCount - a.bidCount);
    case "PRICE_ASC":
      return sorted.sort((a, b) => a.currentPrice - b.currentPrice);
    case "PRICE_DESC":
      return sorted.sort((a, b) => b.currentPrice - a.currentPrice);
    default:
      return sorted;
  }
}

function matchesQuery(
  auction: AuctionSummary,
  query: AuctionQuery,
  now: number,
): boolean {
  if (query.search) {
    const needle = query.search.trim().toLowerCase();
    const haystack = `${auction.product.name} ${auction.category.name} ${auction.seller.displayName}`.toLowerCase();
    if (!haystack.includes(needle)) return false;
  }

  if (query.categorySlugs?.length) {
    if (!query.categorySlugs.includes(auction.category.slug)) return false;
  }

  if (query.statuses?.length) {
    if (!query.statuses.includes(auction.status)) return false;
  }

  if (query.conditions?.length) {
    if (!query.conditions.includes(auction.product.condition)) return false;
  }

  if (typeof query.minPrice === "number" && auction.currentPrice < query.minPrice) {
    return false;
  }

  if (typeof query.maxPrice === "number" && auction.currentPrice > query.maxPrice) {
    return false;
  }

  if (query.endingSoon && !isEndingSoon(auction, now)) return false;

  return true;
}

/** `GET /api/auctions` */
export async function listAuctions(
  query: AuctionQuery = {},
): Promise<AuctionListResult> {
  await delay();
  reconcileAuctionStatuses();

  const now = Date.now();
  const page = Math.max(1, query.page ?? 1);
  const pageSize = query.pageSize ?? AUCTION_CONFIG.pageSize;

  const all = publicAuctions()
    .map(toAuctionSummary)
    .filter((auction) => matchesQuery(auction, query, now));

  const sorted = sortAuctions(all, query.sort ?? "ENDING_SOON", now);
  const start = (page - 1) * pageSize;

  return {
    items: sorted.slice(start, start + pageSize),
    page,
    pageSize,
    totalItems: sorted.length,
    totalPages: Math.max(1, Math.ceil(sorted.length / pageSize)),
    serverTime: serverTime(),
  };
}

/** `GET /api/auctions/{id}` */
export async function getAuction(auctionId: string): Promise<AuctionDetail | null> {
  await delay();
  reconcileAuctionStatuses();

  const auction = findAuction(auctionId);
  if (!auction) return null;

  const summary = toAuctionSummary(auction);
  const recentBids = bidsForAuction(auctionId);
  const viewerBids = recentBids.filter((bid) => bid.bidderId === CURRENT_USER_ID);
  const autoBid =
    db.autoBids.find(
      (item) =>
        item.auctionId === auctionId && item.userId === CURRENT_USER_ID && item.active,
    ) ?? null;

  return {
    ...summary,
    recentBids,
    viewerState: {
      isSeller: auction.sellerId === CURRENT_USER_ID,
      isHighestBidder: recentBids[0]?.bidderId === CURRENT_USER_ID,
      hasBid: viewerBids.length > 0,
      lastBidAmount: viewerBids[0]?.amount ?? null,
      autoBid,
    },
  };
}

export interface HomeFeed {
  live: AuctionSummary[];
  endingSoon: AuctionSummary[];
  upcoming: AuctionSummary[];
  featured: AuctionSummary | null;
  categories: Category[];
  stats: {
    liveCount: number;
    bidsToday: number;
    registeredBidders: number;
  };
  serverTime: string;
}

/**
 * Aggregated landing-page payload. A real deployment would expose this as a
 * single cached endpoint (spec §20.1) rather than several round trips.
 */
export async function getHomeFeed(): Promise<HomeFeed> {
  await delay();
  reconcileAuctionStatuses();

  const now = Date.now();
  const all = publicAuctions().map(toAuctionSummary);

  const live = sortAuctions(
    all.filter((auction) => auction.status === "ACTIVE"),
    "ENDING_SOON",
    now,
  );

  const endingSoon = live.filter((auction) => isEndingSoon(auction, now));

  const upcoming = all
    .filter((auction) => auction.status === "SCHEDULED")
    .sort(
      (a, b) =>
        new Date(a.startTime).getTime() - new Date(b.startTime).getTime(),
    );

  // The showcase lot: the most contested auction still comfortably running.
  const featured =
    [...live].sort((a, b) => b.bidCount - a.bidCount)[0] ?? live[0] ?? null;

  return {
    live: live.slice(0, 8),
    endingSoon: endingSoon.slice(0, 4),
    upcoming: upcoming.slice(0, 4),
    featured,
    categories: db.categories,
    stats: {
      liveCount: live.length,
      bidsToday: all.reduce((total, auction) => total + auction.bidCount, 0),
      registeredBidders: 12_480,
    },
    serverTime: serverTime(),
  };
}

/** `GET /api/categories` */
export async function listCategories(): Promise<Category[]> {
  await delay();
  return db.categories;
}

export async function getCategoryBySlug(slug: string): Promise<Category | null> {
  await delay();
  return db.categories.find((category) => category.slug === slug) ?? null;
}
