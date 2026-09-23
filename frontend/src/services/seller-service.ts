"use client";

import { AUCTION_CONFIG } from "@/constants/auction";
import type {
  Auction,
  AuctionSummary,
  Category,
  Product,
  ProductCondition,
} from "@/types";

import {
  CURRENT_USER_ID,
  db,
  delay,
  findAuction,
  nextId,
  reconcileAuctionStatuses,
  toAuctionSummary,
} from "./mock/db";

/**
 * Seller APIs (spec §7.3, §7.4, §27).
 *
 * Products exist independently of auctions: a seller describes an item once,
 * then schedules it. Auctions stay editable only while they are drafts.
 */

/* -------------------------------------------------------------------------- */
/*                                  Products                                  */
/* -------------------------------------------------------------------------- */

export interface ProductWithMeta {
  product: Product;
  category: Category | null;
  /** The auction currently using this product, if any. */
  auction: Auction | null;
}

/** `GET /api/products?sellerId=me` */
export async function listMyProducts(): Promise<ProductWithMeta[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.products
    .filter((product) => product.sellerId === CURRENT_USER_ID)
    .map((product) => ({
      product,
      category:
        db.categories.find((item) => item.id === product.categoryId) ?? null,
      auction:
        db.auctions.find((item) => item.productId === product.id) ?? null,
    }))
    .sort(
      (a, b) =>
        new Date(b.product.createdAt).getTime() -
        new Date(a.product.createdAt).getTime(),
    );
}

export async function getProduct(productId: string): Promise<Product | null> {
  await delay();
  return db.products.find((product) => product.id === productId) ?? null;
}

export interface ProductInput {
  name: string;
  description: string;
  categoryId: string;
  condition: ProductCondition;
  imageUrls: string[];
}

/** `POST /api/products` */
export async function createProduct(input: ProductInput): Promise<Product> {
  await delay(700);

  const timestamp = new Date().toISOString();
  const id = nextId("prod");

  const product: Product = {
    id,
    sellerId: CURRENT_USER_ID,
    categoryId: input.categoryId,
    name: input.name.trim(),
    description: input.description.trim(),
    condition: input.condition,
    status: "AVAILABLE",
    images: input.imageUrls.map((url, index) => ({
      id: `${id}-img-${index}`,
      url,
      alt: `${input.name} — view ${index + 1}`,
      sortOrder: index,
    })),
    createdAt: timestamp,
    updatedAt: timestamp,
  };

  db.products.unshift(product);
  return product;
}

/** `PUT /api/products/{id}` */
export async function updateProduct(
  productId: string,
  input: ProductInput,
): Promise<Product | null> {
  await delay(600);

  const product = db.products.find((item) => item.id === productId);
  if (!product) return null;

  product.name = input.name.trim();
  product.description = input.description.trim();
  product.categoryId = input.categoryId;
  product.condition = input.condition;
  product.images = input.imageUrls.map((url, index) => ({
    id: `${productId}-img-${index}`,
    url,
    alt: `${input.name} — view ${index + 1}`,
    sortOrder: index,
  }));
  product.updatedAt = new Date().toISOString();

  return { ...product };
}

/* -------------------------------------------------------------------------- */
/*                                  Auctions                                  */
/* -------------------------------------------------------------------------- */

/** `GET /api/auctions?sellerId=me` */
export async function listMyAuctions(): Promise<AuctionSummary[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.auctions
    .filter((auction) => auction.sellerId === CURRENT_USER_ID)
    .map(toAuctionSummary)
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
}

export interface SellerStats {
  activeAuctions: number;
  upcomingAuctions: number;
  totalBids: number;
  completedSales: number;
  grossSales: number;
}

export async function getSellerStats(): Promise<SellerStats> {
  await delay();
  reconcileAuctionStatuses();

  const mine = db.auctions.filter(
    (auction) => auction.sellerId === CURRENT_USER_ID,
  );

  return {
    activeAuctions: mine.filter((auction) => auction.status === "ACTIVE").length,
    upcomingAuctions: mine.filter((auction) => auction.status === "SCHEDULED")
      .length,
    totalBids: mine.reduce((total, auction) => total + auction.bidCount, 0),
    completedSales: mine.filter((auction) => auction.status === "COMPLETED")
      .length,
    grossSales: mine
      .filter((auction) => auction.status === "COMPLETED")
      .reduce((total, auction) => total + auction.currentPrice, 0),
  };
}

export interface AuctionInput {
  productId: string;
  startingPrice: number;
  minimumIncrement: number;
  startTime: string;
  endTime: string;
  antiSnipingEnabled: boolean;
  antiSnipingWindowSeconds: number;
  extensionSeconds: number;
}

/** `POST /api/auctions` */
export async function createAuction(
  input: AuctionInput,
  submitForApproval: boolean,
): Promise<Auction> {
  await delay(800);

  const timestamp = new Date().toISOString();

  const auction: Auction = {
    id: nextId("auction"),
    productId: input.productId,
    sellerId: CURRENT_USER_ID,
    // New lots continue the catalogue numbering.
    lotNumber: db.auctions.reduce((highest, item) => Math.max(highest, item.lotNumber), 0) + 1,
    startingPrice: input.startingPrice,
    currentPrice: input.startingPrice,
    minimumIncrement: input.minimumIncrement,
    startTime: input.startTime,
    endTime: input.endTime,
    status: submitForApproval ? "PENDING_APPROVAL" : "DRAFT",
    bidCount: 0,
    winnerId: null,
    antiSniping: {
      enabled: input.antiSnipingEnabled,
      windowSeconds:
        input.antiSnipingWindowSeconds ||
        AUCTION_CONFIG.defaultAntiSnipingWindowSeconds,
      extensionSeconds:
        input.extensionSeconds || AUCTION_CONFIG.defaultExtensionSeconds,
    },
    extensionCount: 0,
    createdAt: timestamp,
    updatedAt: timestamp,
  };

  db.auctions.unshift(auction);

  const product = db.products.find((item) => item.id === input.productId);
  if (product) product.status = "IN_AUCTION";

  return auction;
}

/** `POST /api/auctions/{id}/submit` */
export async function submitAuctionForApproval(
  auctionId: string,
): Promise<Auction | null> {
  await delay(500);

  const auction = findAuction(auctionId);
  if (!auction) return null;

  // Only a draft (or a rejected listing being resubmitted) can be sent.
  if (auction.status !== "DRAFT" && auction.status !== "REJECTED") {
    return auction;
  }

  auction.status = "PENDING_APPROVAL";
  auction.rejectionReason = undefined;
  auction.updatedAt = new Date().toISOString();

  return { ...auction };
}
