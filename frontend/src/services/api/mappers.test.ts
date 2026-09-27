import { describe, expect, it } from "vitest";

import type { ApiBid } from "./dto";
import { maskName, toBid } from "./mappers";

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
    expect(toBid({ ...bid, amount: "1600000.00" as unknown as number }, null).amount).toBe(1_600_000);
  });
});
