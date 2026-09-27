import { AUCTION_CONFIG } from "@/constants/auction";
import type {
  AuctionDetail,
  AuctionQuery,
  AuctionSummary,
  Category,
  Page,
} from "@/types";

import type { ApiAuctionDetail, ApiAuctionSummary, ApiBid, ApiCategory, ApiPage } from "./api/dto";
import { ApiError, api } from "./api/http";
import { toAuction, toBid, toCategory, toImage, toSummary } from "./api/mappers";

/**
 * Read APIs for the public marketplace (spec §27). They run on the server for
 * the first render and in the browser afterwards; both are anonymous, so
 * anything about the signed-in reader is fetched separately on the client.
 */

export interface AuctionListResult extends Page<AuctionSummary> {
  /** Server clock, used to drive countdowns without trusting the browser. */
  serverTime: string;
}

/** `GET /api/server-time` */
async function getServerTime(): Promise<string> {
  const { serverTime } = await api<{ serverTime: string }>("/api/server-time");
  return serverTime;
}

async function fetchPage(query: AuctionQuery): Promise<Page<AuctionSummary>> {
  const page = await api<ApiPage<ApiAuctionSummary>>("/api/auctions", {
    query: {
      q: query.search?.trim(),
      category: query.categorySlugs,
      status: query.statuses,
      condition: query.conditions,
      minPrice: query.minPrice,
      maxPrice: query.maxPrice,
      endingSoon: query.endingSoon || undefined,
      sort: query.sort,
      page: query.page,
      size: query.pageSize ?? AUCTION_CONFIG.pageSize,
    },
  });
  return { ...page, items: page.items.map((item) => toSummary(item)) };
}

/** `GET /api/auctions` */
export async function listAuctions(query: AuctionQuery = {}): Promise<AuctionListResult> {
  const [page, serverTime] = await Promise.all([fetchPage(query), getServerTime()]);
  return { ...page, serverTime };
}

/** `GET /api/auctions/{id}`, with the most recent bids. Null when the lot is not public. */
export async function getAuction(auctionId: string): Promise<AuctionDetail | null> {
  let detail: ApiAuctionDetail;
  let bids: ApiPage<ApiBid>;
  try {
    [detail, bids] = await Promise.all([
      api<ApiAuctionDetail>(`/api/auctions/${auctionId}`),
      api<ApiPage<ApiBid>>(`/api/auctions/${auctionId}/bids`, { query: { size: 20 } }),
    ]);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) return null;
    throw error;
  }

  const auction = toAuction(detail.auction);
  const category = toCategory(detail.category);
  return {
    ...auction,
    product: {
      id: detail.product.id,
      sellerId: detail.seller.id,
      categoryId: category.id,
      name: detail.product.name,
      description: detail.product.description,
      condition: detail.product.condition,
      status: "IN_AUCTION",
      images: detail.images.map((image) => toImage(image, detail.product.name)),
      createdAt: auction.createdAt,
      updatedAt: auction.updatedAt,
    },
    category,
    seller: { id: detail.seller.id, displayName: detail.seller.displayName },
    viewerCount: detail.viewerCount ?? undefined,
    watched: false,
    recentBids: bids.items.map((bid) => toBid(bid, null)),
  };
}

/** Lots open right now, for the header's "LIVE 09" counter. */
export async function getLiveCount(): Promise<number> {
  try {
    const page = await api<ApiPage<ApiAuctionSummary>>("/api/auctions", {
      query: { status: ["ACTIVE"], size: 1 },
    });
    return page.totalItems;
  } catch {
    // The counter is decoration; the page must render without it.
    return 0;
  }
}

export interface HomeFeed {
  live: AuctionSummary[];
  endingSoon: AuctionSummary[];
  upcoming: AuctionSummary[];
  featured: AuctionSummary | null;
  /** A second live lot for the editorial spread, with its description and photos. */
  editorial: AuctionSummary | null;
  categories: Category[];
  stats: {
    liveCount: number;
  };
  serverTime: string;
}

/** Landing page: a few small list queries run in parallel. */
export async function getHomeFeed(): Promise<HomeFeed> {
  const [live, endingSoon, upcoming, featured, categories, serverTime] = await Promise.all([
    fetchPage({ statuses: ["ACTIVE"], sort: "ENDING_SOON", pageSize: 8 }),
    fetchPage({ statuses: ["ACTIVE"], endingSoon: true, sort: "ENDING_SOON", pageSize: 4 }),
    fetchPage({ statuses: ["SCHEDULED"], sort: "ENDING_SOON", pageSize: 4 }),
    // The showcase lot: the most contested auction still running.
    fetchPage({ statuses: ["ACTIVE"], sort: "MOST_BIDS", pageSize: 1 }),
    listCategoriesWithLiveCounts(),
    getServerTime(),
  ]);

  // EN: The showcase is read in full for its live viewer count. / VI: Lô trưng bày được đọc đầy đủ để lấy số người đang xem.
  const showcase = featured.items[0] ? ((await getAuction(featured.items[0].id)) ?? featured.items[0]) : null;
  // The editorial spread quotes the description, which cards do not carry. It must not repeat the showcase.
  const spread = live.items.find((auction) => auction.id !== showcase?.id);
  const editorial = spread ? ((await getAuction(spread.id)) ?? spread) : null;

  return {
    live: live.items,
    endingSoon: endingSoon.items,
    upcoming: upcoming.items,
    featured: showcase,
    editorial,
    categories,
    stats: { liveCount: live.totalItems },
    serverTime,
  };
}

/** `GET /api/categories` */
export async function listCategories(): Promise<Category[]> {
  const categories = await api<ApiCategory[]>("/api/categories");
  return categories.map(toCategory);
}

/** Categories with how many of their lots are live, for the home page's rooms. */
async function listCategoriesWithLiveCounts(): Promise<Category[]> {
  const categories = await listCategories();
  const counts = await Promise.all(
    categories.map((category) =>
      api<ApiPage<ApiAuctionSummary>>("/api/auctions", {
        query: { status: ["ACTIVE"], category: [category.slug], size: 1 },
      }).then((page) => page.totalItems),
    ),
  );
  return categories.map((category, index) => ({ ...category, auctionCount: counts[index] }));
}
