import { buildAuctionHref } from "@/lib/auction-query";
import type { AuctionQuery, IsoDateString, ProductCondition } from "@/types";

import { api } from "./api/http";

/**
 * EN: Lot alerts: saved catalogue searches and followed sellers. All of it needs a session.
 * VI: Báo lô mới: tìm kiếm đã lưu và người bán đang theo dõi. Tất cả đều cần đăng nhập.
 */
export interface SavedSearch {
  id: string;
  q: string | null;
  category: string[];
  condition: ProductCondition[];
  minPrice: number | null;
  maxPrice: number | null;
  createdAt: IsoDateString;
}

export interface FollowedSeller {
  sellerId: string;
  /** EN: The public handle, "lum***". / VI: Tên hiển thị công khai, "lum***". */
  displayName: string;
  since: IsoDateString;
}

interface ApiSavedSearch extends Omit<SavedSearch, "minPrice" | "maxPrice"> {
  minPrice: number | string | null;
  maxPrice: number | string | null;
}

/** EN: Money may arrive as a string from BigDecimal. / VI: Tiền có thể tới dạng chuỗi từ BigDecimal. */
function toSearch(search: ApiSavedSearch): SavedSearch {
  return {
    ...search,
    q: search.q ?? null,
    minPrice: search.minPrice === null || search.minPrice === undefined ? null : Number(search.minPrice),
    maxPrice: search.maxPrice === null || search.maxPrice === undefined ? null : Number(search.maxPrice),
  };
}

/**
 * EN: Whether the catalogue is narrowed by anything a saved search keeps (status, sort and "ending soon" are not kept).
 * VI: Danh mục có đang bị thu hẹp bởi thứ mà tìm kiếm đã lưu giữ lại không (trạng thái, sắp xếp và "sắp đóng" thì không).
 */
export function canSaveSearch(query: AuctionQuery): boolean {
  return Boolean(
    query.search ||
      query.categorySlugs?.length ||
      query.conditions?.length ||
      typeof query.minPrice === "number" ||
      typeof query.maxPrice === "number",
  );
}

/** EN: The catalogue as the saved search sees it. / VI: Danh mục theo đúng tìm kiếm đã lưu. */
export function savedSearchHref(search: SavedSearch): string {
  return buildAuctionHref({
    search: search.q ?? undefined,
    categorySlugs: search.category,
    conditions: search.condition,
    minPrice: search.minPrice ?? undefined,
    maxPrice: search.maxPrice ?? undefined,
    sort: "NEWEST",
  });
}

/** `GET /api/alerts/searches` — newest first. */
export async function listSavedSearches(): Promise<SavedSearch[]> {
  return (await api<ApiSavedSearch[]>("/api/alerts/searches")).map(toSearch);
}

/** `POST /api/alerts/searches` — saving the same filters again returns the first one. */
export async function saveSearch(query: AuctionQuery): Promise<SavedSearch> {
  return toSearch(
    await api<ApiSavedSearch>("/api/alerts/searches", {
      method: "POST",
      body: {
        q: query.search ?? null,
        category: query.categorySlugs ?? [],
        condition: query.conditions ?? [],
        minPrice: query.minPrice ?? null,
        maxPrice: query.maxPrice ?? null,
      },
    }),
  );
}

/** `DELETE /api/alerts/searches/{id}` */
export async function deleteSavedSearch(id: string): Promise<void> {
  await api(`/api/alerts/searches/${id}`, { method: "DELETE" });
}

/** `GET /api/alerts/sellers` — newest first. */
export async function listFollowedSellers(): Promise<FollowedSeller[]> {
  return api<FollowedSeller[]>("/api/alerts/sellers");
}

/** `GET /api/alerts/sellers/{id}` */
export async function isFollowing(sellerId: string): Promise<boolean> {
  return (await api<{ following: boolean }>(`/api/alerts/sellers/${sellerId}`)).following;
}

/** `PUT` follows, `DELETE` unfollows; returns whether the caller now follows the seller. */
export async function setFollowing(sellerId: string, following: boolean): Promise<boolean> {
  return (
    await api<{ following: boolean }>(`/api/alerts/sellers/${sellerId}`, { method: following ? "PUT" : "DELETE" })
  ).following;
}
