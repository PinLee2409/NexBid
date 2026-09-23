import { AUCTION_CONFIG } from "@/constants/auction";
import type {
  Auction,
  AuctionStatus,
  AuctionSummary,
  ErrorCode,
  User,
} from "@/types";

/**
 * Auction business rules, kept in one place so no component re-implements
 * pricing or eligibility logic. Mirrors the server rules in spec §8 and §7.8 —
 * the client uses these for *immediate feedback only*; the server stays the
 * authority on price, status and winner (spec §30).
 */

/* -------------------------------------------------------------------------- */
/*                                   Pricing                                  */
/* -------------------------------------------------------------------------- */

/**
 * Spec §8: with no bids the floor is the starting price, otherwise it is the
 * current price plus one full increment.
 */
export function getMinimumNextBid(
  auction: Pick<
    Auction,
    "bidCount" | "startingPrice" | "currentPrice" | "minimumIncrement"
  >,
): number {
  if (auction.bidCount === 0) return auction.startingPrice;
  return auction.currentPrice + auction.minimumIncrement;
}

/** Suggested amounts offered next to the bid input (1x / 2x / 5x increment). */
export function getQuickBidAmounts(
  auction: Pick<
    Auction,
    "bidCount" | "startingPrice" | "currentPrice" | "minimumIncrement"
  >,
): number[] {
  const minimum = getMinimumNextBid(auction);
  return AUCTION_CONFIG.quickIncrements.map(
    (multiplier, index) => minimum + index * auction.minimumIncrement * multiplier,
  );
}

/* -------------------------------------------------------------------------- */
/*                                   Timing                                   */
/* -------------------------------------------------------------------------- */

export function getMsRemaining(
  auction: Pick<Auction, "endTime">,
  now: number,
): number {
  return Math.max(0, new Date(auction.endTime).getTime() - now);
}

export function getMsUntilStart(
  auction: Pick<Auction, "startTime">,
  now: number,
): number {
  return Math.max(0, new Date(auction.startTime).getTime() - now);
}

export type UrgencyLevel = "none" | "soon" | "urgent" | "critical";

/**
 * Drives the countdown's visual treatment. Red is reserved for the last
 * minutes so it keeps its meaning.
 */
export function getUrgency(msRemaining: number): UrgencyLevel {
  if (msRemaining <= 0) return "none";
  if (msRemaining <= AUCTION_CONFIG.criticalMs) return "critical";
  if (msRemaining <= AUCTION_CONFIG.urgentMs) return "urgent";
  if (msRemaining <= AUCTION_CONFIG.endingSoonMs) return "soon";
  return "none";
}

export function isEndingSoon(auction: Pick<Auction, "endTime" | "status">, now: number): boolean {
  if (auction.status !== "ACTIVE") return false;
  const remaining = getMsRemaining(auction, now);
  return remaining > 0 && remaining <= AUCTION_CONFIG.endingSoonMs;
}

/**
 * Spec §13: a bid landing inside the anti-sniping window pushes the end time
 * back. Exposed so the bid panel can warn before the fact and so the mock
 * realtime engine can apply the same rule the backend will.
 */
export function willTriggerExtension(
  auction: Pick<Auction, "endTime" | "antiSniping">,
  now: number,
): boolean {
  if (!auction.antiSniping.enabled) return false;
  const remaining = getMsRemaining(auction, now);
  return remaining > 0 && remaining <= auction.antiSniping.windowSeconds * 1000;
}

export function applyExtension(
  endTime: string,
  extensionSeconds: number,
): string {
  return new Date(new Date(endTime).getTime() + extensionSeconds * 1000).toISOString();
}

/* -------------------------------------------------------------------------- */
/*                                   Status                                   */
/* -------------------------------------------------------------------------- */

export function isLive(auction: Pick<Auction, "status">): boolean {
  return auction.status === "ACTIVE";
}

export function isUpcoming(auction: Pick<Auction, "status">): boolean {
  return auction.status === "SCHEDULED";
}

export function isFinished(auction: Pick<Auction, "status">): boolean {
  return (
    auction.status === "ENDED" ||
    auction.status === "COMPLETED" ||
    auction.status === "CANCELLED"
  );
}

export function isBiddable(auction: Pick<Auction, "status">): boolean {
  return auction.status === "ACTIVE";
}

/** Seller-side rule: auctions are only editable while still a draft (§7.4). */
export function isAuctionEditable(status: AuctionStatus): boolean {
  return status === "DRAFT" || status === "REJECTED";
}

/* -------------------------------------------------------------------------- */
/*                               Bid validation                               */
/* -------------------------------------------------------------------------- */

/**
 * Reasons a bid is refused. These are *codes*, never copy — the UI maps them
 * to a localised message from the `bidErrors` namespace.
 */
export type BidRejectionCode =
  | Extract<
      ErrorCode,
      | "NOT_AUTHENTICATED"
      | "AUCTION_NOT_ACTIVE"
      | "AUCTION_ALREADY_ENDED"
      | "SELLER_CANNOT_BID"
      | "BID_TOO_LOW"
      | "ACCOUNT_BLOCKED"
    >
  | "BID_EMPTY";

export type BidRejection = {
  ok: false;
  code: BidRejectionCode;
  /** Present for `BID_TOO_LOW`, so the message can name the floor. */
  minimumAmount?: number;
};

export type BidValidation = { ok: true } | BidRejection;

export interface BidValidationContext {
  auction: Pick<
    AuctionSummary,
    | "status"
    | "endTime"
    | "sellerId"
    | "bidCount"
    | "startingPrice"
    | "currentPrice"
    | "minimumIncrement"
  >;
  user: Pick<User, "id" | "status"> | null;
  amount: number | null;
  now: number;
}

/**
 * Mirrors the server's validation order (spec §7.8) so the UI can disable or
 * explain the bid action before a request is ever sent.
 */
export function validateBid({
  auction,
  user,
  amount,
  now,
}: BidValidationContext): BidValidation {
  if (!user) {
    return { ok: false, code: "NOT_AUTHENTICATED" };
  }

  if (user.status === "BLOCKED") {
    return { ok: false, code: "ACCOUNT_BLOCKED" };
  }

  if (auction.status !== "ACTIVE") {
    return { ok: false, code: "AUCTION_NOT_ACTIVE" };
  }

  if (getMsRemaining(auction, now) <= 0) {
    return { ok: false, code: "AUCTION_ALREADY_ENDED" };
  }

  if (auction.sellerId === user.id) {
    return { ok: false, code: "SELLER_CANNOT_BID" };
  }

  const minimum = getMinimumNextBid(auction);

  if (amount === null || Number.isNaN(amount)) {
    return { ok: false, code: "BID_EMPTY" };
  }

  if (amount < minimum) {
    return { ok: false, code: "BID_TOO_LOW", minimumAmount: minimum };
  }

  return { ok: true };
}

/* -------------------------------------------------------------------------- */
/*                                  Auto bid                                  */
/* -------------------------------------------------------------------------- */

/** Spec §14: max amount must clear the current minimum next bid. */
export function validateAutoBid(
  auction: Parameters<typeof getMinimumNextBid>[0],
  maxAmount: number | null,
): BidValidation {
  const minimum = getMinimumNextBid(auction);

  if (maxAmount === null || Number.isNaN(maxAmount)) {
    return { ok: false, code: "BID_EMPTY" };
  }

  if (maxAmount < minimum) {
    return { ok: false, code: "BID_TOO_LOW", minimumAmount: minimum };
  }

  return { ok: true };
}
