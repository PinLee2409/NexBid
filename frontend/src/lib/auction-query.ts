import { AUCTION_CONFIG, AUCTION_SORT_OPTIONS, BROWSABLE_STATUSES, PRODUCT_CONDITIONS } from "@/constants/auction";
import type {
  AuctionQuery,
  AuctionSort,
  AuctionStatus,
  ProductCondition,
} from "@/types";

/**
 * The browse page keeps its whole filter state in the URL, so a filtered view
 * is shareable, bookmarkable and survives a refresh. This module is the single
 * translation layer between query strings and `AuctionQuery`.
 */

export type RawSearchParams = Record<string, string | string[] | undefined>;

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** Reads `?a=1&a=2` and `?a=1,2` alike. */
function listValue(value: string | string[] | undefined): string[] {
  if (value === undefined) return [];
  const raw = Array.isArray(value) ? value : [value];
  return raw.flatMap((entry) => entry.split(",")).filter(Boolean);
}

function numberValue(value: string | string[] | undefined): number | undefined {
  const raw = firstValue(value);
  if (raw === undefined || raw === "") return undefined;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function parseAuctionQuery(params: RawSearchParams): AuctionQuery {
  const statuses = listValue(params.status).filter((value): value is AuctionStatus =>
    BROWSABLE_STATUSES.includes(value as AuctionStatus),
  );

  const conditions = listValue(params.condition).filter(
    (value): value is ProductCondition =>
      PRODUCT_CONDITIONS.includes(value as ProductCondition),
  );

  const sortRaw = firstValue(params.sort);
  const sort = AUCTION_SORT_OPTIONS.includes(sortRaw as AuctionSort)
    ? (sortRaw as AuctionSort)
    : "ENDING_SOON";

  const page = numberValue(params.page);

  return {
    search: firstValue(params.search)?.trim() || undefined,
    categorySlugs: listValue(params.category),
    statuses,
    conditions,
    minPrice: numberValue(params.minPrice),
    maxPrice: numberValue(params.maxPrice),
    endingSoon: firstValue(params.endingSoon) === "1",
    sort,
    page: page && page > 0 ? Math.floor(page) : 1,
    pageSize: AUCTION_CONFIG.pageSize,
  };
}

/**
 * Serialises a query back to a query string. Defaults are omitted so shared
 * links stay short and readable.
 */
export function buildAuctionSearchParams(query: AuctionQuery): URLSearchParams {
  const params = new URLSearchParams();

  if (query.search) params.set("search", query.search);
  if (query.categorySlugs?.length) {
    params.set("category", query.categorySlugs.join(","));
  }
  if (query.statuses?.length) params.set("status", query.statuses.join(","));
  if (query.conditions?.length) {
    params.set("condition", query.conditions.join(","));
  }
  if (typeof query.minPrice === "number") {
    params.set("minPrice", String(query.minPrice));
  }
  if (typeof query.maxPrice === "number") {
    params.set("maxPrice", String(query.maxPrice));
  }
  if (query.endingSoon) params.set("endingSoon", "1");
  if (query.sort && query.sort !== "ENDING_SOON") params.set("sort", query.sort);
  if (query.page && query.page > 1) params.set("page", String(query.page));

  return params;
}

export function buildAuctionHref(query: AuctionQuery): string {
  const params = buildAuctionSearchParams(query);
  const queryString = params.toString();
  return queryString ? `/auctions?${queryString}` : "/auctions";
}

/** True when the shopper has narrowed the catalogue in any way. */
export function hasActiveFilters(query: AuctionQuery): boolean {
  return Boolean(
    query.search ||
      query.categorySlugs?.length ||
      query.statuses?.length ||
      query.conditions?.length ||
      typeof query.minPrice === "number" ||
      typeof query.maxPrice === "number" ||
      query.endingSoon,
  );
}

export function countActiveFilters(query: AuctionQuery): number {
  return (
    (query.categorySlugs?.length ?? 0) +
    (query.statuses?.length ?? 0) +
    (query.conditions?.length ?? 0) +
    (typeof query.minPrice === "number" || typeof query.maxPrice === "number" ? 1 : 0) +
    (query.endingSoon ? 1 : 0)
  );
}
