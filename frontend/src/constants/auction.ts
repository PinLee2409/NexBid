import type { AuctionSort, AuctionStatus, ProductCondition } from "@/types";

/**
 * Auction tuning values. Business rules read these instead of sprinkling magic
 * numbers through components.
 *
 * Human-readable labels for every enum live in `messages/*.json` under the
 * `enums` namespace — never here — so they follow the reader's locale.
 */
export const AUCTION_CONFIG = {
  /** An auction is flagged "ending soon" below this remaining time. */
  endingSoonMs: 60 * 60 * 1000,
  /** Below this, the countdown switches to its urgent treatment. */
  urgentMs: 10 * 60 * 1000,
  /** Below this, the countdown shows a critical, second-by-second state. */
  criticalMs: 60 * 1000,
  /** Default page size for the browse grid. */
  pageSize: 12,
  /** Bid input quick-add multipliers, expressed in increments. */
  quickIncrements: [1, 2, 5],
  /** Defaults offered when a seller creates an auction (spec §7.4). */
  defaultAntiSnipingWindowSeconds: 30,
  defaultExtensionSeconds: 120,
} as const;

export const PRODUCT_CONDITIONS: ProductCondition[] = [
  "NEW",
  "LIKE_NEW",
  "GOOD",
  "FAIR",
  "USED",
];

export const AUCTION_SORT_OPTIONS: AuctionSort[] = [
  "ENDING_SOON",
  "NEWEST",
  "MOST_BIDS",
  "PRICE_ASC",
  "PRICE_DESC",
];

/** Statuses a shopper can filter by on the public browse page. */
export const BROWSABLE_STATUSES: AuctionStatus[] = [
  "ACTIVE",
  "SCHEDULED",
  "ENDED",
];

/** Price filter bounds for the browse sidebar. */
export const PRICE_FILTER = {
  min: 0,
  max: 20_000,
  step: 100,
} as const;
