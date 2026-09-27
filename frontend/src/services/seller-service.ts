"use client";

import { isLocalFileUrl, localFileFor } from "@/lib/local-files";
import type {
  Auction,
  AuctionSummary,
  Category,
  Product,
  ProductCondition,
  ProductImage,
} from "@/types";

import type { ApiAuction, ApiImage, ApiOrder, ApiProduct } from "./api/dto";
import { api } from "./api/http";
import { toAuction, toCategory, toImage, toProduct } from "./api/mappers";
import { toEntry, type OrderEntry } from "./order-service";
import { getCurrentUser } from "./session-service";

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

async function imagesOf(product: ApiProduct): Promise<ProductImage[]> {
  const images = await api<ApiImage[]>(`/api/seller/products/${product.id}/images`);
  return images.map((image) => toImage(image, product.name));
}

/** The seller's products with their photos, and all their auctions — one load for both lists. */
async function loadCatalogue(): Promise<{ products: ProductWithMeta[]; auctions: ApiAuction[] }> {
  const [products, auctions] = await Promise.all([
    api<ApiProduct[]>("/api/seller/products"),
    api<ApiAuction[]>("/api/seller/auctions"),
  ]);
  const images = await Promise.all(products.map(imagesOf));

  const entries = products.map((product, index) => {
    // The newest auction for a product is the one that matters.
    const auction = auctions
      .filter((item) => item.productId === product.id)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];
    return {
      product: toProduct(product, images[index]),
      category: product.category ? toCategory(product.category) : null,
      auction: auction ? toAuction(auction) : null,
    };
  });
  return { products: entries, auctions };
}

/** `GET /api/seller/products`, each with its photos and current auction. */
export async function listMyProducts(): Promise<ProductWithMeta[]> {
  return (await loadCatalogue()).products;
}

/** `GET /api/seller/products/{id}` */
export async function getProduct(productId: string): Promise<Product | null> {
  const product = await api<ApiProduct>(`/api/seller/products/${productId}`);
  return toProduct(product, await imagesOf(product));
}

export interface ProductInput {
  name: string;
  description: string;
  categoryId: string;
  condition: ProductCondition;
  /** Server URLs for photos kept, object URLs for new ones; the first is the cover. */
  imageUrls: string[];
}

/**
 * EN: Makes the product's photos match the form: uploads new files, deletes removed photos, and sets
 *     the first one as the cover.
 * VI: Làm cho ảnh của sản phẩm khớp với form: tải ảnh mới lên, xoá ảnh bị bỏ, và đặt ảnh đầu làm ảnh bìa.
 */
async function syncImages(productId: string, wanted: string[], existing: ProductImage[]): Promise<void> {
  const base = `/api/seller/products/${productId}/images`;

  for (const image of existing.filter((item) => !wanted.includes(item.url))) {
    await api(`${base}/${image.id}`, { method: "DELETE" });
  }

  const newFiles = wanted.filter(isLocalFileUrl).map((url) => ({ url, file: localFileFor(url) }));
  let current = existing.filter((item) => wanted.includes(item.url));
  const uploadedByUrl = new Map<string, string>();
  if (newFiles.some((entry) => entry.file)) {
    const form = new FormData();
    for (const { file } of newFiles) if (file) form.append("files", file);
    const after = await api<ApiImage[]>(base, { method: "POST", form });
    // The server lists existing photos first, then the new ones in upload order.
    const added = after.filter((image) => !current.some((item) => item.id === image.id));
    newFiles.forEach((entry, index) => {
      if (added[index]) uploadedByUrl.set(entry.url, added[index].id);
    });
    current = after.map((image) => toImage(image, ""));
  }

  const coverUrl = wanted[0];
  const coverId = coverUrl
    ? (uploadedByUrl.get(coverUrl) ?? current.find((image) => image.url === coverUrl)?.id)
    : undefined;
  if (coverId && current[0]?.id !== coverId) {
    await api(`${base}/${coverId}/cover`, { method: "PUT" });
  }
}

/** `POST /api/seller/products`, then its photos. */
export async function createProduct(input: ProductInput): Promise<Product> {
  const product = await api<ApiProduct>("/api/seller/products", {
    method: "POST",
    body: {
      name: input.name.trim(),
      description: input.description.trim(),
      categoryId: input.categoryId,
      condition: input.condition,
      publishNow: true,
    },
  });
  await syncImages(product.id, input.imageUrls, []);
  return toProduct(product, await imagesOf(product));
}

/** `PUT /api/seller/products/{id}`, then its photos. */
export async function updateProduct(productId: string, input: ProductInput): Promise<Product> {
  const existing = await imagesOf(await api<ApiProduct>(`/api/seller/products/${productId}`));
  const product = await api<ApiProduct>(`/api/seller/products/${productId}`, {
    method: "PUT",
    body: {
      name: input.name.trim(),
      description: input.description.trim(),
      categoryId: input.categoryId,
      condition: input.condition,
    },
  });
  await syncImages(productId, input.imageUrls, existing);
  return toProduct(product, await imagesOf(product));
}

/* -------------------------------------------------------------------------- */
/*                                  Auctions                                  */
/* -------------------------------------------------------------------------- */

/** `GET /api/seller/auctions`, as cards with the seller's own product details. */
export async function listMyAuctions(): Promise<AuctionSummary[]> {
  const { products, auctions } = await loadCatalogue();
  const seller = getCurrentUser();

  return auctions
    .map((view): AuctionSummary | null => {
      const entry = products.find((item) => item.product.id === view.productId);
      if (!entry) return null;
      return {
        ...toAuction(view),
        product: entry.product,
        category: entry.category ?? toCategory(null),
        seller: { id: view.sellerId, displayName: seller?.displayName ?? "" },
        watched: false,
      };
    })
    .filter((item): item is AuctionSummary => item !== null)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

export interface SellerStats {
  activeAuctions: number;
  upcomingAuctions: number;
  totalBids: number;
  completedSales: number;
  grossSales: number;
}

export async function getSellerStats(): Promise<SellerStats> {
  const mine = await listMyAuctions();
  const completed = mine.filter((auction) => auction.status === "COMPLETED");
  return {
    activeAuctions: mine.filter((auction) => auction.status === "ACTIVE").length,
    upcomingAuctions: mine.filter((auction) => auction.status === "SCHEDULED").length,
    totalBids: mine.reduce((total, auction) => total + auction.bidCount, 0),
    completedSales: completed.length,
    grossSales: completed.reduce((total, auction) => total + auction.currentPrice, 0),
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

/** `POST /api/seller/auctions`, and `…/submit` straight after when asked. */
export async function createAuction(
  input: AuctionInput,
  submitForApproval: boolean,
): Promise<Auction> {
  const created = await api<ApiAuction>("/api/seller/auctions", {
    method: "POST",
    body: {
      productId: input.productId,
      startingPrice: input.startingPrice,
      minimumIncrement: input.minimumIncrement,
      startTime: input.startTime,
      endTime: input.endTime,
      antiSnipingEnabled: input.antiSnipingEnabled,
      antiSnipingWindowSeconds: input.antiSnipingEnabled ? input.antiSnipingWindowSeconds : undefined,
      extensionSeconds: input.antiSnipingEnabled ? input.extensionSeconds : undefined,
    },
  });
  if (!submitForApproval) return toAuction(created);
  return toAuction(await api<ApiAuction>(`/api/seller/auctions/${created.id}/submit`, { method: "POST" }));
}

/** `POST /api/seller/auctions/{id}/submit` */
export async function submitAuctionForApproval(auctionId: string): Promise<Auction> {
  return toAuction(await api<ApiAuction>(`/api/seller/auctions/${auctionId}/submit`, { method: "POST" }));
}

/** `GET /api/seller/orders` — what this seller has sold, newest first. */
export async function listSoldOrders(): Promise<OrderEntry[]> {
  return (await api<ApiOrder[]>("/api/seller/orders")).map(toEntry);
}

/** `POST /api/seller/orders/{id}/ship` — PAID → PROCESSING. */
export async function shipOrder(orderId: string): Promise<OrderEntry> {
  return toEntry(await api<ApiOrder>(`/api/seller/orders/${orderId}/ship`, { method: "POST" }));
}
