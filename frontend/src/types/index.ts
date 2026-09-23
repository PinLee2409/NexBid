/**
 * Domain model for NexBid.
 *
 * These types mirror the REST contract described in the project specification
 * (§22–§29). The mock services in `src/services` produce exactly these shapes,
 * so swapping them for a real Spring Boot API should not require UI changes.
 */

/* -------------------------------------------------------------------------- */
/*                                   Shared                                   */
/* -------------------------------------------------------------------------- */

/** ISO-8601 timestamp, always produced by the server. */
export type IsoDateString = string;

export type Id = string;

/** Standard success envelope (spec §28). */
export interface ApiResponse<T> {
  success: true;
  message: string;
  data: T;
}

/** Standard error envelope (spec §28). */
export interface ApiError {
  success: false;
  code: ErrorCode;
  message: string;
  timestamp: IsoDateString;
}

/** Error codes the backend is expected to return (spec §29). */
export type ErrorCode =
  | "USER_NOT_FOUND"
  | "EMAIL_ALREADY_EXISTS"
  | "INVALID_CREDENTIALS"
  | "ACCOUNT_BLOCKED"
  | "PRODUCT_NOT_FOUND"
  | "AUCTION_NOT_FOUND"
  | "AUCTION_NOT_ACTIVE"
  | "AUCTION_ALREADY_ENDED"
  | "AUCTION_NOT_EDITABLE"
  | "SELLER_CANNOT_BID"
  | "BID_TOO_LOW"
  | "BID_CONFLICT"
  | "BID_RATE_LIMITED"
  | "AUTO_BID_INVALID"
  | "PAYMENT_NOT_FOUND"
  | "PAYMENT_EXPIRED"
  | "ACCESS_DENIED"
  | "NOT_AUTHENTICATED";

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

/* -------------------------------------------------------------------------- */
/*                                    User                                    */
/* -------------------------------------------------------------------------- */

export type UserRole = "BUYER" | "SELLER" | "ADMIN";

export type UserStatus = "ACTIVE" | "BLOCKED";

export interface User {
  id: Id;
  fullName: string;
  email: string;
  /** Masked handle shown in public bid history, e.g. `pin***`. */
  displayName: string;
  avatarUrl?: string;
  roles: UserRole[];
  status: UserStatus;
  createdAt: IsoDateString;
}

/** Public-facing seller summary shown on auction pages. */
export interface SellerSummary {
  id: Id;
  displayName: string;
  avatarUrl?: string;
  /** 0–5, one decimal. */
  rating: number;
  totalSales: number;
  memberSince: IsoDateString;
  verified: boolean;
}

/* -------------------------------------------------------------------------- */
/*                              Category & Product                            */
/* -------------------------------------------------------------------------- */

export interface Category {
  id: Id;
  slug: string;
  name: string;
  description: string;
  imageUrl: string;
  auctionCount: number;
}

/** Product condition (spec §7.3). */
export type ProductCondition = "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "USED";

export type ProductStatus = "DRAFT" | "AVAILABLE" | "IN_AUCTION" | "SOLD";

export interface ProductImage {
  id: Id;
  url: string;
  alt: string;
  sortOrder: number;
}

export interface Product {
  id: Id;
  sellerId: Id;
  categoryId: Id;
  name: string;
  description: string;
  condition: ProductCondition;
  status: ProductStatus;
  images: ProductImage[];
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/* -------------------------------------------------------------------------- */
/*                                   Auction                                  */
/* -------------------------------------------------------------------------- */

/** Auction lifecycle (spec §5.1). */
export type AuctionStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "SCHEDULED"
  | "ACTIVE"
  | "ENDED"
  | "COMPLETED"
  | "REJECTED"
  | "CANCELLED";

export interface AntiSnipingConfig {
  enabled: boolean;
  /** Bids inside this window before `endTime` extend the auction. */
  windowSeconds: number;
  /** How much time each triggered extension adds. */
  extensionSeconds: number;
}

export interface Auction {
  id: Id;
  productId: Id;
  sellerId: Id;

  /** Catalogue index, displayed as a three-digit lot number. */
  lotNumber: number;

  startingPrice: number;
  currentPrice: number;
  minimumIncrement: number;

  startTime: IsoDateString;
  endTime: IsoDateString;

  status: AuctionStatus;

  bidCount: number;
  winnerId: Id | null;

  antiSniping: AntiSnipingConfig;
  /** Number of times anti-sniping has pushed `endTime` back. */
  extensionCount: number;

  /** Admin decision context, present on REJECTED auctions. */
  rejectionReason?: string;

  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/**
 * Auction joined with everything the listing UI needs in a single payload.
 * The backend is expected to expose this as the auction list/detail DTO so the
 * client never fans out to N additional requests per card.
 */
export interface AuctionSummary extends Auction {
  product: Product;
  category: Category;
  seller: SellerSummary;
  /** Live viewer count, sourced from Redis (spec §20.3). */
  viewerCount: number;
  /** Whether the *current* user watches this auction. */
  watched: boolean;
}

export interface AuctionDetail extends AuctionSummary {
  recentBids: Bid[];
  /** Current user's position in this auction, absent when signed out. */
  viewerState?: ViewerAuctionState;
}

/** Where the signed-in user stands in a given auction. */
export interface ViewerAuctionState {
  isSeller: boolean;
  isHighestBidder: boolean;
  hasBid: boolean;
  /** The user's own most recent bid amount, if any. */
  lastBidAmount: number | null;
  autoBid: AutoBid | null;
}

/** Sort options exposed by the auction list endpoint (spec §7.6). */
export type AuctionSort =
  | "ENDING_SOON"
  | "NEWEST"
  | "PRICE_ASC"
  | "PRICE_DESC"
  | "MOST_BIDS";

/** Public browse filter — maps 1:1 to query params. */
export interface AuctionQuery {
  search?: string;
  categorySlugs?: string[];
  statuses?: AuctionStatus[];
  conditions?: ProductCondition[];
  minPrice?: number;
  maxPrice?: number;
  endingSoon?: boolean;
  sort?: AuctionSort;
  page?: number;
  pageSize?: number;
}

/* -------------------------------------------------------------------------- */
/*                                     Bid                                    */
/* -------------------------------------------------------------------------- */

export interface Bid {
  id: Id;
  auctionId: Id;
  bidderId: Id;
  /** Masked bidder handle — the API never exposes full identities publicly. */
  bidderDisplayName: string;
  amount: number;
  /** True when the bid was generated by the auto-bid engine (spec §14). */
  automatic: boolean;
  createdAt: IsoDateString;
}

export interface PlaceBidRequest {
  auctionId: Id;
  amount: number;
}

export interface PlaceBidResult {
  bid: Bid;
  auction: Auction;
}

/** Auto bid — max amount is never exposed to other users (spec §14, §30). */
export interface AutoBid {
  id: Id;
  auctionId: Id;
  userId: Id;
  maxAmount: number;
  active: boolean;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/* -------------------------------------------------------------------------- */
/*                                  Realtime                                  */
/* -------------------------------------------------------------------------- */

/**
 * Events broadcast on `/topic/auctions/{auctionId}` (spec §10).
 * The mock realtime client emits exactly these, so the WebSocket swap is a
 * transport change rather than a UI change.
 */
export type AuctionEvent =
  | {
      type: "BID_PLACED";
      auctionId: Id;
      currentPrice: number;
      totalBids: number;
      bid: Bid;
      serverTime: IsoDateString;
    }
  | {
      type: "AUCTION_EXTENDED";
      auctionId: Id;
      endTime: IsoDateString;
      extensionSeconds: number;
      serverTime: IsoDateString;
    }
  | {
      type: "AUCTION_STARTED";
      auctionId: Id;
      serverTime: IsoDateString;
    }
  | {
      type: "AUCTION_ENDED";
      auctionId: Id;
      winnerId: Id | null;
      finalPrice: number;
      serverTime: IsoDateString;
    }
  | {
      type: "VIEWERS_CHANGED";
      auctionId: Id;
      viewerCount: number;
      serverTime: IsoDateString;
    };

export type AuctionEventType = AuctionEvent["type"];

/** Transport-agnostic subscription handle. */
export type Unsubscribe = () => void;

/* -------------------------------------------------------------------------- */
/*                                  Watchlist                                 */
/* -------------------------------------------------------------------------- */

export interface WatchlistItem {
  id: Id;
  userId: Id;
  auctionId: Id;
  createdAt: IsoDateString;
  auction: AuctionSummary;
}

/* -------------------------------------------------------------------------- */
/*                           Notification / Payment                           */
/* -------------------------------------------------------------------------- */

/** Notification types (spec §18). */
export type NotificationType =
  | "OUTBID"
  | "AUCTION_STARTING"
  | "AUCTION_ENDING"
  | "AUCTION_WON"
  | "AUCTION_LOST"
  | "AUCTION_EXTENDED"
  | "PAYMENT_REQUIRED"
  | "PAYMENT_SUCCESS"
  | "PAYMENT_EXPIRED"
  | "AUCTION_CANCELLED";

export interface AppNotification {
  id: Id;
  userId: Id;
  type: NotificationType;
  title: string;
  message: string;
  isRead: boolean;
  /** Deep link into the relevant auction / order. */
  href?: string;
  createdAt: IsoDateString;
}

/** Payment status (spec §5.2). */
export type PaymentStatus =
  | "PENDING"
  | "SUCCESS"
  | "FAILED"
  | "EXPIRED"
  | "REFUNDED";

export interface Payment {
  id: Id;
  auctionId: Id;
  userId: Id;
  amount: number;
  status: PaymentStatus;
  expiredAt: IsoDateString;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/** Order status (spec §5.3). */
export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "PROCESSING"
  | "COMPLETED"
  | "CANCELLED";

export interface Order {
  id: Id;
  auctionId: Id;
  buyerId: Id;
  sellerId: Id;
  paymentId: Id | null;
  amount: number;
  status: OrderStatus;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

/* -------------------------------------------------------------------------- */
/*                                  Audit log                                 */
/* -------------------------------------------------------------------------- */

export type AuditAction =
  | "USER_LOGIN"
  | "AUCTION_CREATED"
  | "AUCTION_APPROVED"
  | "AUCTION_REJECTED"
  | "BID_PLACED"
  | "AUCTION_EXTENDED"
  | "AUCTION_ENDED"
  | "PAYMENT_SUCCESS"
  | "USER_BLOCKED";

export interface AuditLog {
  id: Id;
  userId: Id | null;
  actorDisplayName: string;
  action: AuditAction;
  entityType: string;
  entityId: Id;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string;
  createdAt: IsoDateString;
}
