import { describe, expect, it } from "vitest";

import {
  applyExtension,
  getMinimumNextBid,
  getQuickBidAmounts,
  getUrgency,
  isAuctionEditable,
  isEndingSoon,
  validateAutoBid,
  validateBid,
  willTriggerExtension,
  type BidValidationContext,
} from "./auction-rules";

const NOW = Date.parse("2026-09-27T10:00:00Z");
const MINUTE = 60_000;
const at = (ms: number) => new Date(NOW + ms).toISOString();

const opened = { bidCount: 0, startingPrice: 1_000_000, currentPrice: 1_000_000, minimumIncrement: 100_000 };
const bidOn = { bidCount: 3, startingPrice: 1_000_000, currentPrice: 1_500_000, minimumIncrement: 100_000 };

describe("the minimum next bid (spec §8)", () => {
  it("is the starting price while nobody has bid", () => {
    expect(getMinimumNextBid(opened)).toBe(1_000_000);
  });

  it("is the current price plus one increment once someone has", () => {
    expect(getMinimumNextBid(bidOn)).toBe(1_600_000);
  });

  it("offers quick bids of 1x, 2x and 5x the increment, the first being the minimum", () => {
    expect(getQuickBidAmounts(bidOn)).toEqual([1_600_000, 1_700_000, 2_000_000]);
    expect(getQuickBidAmounts(opened)).toEqual([1_000_000, 1_100_000, 1_400_000]);
  });
});

describe("the countdown's urgency", () => {
  it.each([
    [0, "none"],
    [30_000, "critical"],
    [MINUTE, "critical"],
    [5 * MINUTE, "urgent"],
    [10 * MINUTE, "urgent"],
    [30 * MINUTE, "soon"],
    [60 * MINUTE, "soon"],
    [2 * 60 * MINUTE, "none"],
  ] as const)("%i ms left is %s", (remaining, level) => {
    expect(getUrgency(remaining)).toBe(level);
  });

  it("calls a live lot in its last hour ending soon, and nothing else", () => {
    expect(isEndingSoon({ status: "ACTIVE", endTime: at(30 * MINUTE) }, NOW)).toBe(true);
    expect(isEndingSoon({ status: "ACTIVE", endTime: at(2 * 60 * MINUTE) }, NOW)).toBe(false);
    expect(isEndingSoon({ status: "SCHEDULED", endTime: at(30 * MINUTE) }, NOW)).toBe(false);
    expect(isEndingSoon({ status: "ACTIVE", endTime: at(-MINUTE) }, NOW)).toBe(false);
  });
});

describe("anti-sniping (spec §13)", () => {
  const guarded = { enabled: true, windowSeconds: 30, extensionSeconds: 120 };

  it("extends a lot for a bid inside the final window only", () => {
    expect(willTriggerExtension({ endTime: at(20_000), antiSniping: guarded }, NOW)).toBe(true);
    expect(willTriggerExtension({ endTime: at(40_000), antiSniping: guarded }, NOW)).toBe(false);
    expect(willTriggerExtension({ endTime: at(-1_000), antiSniping: guarded }, NOW)).toBe(false);
    expect(willTriggerExtension({ endTime: at(20_000), antiSniping: { ...guarded, enabled: false } }, NOW)).toBe(false);
  });

  it("moves the end time back by the extension", () => {
    expect(applyExtension("2026-09-27T10:00:00.000Z", 120)).toBe("2026-09-27T10:02:00.000Z");
  });
});

describe("bid validation, in the server's order (spec §7.8)", () => {
  const buyer = { id: "buyer", status: "ACTIVE" as const };
  const base: BidValidationContext = {
    auction: { ...bidOn, status: "ACTIVE", endTime: at(10 * MINUTE), sellerId: "seller" },
    user: buyer,
    amount: 1_600_000,
    now: NOW,
  };

  it("accepts the minimum next bid", () => {
    expect(validateBid(base)).toEqual({ ok: true });
  });

  it("accepts a first bid equal to the starting price", () => {
    expect(validateBid({ ...base, auction: { ...base.auction, ...opened }, amount: 1_000_000 })).toEqual({ ok: true });
  });

  it("asks a visitor to sign in before anything else", () => {
    expect(validateBid({ ...base, user: null, amount: null })).toMatchObject({ code: "NOT_AUTHENTICATED" });
  });

  it("stops a blocked account even on a lot that has ended", () => {
    expect(
      validateBid({ ...base, user: { ...buyer, status: "BLOCKED" }, auction: { ...base.auction, status: "ENDED" } }),
    ).toMatchObject({ code: "ACCOUNT_BLOCKED" });
  });

  it("refuses a lot that is not live, then one whose clock has run out", () => {
    expect(validateBid({ ...base, auction: { ...base.auction, status: "SCHEDULED" } })).toMatchObject({
      code: "AUCTION_NOT_ACTIVE",
    });
    expect(validateBid({ ...base, auction: { ...base.auction, endTime: at(-1) } })).toMatchObject({
      code: "AUCTION_ALREADY_ENDED",
    });
  });

  it("does not let sellers bid on their own lot", () => {
    expect(validateBid({ ...base, user: { ...buyer, id: "seller" } })).toMatchObject({ code: "SELLER_CANNOT_BID" });
  });

  it("names the floor when a bid is too low, and flags an empty one", () => {
    expect(validateBid({ ...base, amount: 1_599_999 })).toEqual({
      ok: false,
      code: "BID_TOO_LOW",
      minimumAmount: 1_600_000,
    });
    expect(validateBid({ ...base, amount: null })).toMatchObject({ code: "BID_EMPTY" });
    expect(validateBid({ ...base, amount: Number.NaN })).toMatchObject({ code: "BID_EMPTY" });
  });

  it("holds an auto bid's ceiling to the same floor (spec §14)", () => {
    expect(validateAutoBid(bidOn, 1_600_000)).toEqual({ ok: true });
    expect(validateAutoBid(bidOn, 1_500_000)).toEqual({ ok: false, code: "BID_TOO_LOW", minimumAmount: 1_600_000 });
    expect(validateAutoBid(bidOn, null)).toMatchObject({ code: "BID_EMPTY" });
  });
});

describe("seller editing (spec §7.4)", () => {
  it("allows editing a draft or a rejected auction only", () => {
    expect(isAuctionEditable("DRAFT")).toBe(true);
    expect(isAuctionEditable("REJECTED")).toBe(true);
    expect(isAuctionEditable("PENDING_APPROVAL")).toBe(false);
    expect(isAuctionEditable("ACTIVE")).toBe(false);
  });
});
