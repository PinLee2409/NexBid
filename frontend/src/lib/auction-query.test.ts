import { describe, expect, it } from "vitest";

import type { AuctionQuery } from "@/types";

import {
  buildAuctionHref,
  countActiveFilters,
  hasActiveFilters,
  parseAuctionQuery,
} from "./auction-query";

describe("reading the browse filters from the URL", () => {
  it("falls back to the defaults for an empty query", () => {
    expect(parseAuctionQuery({})).toMatchObject({
      search: undefined,
      categorySlugs: [],
      statuses: [],
      conditions: [],
      endingSoon: false,
      sort: "ENDING_SOON",
      page: 1,
    });
  });

  it("reads lists both as repeated keys and as commas", () => {
    expect(parseAuctionQuery({ category: ["watches", "cameras,sneakers"] }).categorySlugs).toEqual([
      "watches",
      "cameras",
      "sneakers",
    ]);
  });

  it("drops values a shared link cannot mean", () => {
    const query = parseAuctionQuery({ status: "ACTIVE,DRAFT,NOPE", condition: "NEW,BROKEN", sort: "CHEAPEST_FIRST!" });
    expect(query.statuses).toEqual(["ACTIVE"]);
    expect(query.conditions).toEqual(["NEW"]);
    expect(query.sort).toBe("ENDING_SOON");
  });

  it.each([
    ["abc", 1],
    ["0", 1],
    ["-2", 1],
    ["2.7", 2],
    ["3", 3],
  ])("turns page %j into page %i", (raw, page) => {
    expect(parseAuctionQuery({ page: raw }).page).toBe(page);
  });

  it("trims the search and ignores a blank one", () => {
    expect(parseAuctionQuery({ search: "  leica " }).search).toBe("leica");
    expect(parseAuctionQuery({ search: "   " }).search).toBeUndefined();
  });

  it("keeps only numeric prices", () => {
    expect(parseAuctionQuery({ minPrice: "500000", maxPrice: "lots" })).toMatchObject({
      minPrice: 500_000,
      maxPrice: undefined,
    });
  });
});

describe("writing the filters back into a link", () => {
  it("leaves defaults out, so the plain catalogue is just /auctions", () => {
    expect(buildAuctionHref(parseAuctionQuery({}))).toBe("/auctions");
  });

  it("round-trips a fully narrowed view", () => {
    const query: AuctionQuery = {
      search: "omega",
      categorySlugs: ["watches"],
      statuses: ["ACTIVE", "SCHEDULED"],
      conditions: ["LIKE_NEW"],
      minPrice: 1_000_000,
      maxPrice: 90_000_000,
      endingSoon: true,
      sort: "NEWEST",
      page: 2,
      pageSize: parseAuctionQuery({}).pageSize,
    };
    const href = buildAuctionHref(query);
    const params = Object.fromEntries(new URL(href, "http://nexbid.test").searchParams);

    expect(parseAuctionQuery(params)).toEqual(query);
  });
});

describe("counting what narrows the catalogue", () => {
  it("counts a price range once, and a search as narrowing but not as a filter chip", () => {
    const query = parseAuctionQuery({ search: "leica", category: "cameras", minPrice: "1", maxPrice: "2" });
    expect(hasActiveFilters(query)).toBe(true);
    expect(countActiveFilters(query)).toBe(2);
    expect(hasActiveFilters(parseAuctionQuery({}))).toBe(false);
  });
});
