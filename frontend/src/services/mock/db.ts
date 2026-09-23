import type {
  AppNotification,
  Auction,
  AuctionSummary,
  AuditLog,
  AutoBid,
  Bid,
  Category,
  Order,
  Payment,
  Product,
  SellerSummary,
  User,
} from "@/types";

import {
  AUCTIONS,
  AUDIT_LOGS,
  AUTO_BIDS,
  BIDS,
  CATEGORIES,
  CURRENT_USER_ID,
  NOTIFICATIONS,
  ORDERS,
  PAYMENTS,
  PRODUCTS,
  SELLERS,
  SEED_EPOCH,
  USERS,
  WATCHLIST_SEED,
} from "./seed";

/**
 * In-memory stand-in for the backend.
 *
 * It holds mutable copies of the seed data so optimistic actions (placing a
 * bid, watching an auction, marking a notification read) behave like a real
 * API within a session. Only `src/services/*` may import this module.
 */

interface MockDatabase {
  users: User[];
  sellers: SellerSummary[];
  categories: Category[];
  products: Product[];
  auctions: Auction[];
  bids: Bid[];
  autoBids: AutoBid[];
  watchedAuctionIds: Set<string>;
  notifications: AppNotification[];
  payments: Payment[];
  orders: Order[];
  auditLogs: AuditLog[];
}

function createDatabase(): MockDatabase {
  return {
    users: structuredClone(USERS),
    sellers: structuredClone(SELLERS),
    categories: structuredClone(CATEGORIES),
    products: structuredClone(PRODUCTS),
    auctions: structuredClone(AUCTIONS),
    bids: structuredClone(BIDS),
    autoBids: structuredClone(AUTO_BIDS),
    watchedAuctionIds: new Set(WATCHLIST_SEED.map((item) => item.auctionId)),
    notifications: structuredClone(NOTIFICATIONS),
    payments: structuredClone(PAYMENTS),
    orders: structuredClone(ORDERS),
    auditLogs: structuredClone(AUDIT_LOGS),
  };
}

/**
 * Survives Fast Refresh so an in-session bid is not wiped by a module reload,
 * but is rebuilt whenever the seed module itself is re-evaluated — otherwise
 * edits to the catalogue would never show up in development.
 */
const globalStore = globalThis as typeof globalThis & {
  __nexbidDb?: { epoch: number; data: MockDatabase };
};

if (globalStore.__nexbidDb?.epoch !== SEED_EPOCH) {
  globalStore.__nexbidDb = { epoch: SEED_EPOCH, data: createDatabase() };
}

export const db: MockDatabase = globalStore.__nexbidDb.data;

export { CURRENT_USER_ID };

/* -------------------------------------------------------------------------- */
/*                                  Latency                                   */
/* -------------------------------------------------------------------------- */

/**
 * Client calls get a small delay so loading and skeleton states are exercised
 * during development. Server rendering stays instant.
 */
const DEFAULT_LATENCY_MS = typeof window === "undefined" ? 0 : 220;

export function delay(ms: number = DEFAULT_LATENCY_MS): Promise<void> {
  if (ms <= 0) return Promise.resolve();
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

/** The server is the source of truth for time (spec §11). */
export function serverTime(): string {
  return new Date().toISOString();
}

/* -------------------------------------------------------------------------- */
/*                                   Lookups                                  */
/* -------------------------------------------------------------------------- */

export function findProduct(productId: string): Product | undefined {
  return db.products.find((product) => product.id === productId);
}

export function findCategory(categoryId: string): Category | undefined {
  return db.categories.find((category) => category.id === categoryId);
}

export function findSeller(sellerId: string): SellerSummary | undefined {
  return db.sellers.find((seller) => seller.id === sellerId);
}

export function findUser(userId: string): User | undefined {
  return db.users.find((user) => user.id === userId);
}

export function findAuction(auctionId: string): Auction | undefined {
  return db.auctions.find((auction) => auction.id === auctionId);
}

export function bidsForAuction(auctionId: string): Bid[] {
  return db.bids
    .filter((bid) => bid.auctionId === auctionId)
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() ||
        b.amount - a.amount,
    );
}

/**
 * Advances auction lifecycle the way the backend scheduler would (spec §12).
 * Called before every read so the marketplace never shows an ACTIVE auction
 * whose end time has already passed.
 */
export function reconcileAuctionStatuses(now: number = Date.now()): void {
  for (const auction of db.auctions) {
    const startMs = new Date(auction.startTime).getTime();
    const endMs = new Date(auction.endTime).getTime();

    if (auction.status === "SCHEDULED" && startMs <= now) {
      auction.status = "ACTIVE";
      auction.updatedAt = new Date(now).toISOString();
      continue;
    }

    if (auction.status === "ACTIVE" && endMs <= now) {
      const highest = bidsForAuction(auction.id)[0];
      auction.status = "ENDED";
      auction.winnerId = highest?.bidderId ?? null;
      auction.updatedAt = new Date(now).toISOString();
    }
  }
}

/**
 * Composes the denormalised DTO the listing and detail screens consume. The
 * real API is expected to return this exact shape in one round trip.
 */
export function toAuctionSummary(auction: Auction): AuctionSummary {
  const product = findProduct(auction.productId);
  if (!product) {
    throw new Error(`Missing product for auction ${auction.id}`);
  }

  const category = findCategory(product.categoryId);
  if (!category) {
    throw new Error(`Missing category for product ${product.id}`);
  }

  const seller = findSeller(product.sellerId);
  if (!seller) {
    throw new Error(`Missing seller for product ${product.id}`);
  }

  return {
    ...auction,
    product,
    category,
    seller,
    viewerCount: viewerCountFor(auction),
    watched: db.watchedAuctionIds.has(auction.id),
  };
}

/**
 * Viewer counts come from Redis in production (spec §20.3). Here they are
 * derived deterministically from auction state so they stay stable between
 * server render and hydration.
 */
function viewerCountFor(auction: Auction): number {
  if (auction.status !== "ACTIVE") return 0;
  const seed = auction.id
    .split("")
    .reduce((total, char) => total + char.charCodeAt(0), 0);
  return 20 + ((seed * 7 + auction.bidCount * 13) % 300);
}

/** Auctions the public marketplace is allowed to surface. */
export function publicAuctions(): Auction[] {
  return db.auctions.filter(
    (auction) =>
      auction.status === "ACTIVE" ||
      auction.status === "SCHEDULED" ||
      auction.status === "ENDED" ||
      auction.status === "COMPLETED",
  );
}

let sequence = 0;

export function nextId(prefix: string): string {
  sequence += 1;
  return `${prefix}-${Date.now().toString(36)}-${sequence}`;
}
