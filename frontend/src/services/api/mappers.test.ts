import { describe, expect, it } from "vitest";

import type { NotificationType } from "@/types";

import type { ApiAuction, ApiAuctionSummary, ApiBid, ApiNotification } from "./dto";
import { maskName, toBid, toNotification, toOrder, toPayment, toSummary, toUser } from "./mappers";

/** EN: Spring sends BigDecimal amounts; JSON may carry them as strings. / VI: Spring gửi số tiền BigDecimal; JSON có thể mang chúng dưới dạng chuỗi. */
const decimal = (value: string) => value as unknown as number;

describe("public handles", () => {
  it.each([
    ["Alex Turner", "ale***"],
    ["  Pin Le ", "pin***"],
    ["Al", "al***"],
    ["", "***"],
  ])("mask %j as %j, the way the server does", (name, handle) => {
    expect(maskName(name)).toBe(handle);
  });
});

describe("accounts from the API", () => {
  it("carry the masked handle, and a join date even when the answer has none", () => {
    const user = toUser({ id: "u1", fullName: "Sara Novak", email: "sara@nexbid.test", roles: ["BUYER"], status: "ACTIVE" });
    expect(user).toMatchObject({ displayName: "sar***", createdAt: new Date(0).toISOString() });
  });
});

describe("bids from the API", () => {
  const bid: ApiBid = {
    id: "b1",
    auctionId: "a1",
    bidderMask: "ale***",
    mine: false,
    amount: 1_600_000,
    createdAt: "2026-09-27T03:21:39Z",
  };

  it("never name another bidder, only their mask", () => {
    expect(toBid(bid, "viewer")).toMatchObject({ bidderId: "bidder:ale***", bidderDisplayName: "ale***" });
  });

  it("carry the viewer's id on the viewer's own bids, so the feed can say You", () => {
    expect(toBid({ ...bid, mine: true }, "viewer").bidderId).toBe("viewer");
    expect(toBid({ ...bid, mine: true }, null).bidderId).toBe("bidder:ale***");
  });

  it("turn a decimal amount from JSON into a number", () => {
    expect(toBid({ ...bid, amount: decimal("1600000.00") }, null).amount).toBe(1_600_000);
  });
});

describe("catalogue cards from the API", () => {
  const auction: ApiAuction = {
    id: "a1",
    lotNumber: null,
    productId: "p1",
    sellerId: "s1",
    startingPrice: decimal("1000000.00"),
    currentPrice: decimal("1500000.00"),
    minimumIncrement: decimal("100000.00"),
    startTime: "2026-09-27T00:00:00Z",
    endTime: "2026-09-28T00:00:00Z",
    status: "ACTIVE",
    antiSniping: { enabled: true, windowSeconds: 30, extensionSeconds: 120 },
    extensionCount: 0,
    bidCount: 3,
    winnerId: null,
    rejectionReason: null,
    createdAt: "2026-09-26T00:00:00Z",
    updatedAt: "2026-09-27T01:00:00Z",
  };
  const card: ApiAuctionSummary = {
    auction,
    product: { id: "p1", name: "Leica Q3", coverImageUrl: "/media/p1.jpg", condition: null },
    category: null,
    seller: { id: "s1", displayName: "lum***" },
  };

  it("turn prices into numbers and give a lot without a number lot 0", () => {
    expect(toSummary(card)).toMatchObject({ startingPrice: 1_000_000, currentPrice: 1_500_000, minimumIncrement: 100_000, lotNumber: 0 });
  });

  it("carry only the cover photo, named after the product", () => {
    expect(toSummary(card).product.images).toEqual([{ id: "p1-cover", url: "/media/p1.jpg", alt: "Leica Q3", sortOrder: 0 }]);
    expect(toSummary({ ...card, product: { ...card.product, coverImageUrl: null } }).product.images).toEqual([]);
  });

  it("fill in blanks rather than invent values for what the card lacks", () => {
    const summary = toSummary(card, true);
    expect(summary.category).toEqual({ id: "", slug: "", name: "", description: "", imageUrl: "" });
    expect(summary.product).toMatchObject({ condition: "GOOD", description: "", createdAt: auction.createdAt });
    expect(summary.watched).toBe(true);
    expect(summary.rejectionReason).toBeUndefined();
  });
});

describe("where a notification leads", () => {
  const notice = (type: NotificationType, auctionId: string | null = "a1"): ApiNotification => ({
    id: "n1",
    type,
    title: "t",
    message: "m",
    auctionId,
    read: false,
    createdAt: "2026-09-27T00:00:00Z",
  });

  it.each([
    ["AUCTION_WON", "/payments"],
    ["PAYMENT_REQUIRED", "/payments"],
    ["PAYMENT_SUCCESS", "/orders"],
    ["PAYMENT_EXPIRED", "/orders"],
    ["OUTBID", "/auctions/a1"],
    ["AUCTION_EXTENDED", "/auctions/a1"],
    ["SELLER_APPROVED", "/seller/products/create"],
    ["SELLER_REJECTED", "/become-seller"],
    ["SECOND_CHANCE_OFFER", "/my-wins"],
    ["SECOND_CHANCE_ACCEPTED", "/seller/orders"],
    ["SECOND_CHANCE_DECLINED", "/seller/orders"],
  ] as const)("%s opens %s", (type, href) => {
    expect(toNotification(notice(type), "u1").href).toBe(href);
  });

  it("goes nowhere for a cancelled lot, or a notice about no lot", () => {
    expect(toNotification(notice("AUCTION_CANCELLED"), "u1").href).toBeUndefined();
    expect(toNotification(notice("OUTBID", null), "u1").href).toBeUndefined();
  });

  it("belongs to the reader and keeps its read state", () => {
    expect(toNotification({ ...notice("OUTBID"), read: true }, "u1")).toMatchObject({ userId: "u1", isRead: true });
  });
});

describe("payments and orders from the API", () => {
  it("turn their amounts into numbers", () => {
    const payment = toPayment({
      id: "pay1", auctionId: "a1", userId: "u1", amount: decimal("6600000.00"), status: "PENDING",
      expiredAt: "2026-09-28T00:00:00Z", createdAt: "2026-09-27T00:00:00Z", updatedAt: "2026-09-27T00:00:00Z",
    });
    expect(payment.amount).toBe(6_600_000);
    const order = toOrder({
      id: "o1", auctionId: "a1", buyerId: "u1", sellerId: "s1", paymentId: "pay1", amount: decimal("15500000.00"),
      status: "PAID", createdAt: "2026-09-27T00:00:00Z", updatedAt: "2026-09-27T00:00:00Z",
    });
    expect(order.amount).toBe(15_500_000);
  });
});
