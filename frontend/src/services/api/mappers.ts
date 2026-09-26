import type {
  AppNotification,
  Auction,
  AuctionSummary,
  AuditLog,
  Bid,
  Category,
  Order,
  Payment,
  Product,
  ProductImage,
  User,
} from "@/types";

import type {
  ApiAuction,
  ApiAuctionSummary,
  ApiAuditLog,
  ApiBid,
  ApiCategory,
  ApiImage,
  ApiNotification,
  ApiOrder,
  ApiPaymentDetails,
  ApiProduct,
  ApiUser,
} from "./dto";

/**
 * EN: Backend shapes → the domain types the UI was built against. Fields the API does not have stay
 *     undefined (the UI hides them) rather than being filled with invented numbers.
 * VI: Dạng dữ liệu của backend → kiểu dữ liệu miền mà UI dựa vào. Trường nào API không có thì để undefined
 *     (UI tự ẩn) chứ không điền số bịa.
 */

/** EN: The public handle, masked exactly as the server masks it. / VI: Tên hiển thị, che đúng như server che. */
export function maskName(fullName: string): string {
  const trimmed = fullName.trim();
  if (!trimmed) return "***";
  return `${trimmed.slice(0, 3).toLowerCase()}***`;
}

export function toUser(user: ApiUser): User {
  return {
    id: user.id,
    fullName: user.fullName,
    email: user.email,
    displayName: maskName(user.fullName),
    roles: user.roles,
    status: user.status,
    createdAt: user.createdAt ?? new Date(0).toISOString(),
  };
}

export function toCategory(category: ApiCategory | null): Category {
  if (!category) {
    return { id: "", slug: "", name: "", description: "", imageUrl: "" };
  }
  return {
    id: category.id,
    slug: category.slug,
    name: category.name,
    description: category.description ?? "",
    imageUrl: category.imageUrl ?? "",
  };
}

export function toImage(image: ApiImage, productName: string): ProductImage {
  return { id: image.id, url: image.url, alt: image.alt ?? productName, sortOrder: image.sortOrder };
}

export function toAuction(auction: ApiAuction): Auction {
  return {
    id: auction.id,
    productId: auction.productId,
    sellerId: auction.sellerId,
    lotNumber: auction.lotNumber ?? 0,
    startingPrice: Number(auction.startingPrice),
    currentPrice: Number(auction.currentPrice),
    minimumIncrement: Number(auction.minimumIncrement),
    startTime: auction.startTime,
    endTime: auction.endTime,
    status: auction.status,
    bidCount: auction.bidCount,
    winnerId: auction.winnerId,
    antiSniping: auction.antiSniping,
    extensionCount: auction.extensionCount,
    rejectionReason: auction.rejectionReason ?? undefined,
    createdAt: auction.createdAt,
    updatedAt: auction.updatedAt,
  };
}

/** EN: A catalogue card. The card only carries the cover photo. / VI: Một thẻ trong danh mục. Thẻ chỉ mang ảnh bìa. */
export function toSummary(summary: ApiAuctionSummary, watched = false): AuctionSummary {
  const auction = toAuction(summary.auction);
  const category = toCategory(summary.category);
  const product: Product = {
    id: summary.product.id,
    sellerId: summary.seller.id,
    categoryId: category.id,
    name: summary.product.name,
    description: "",
    condition: summary.product.condition ?? "GOOD",
    status: "IN_AUCTION",
    images: summary.product.coverImageUrl
      ? [{ id: `${summary.product.id}-cover`, url: summary.product.coverImageUrl, alt: summary.product.name, sortOrder: 0 }]
      : [],
    // EN: The card has no product dates; the lot's own creation is the listing date. / VI: Thẻ không có ngày của sản phẩm; ngày tạo lô chính là ngày đăng.
    createdAt: auction.createdAt,
    updatedAt: auction.updatedAt,
  };
  return {
    ...auction,
    product,
    category,
    seller: { id: summary.seller.id, displayName: summary.seller.displayName },
    watched,
  };
}

/**
 * EN: The API never says who bid, only whether it was the caller; the caller's own bids get their id
 *     so the UI can say "You".
 * VI: API không bao giờ nói ai trả giá, chỉ nói có phải người gọi không; lượt của chính người gọi được gắn
 *     id của họ để UI hiện "Bạn".
 */
export function toBid(bid: ApiBid, viewerId: string | null): Bid {
  return {
    id: bid.id,
    auctionId: bid.auctionId,
    bidderId: bid.mine && viewerId ? viewerId : `bidder:${bid.bidderMask}`,
    bidderDisplayName: bid.bidderMask,
    amount: Number(bid.amount),
    automatic: false,
    createdAt: bid.createdAt,
  };
}

/** EN: Where a notice leads when clicked. / VI: Thông báo dẫn tới đâu khi được bấm. */
function hrefFor(notification: ApiNotification): string | undefined {
  switch (notification.type) {
    case "AUCTION_WON":
    case "PAYMENT_REQUIRED":
      return "/payments";
    case "PAYMENT_SUCCESS":
    case "PAYMENT_EXPIRED":
      return "/orders";
    default:
      return notification.auctionId ? `/auctions/${notification.auctionId}` : undefined;
  }
}

export function toNotification(notification: ApiNotification, userId: string): AppNotification {
  return {
    id: notification.id,
    userId,
    type: notification.type,
    title: notification.title,
    message: notification.message,
    isRead: notification.read,
    href: hrefFor(notification),
    createdAt: notification.createdAt,
  };
}

export function toPayment(payment: ApiPaymentDetails): Payment {
  return { ...payment, amount: Number(payment.amount) };
}

export function toOrder(order: ApiOrder["order"]): Order {
  return { ...order, amount: Number(order.amount) };
}

export function toProduct(product: ApiProduct, images: ProductImage[] = []): Product {
  return {
    id: product.id,
    sellerId: product.sellerId,
    categoryId: product.category?.id ?? "",
    name: product.name,
    description: product.description,
    condition: product.condition,
    status: product.status,
    images,
    createdAt: product.createdAt,
    updatedAt: product.updatedAt,
  };
}

export function toAuditLog(entry: ApiAuditLog): AuditLog {
  return entry;
}
