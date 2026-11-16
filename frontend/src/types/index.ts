/**
 * Domain model for NexBid.
 *
 * These are the shapes the UI is built on, following the REST contract in the
 * project specification (§22–§29). The services in `src/services` map the
 * backend's answers (`services/api/dto.ts`) into them (`services/api/mappers.ts`).
 */

/* -------------------------------------------------------------------------- */
/*                                   Shared                                   */
/* -------------------------------------------------------------------------- */

/** ISO-8601 timestamp, always produced by the server. */
export type IsoDateString = string;

type Id = string;

/**
 * Error codes the backend returns — the same list, in the same order, as
 * `backend/.../common/exception/ErrorCode.java`. The two change together;
 * `src/i18n/messages.test.ts` fails when they drift apart.
 */
export const ERROR_CODES = [
  "USER_NOT_FOUND",
  "EMAIL_ALREADY_EXISTS",
  "INVALID_CREDENTIALS",
  "ACCOUNT_BLOCKED",
  "ADMIN_NOT_BLOCKABLE",
  "STAFF_ROLES_INVALID",
  "DEPOSIT_REQUIRED",
  "DEPOSIT_NOT_NEEDED",
  "DEPOSIT_ALREADY_HELD",
  "DEPOSIT_NOT_FOUND",
  "DEPOSIT_NOT_DUE",
  "OFFER_UNAVAILABLE",
  "OFFER_INVALID",
  "OFFER_LIMIT",
  "RELIST_NOT_ALLOWED",
  "VACATION_INVALID",
  "SELLER_ON_VACATION",
  "TEMPLATE_NOT_FOUND",
  "TEMPLATE_INVALID",
  "COUPON_INVALID",
  "COUPON_NOT_FOUND",
  "QUICK_REPLY_LIMIT",
  "QUICK_REPLY_NOT_FOUND",
  "MEDIA_NOT_FOUND",
  "SEALED_BID_EXISTS",
  "SEALED_LOT",
  "REFRESH_TOKEN_INVALID",
  "LOGIN_RATE_LIMITED",
  "ALREADY_SELLER",
  "SELLER_APPLICATION_PENDING",
  "SELLER_APPLICATION_NOT_FOUND",
  "SELLER_APPLICATION_NOT_PENDING",
  "PRODUCT_NOT_FOUND",
  "CATEGORY_NOT_FOUND",
  "CATEGORY_ALREADY_EXISTS",
  "IMAGE_NOT_FOUND",
  "IMAGE_INVALID",
  "IMAGE_TOO_LARGE",
  "IMAGE_LIMIT_REACHED",
  "PRODUCT_NOT_EDITABLE",
  "PRODUCT_NOT_SELLABLE",
  "PRODUCT_ALREADY_IN_AUCTION",
  "AUCTION_NOT_FOUND",
  "AUCTION_NOT_ACTIVE",
  "AUCTION_ALREADY_ENDED",
  "AUCTION_NOT_EDITABLE",
  "AUCTION_SCHEDULE_INVALID",
  "AUCTION_NOT_PENDING",
  "AUCTION_PRICES_INVALID",
  "RESERVE_DECISION_CLOSED",
  "SELLER_CANNOT_BID",
  "BID_TOO_LOW",
  "BID_CONFLICT",
  "BID_RATE_LIMITED",
  "AUTO_BID_INVALID",
  "AUTO_BID_EXISTS",
  "AUTO_BID_NOT_FOUND",
  "BUY_NOW_UNAVAILABLE",
  "PAYMENT_NOT_FOUND",
  "PAYMENT_EXPIRED",
  "PAYMENT_ALREADY_PAID",
  "PAYMENT_GATEWAY_INVALID",
  "ADDRESS_REQUIRED",
  "ORDER_NOT_FOUND",
  "ORDER_STATUS_INVALID",
  "REFUND_ACCOUNT_REQUIRED",
  "STRAY_CHARGE_NOT_FOUND",
  "STRAY_CHARGE_REFUNDED",
  "PAYOUT_AMOUNT_CHANGED",
  "EMAIL_NOT_FOUND",
  "EMAIL_NOT_RETRYABLE",
  "REFERENCE_USED",
  "AUCTION_NOT_REMOVABLE",
  "REPORT_NOT_FOUND",
  "REPORT_CLOSED",
  "REPORT_NOT_ALLOWED",
  "REPORT_DUPLICATE",
  "REPORT_LIMIT",
  "BUYER_REVIEW_NOT_ALLOWED",
  "BIDDER_RESTRICTED",
  "NOT_A_SELLER",
  "MESSAGES_CLOSED",
  "MESSAGE_BLOCKED",
  "MESSAGE_LIMIT",
  "SHIPPING_UNAVAILABLE",
  "TRACKING_NOT_FOUND",
  "GHN_REFUSED",
  "GHN_ADDRESS_REQUIRED",
  "TWO_FACTOR_ALREADY_ON",
  "TWO_FACTOR_NOT_SET_UP",
  "TWO_FACTOR_CODE_INVALID",
  "TWO_FACTOR_CHALLENGE_EXPIRED",
  "GOOGLE_SIGN_IN_OFF",
  "GOOGLE_SIGN_IN_FAILED",
  "IMPORT_INVALID",
  "PUSH_SUBSCRIPTION_INVALID",
  "PROMO_INVALID",
  "PROMO_EXISTS",
  "PASSWORD_RESET_INVALID",
  "PASSWORD_INCORRECT",
  "PASSWORD_UNCHANGED",
  "VIDEO_INVALID",
  "QUESTION_NOT_FOUND",
  "QUESTION_NOT_ALLOWED",
  "QUESTION_LIMIT",
  "QUESTION_ANSWERED",
  "SAVED_SEARCH_NOT_FOUND",
  "SAVED_SEARCH_LIMIT",
  "FOLLOW_NOT_ALLOWED",
  "DISPUTE_NOT_FOUND",
  "DISPUTE_CLOSED",
  "BANK_ACCOUNT_REQUIRED",
  "NOTHING_TO_PAY_OUT",
  "SECOND_CHANCE_UNAVAILABLE",
  "NO_RUNNER_UP",
  "OFFER_ALREADY_MADE",
  "OFFER_NOT_FOUND",
  "OFFER_NOT_PENDING",
  "REVIEW_NOT_ALLOWED",
  "REVIEW_EXISTS",
  "NOTIFICATION_NOT_FOUND",
  "ACCESS_DENIED",
  "NOT_AUTHENTICATED",
  "VALIDATION_ERROR",
  "NOT_FOUND",
  "METHOD_NOT_ALLOWED",
  "UNSUPPORTED_MEDIA_TYPE",
  "INTERNAL_ERROR",
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

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

export type UserRole = "BUYER" | "SELLER" | "ADMIN" | "MODERATOR" | "FINANCE" | "SUPPORT";

export type UserStatus = "ACTIVE" | "BLOCKED";

export interface User {
  id: Id;
  fullName: string;
  email: string;
  /** Masked handle shown in public bid history, e.g. `pin***`. */
  displayName: string;
  /** EN: A seller whose papers an admin checked. / VI: Người bán đã được admin xác minh giấy tờ. */
  verified?: boolean;
  avatarUrl?: string;
  roles: UserRole[];
  status: UserStatus;
  createdAt: IsoDateString;
}

export type SellerApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

/**
 * EN: A request to become a seller (spec §7.1), with who sent it.
 * VI: Một yêu cầu trở thành người bán (spec §7.1), kèm người gửi.
 */
export interface SellerApplication {
  id: Id;
  userId: Id;
  fullName: string;
  email: string;
  note: string;
  status: SellerApplicationStatus;
  rejectionReason: string | null;
  createdAt: IsoDateString;
  decidedAt: IsoDateString | null;
}

export type OfferStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "EXPIRED";

/**
 * EN: A second-chance offer (spec §17): the lot at the runner-up's own highest bid, open until `expiresAt`.
 * VI: Một đề nghị cơ hội thứ hai (spec §17): lô với giá cao nhất của chính người thứ hai, mở tới `expiresAt`.
 */
export interface SecondChanceOffer {
  id: Id;
  auctionId: Id;
  amount: number;
  status: OfferStatus;
  expiresAt: IsoDateString;
  createdAt: IsoDateString;
  decidedAt: IsoDateString | null;
}

/**
 * EN: A buyer's review of a seller, as anyone may read it: the reviewer by public handle only.
 * VI: Đánh giá của người mua về người bán mà ai cũng đọc được: người viết chỉ hiện tên công khai.
 */
export interface SellerReview {
  id: Id;
  orderId: Id;
  auctionId: Id;
  /** 1–5 stars. */
  rating: number;
  comment: string | null;
  /** EN: Optional stars per aspect. / VI: Số sao tuỳ chọn cho từng khía cạnh. */
  packaging?: number | null;
  accuracy?: number | null;
  speed?: number | null;
  reviewer: string;
  lotName: string | null;
  createdAt: IsoDateString;
}

/** EN: A seller's standing, shown on their lots. / VI: Uy tín của người bán, hiện trên các lô của họ. */
export interface SellerReputation {
  /** One decimal; null before the first review. */
  average: number | null;
  reviewCount: number;
  sales: number;
  /** EN: Average stars per aspect; null where nobody rated it. / VI: Sao trung bình mỗi khía cạnh; null nếu chưa ai chấm. */
  aspects: { packaging: number | null; accuracy: number | null; speed: number | null };
  reviews: SellerReview[];
}

/** Public-facing seller summary shown on auction pages. */
interface SellerSummary {
  id: Id;
  displayName: string;
  avatarUrl?: string;
  memberSince?: IsoDateString;
  verified?: boolean;
}

/* -------------------------------------------------------------------------- */
/*                              Category & Product                            */
/* -------------------------------------------------------------------------- */

/** EN: ARCHIVED categories keep their products but leave the browse list. / VI: Danh mục ARCHIVED giữ sản phẩm nhưng rời khỏi danh sách duyệt. */
export type CategoryStatus = "ACTIVE" | "ARCHIVED";

export interface Category {
  id: Id;
  slug: string;
  name: string;
  description: string;
  imageUrl: string;
  /** Known when the API sent it; only the admin list includes archived ones. */
  status?: CategoryStatus;
  /** Lots live in this category, when the caller asked for it. */
  auctionCount?: number;
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

/** EN: Weight in grams, sides in centimetres. / VI: Cân nặng tính bằng gam, các cạnh tính bằng cm. */
export interface Parcel {
  weightGrams: number;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
}

/** EN: One piece of a set sold as one lot. / VI: Một món trong một bộ bán chung một lô. */
export interface BundleItem {
  name: string;
  quantity: number;
}

export interface Product {
  id: Id;
  sellerId: Id;
  categoryId: Id;
  name: string;
  description: string;
  condition: ProductCondition;
  status: ProductStatus;
  /** EN: Packed size for shipping quotes; null when not given. / VI: Kích thước kiện để báo phí ship; null khi chưa khai. */
  parcel?: Parcel | null;
  /** EN: The pieces of a set; empty for a single item. / VI: Các món của một bộ; rỗng với món đơn lẻ. */
  items?: BundleItem[];
  /** EN: A short video, or null. / VI: Một video ngắn, hoặc null. */
  videoUrl?: string | null;
  /** EN: The photos are frames of a 360° turn. / VI: Các ảnh là khung hình của một vòng xoay 360°. */
  spinView?: boolean;
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
  | "CANCELLED"
  | "REMOVED";

interface AntiSnipingConfig {
  enabled: boolean;
  /** Bids inside this window before `endTime` extend the auction. */
  windowSeconds: number;
  /** How much time each triggered extension adds. */
  extensionSeconds: number;
}

/** EN: A lot that closed below its reserve: waiting for the seller, sold anyway, or not sold. / VI: Lô đóng dưới giá sàn: chờ người bán, vẫn bán, hoặc không bán. */
export type ReserveDecision = "PENDING" | "ACCEPTED" | "DECLINED" | "LAPSED";

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

  /** EN: The amount, for the seller and admins only. / VI: Con số, chỉ cho người bán và admin. */
  reservePrice?: number;
  hasReserve: boolean;
  /** EN: Null without a reserve. / VI: Null nếu không có giá sàn. */
  reserveMet: boolean | null;
  /** EN: Offered until the first bid. / VI: Có hiệu lực tới lượt trả giá đầu tiên. */
  buyNowPrice: number | null;
  boughtNow: boolean;
  /** EN: The seller pays the parcel. / VI: Người bán trả tiền gửi kiện. */
  freeShipping: boolean;
  /** EN: One hidden bid per person, the highest wins at the close. / VI: Mỗi người một lượt giá ẩn, giá cao nhất thắng khi đóng. */
  sealed: boolean;
  /** EN: A draft timed to go for approval. / VI: Bản nháp hẹn giờ gửi duyệt. */
  submitAt?: IsoDateString | null;
  /** EN: Set once the lot closed below its reserve. / VI: Có giá trị khi lô đã đóng dưới giá sàn. */
  reserveDecision: ReserveDecision | null;
  reserveDecisionUntil: IsoDateString | null;

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
  /** Live viewer count (spec §20.3). Not provided by the API yet; hidden when absent. */
  viewerCount?: number;
  /** Whether the *current* user watches this auction. */
  watched: boolean;
}

export interface AuctionDetail extends AuctionSummary {
  recentBids: Bid[];
  /** Current user's position in this auction, absent when signed out. */
  viewerState?: ViewerAuctionState;
}

/** Where the signed-in user stands in a given auction. */
interface ViewerAuctionState {
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

/** What the server answers to an accepted bid. */
export interface PlaceBidResult {
  bid: Bid;
  currentPrice: number;
  bidCount: number;
  minimumNextBid: number;
  endTime: IsoDateString;
  /** False when an auto bid answered straight away and took the lead back. */
  leading: boolean;
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
 * Events broadcast on `/topic/auctions/{auctionId}` (spec §10), as the realtime
 * service hands them to the pages.
 */
export type AuctionEvent =
  | {
      type: "BID_PLACED";
      auctionId: Id;
      currentPrice: number;
      totalBids: number;
      bid: Bid;
      /** Null without a reserve. */
      reserveMet: boolean | null;
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
      /** False with bids means it closed below the reserve. */
      sold: boolean;
      endTime: IsoDateString;
      serverTime: IsoDateString;
    }
  | {
      type: "AUCTION_REMOVED";
      auctionId: Id;
      serverTime: IsoDateString;
    }
  | {
      type: "VIEWERS_CHANGED";
      auctionId: Id;
      viewerCount: number;
      serverTime: IsoDateString;
    };

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
  | "AUCTION_CANCELLED"
  | "SELLER_APPROVED"
  | "SELLER_REJECTED"
  | "SECOND_CHANCE_OFFER"
  | "SECOND_CHANCE_ACCEPTED"
  | "SECOND_CHANCE_DECLINED"
  | "NEW_REVIEW"
  | "RESERVE_NOT_MET"
  | "DISPUTE_OPENED"
  | "DISPUTE_RESOLVED"
  | "DISPUTE_SELLER_RESOLVED"
  | "PAYOUT_SENT"
  | "ORDER_COMPLETING"
  | "ORDER_REFUNDED"
  | "NEW_QUESTION"
  | "QUESTION_ANSWERED"
  | "LOT_ALERT"
  | "STRAY_CHARGE"
  | "SALE_REFUNDED"
  | "LOT_REMOVED"
  | "REPORT_RESOLVED"
  | "SELLER_VERIFIED"
  | "NEW_MESSAGE"
  | "SHIPMENT_CANCELLED"
  | "ORDER_ADDRESS_CHANGED"
  | "OFFER_RECEIVED"
  | "OFFER_ANSWERED"
  | "LOT_RELISTED"
  | "MEDIA_REMOVED";

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
  /** EN: Null until quoted at checkout. / VI: Null tới khi báo phí lúc thanh toán. */
  shippingFee: number | null;
  shippingSource: "GHN" | "FLAT" | "SELLER" | "COMBINED" | null;
  /** EN: Goods plus shipping less any voucher: what VNPay charges. / VI: Tiền hàng cộng phí ship trừ voucher: số VNPay thu. */
  total: number;
  shippingDiscount?: number;
  promoCode?: string | null;
  /** EN: Taken off the goods by the seller's own code. / VI: Phần mã riêng của người bán trừ vào tiền hàng. */
  sellerDiscount?: number;
  sellerCoupon?: string | null;
  /** EN: The lot whose parcel this one travels in. / VI: Lô mà kiện của nó chở luôn lô này. */
  shipsWithAuctionId?: string | null;
}

/** Order status (spec §5.3). */
export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "PROCESSING"
  | "COMPLETED"
  | "DISPUTED"
  | "CANCELLED";

export type DisputeReason = "NOT_RECEIVED" | "NOT_AS_DESCRIBED" | "DAMAGED" | "OTHER";
export type DisputeStatus = "OPEN" | "REFUNDED" | "REJECTED";

/** EN: The newest complaint about an order. / VI: Khiếu nại mới nhất về một đơn. */
export interface OrderDispute {
  id: Id;
  reason: DisputeReason;
  details: string;
  status: DisputeStatus;
  resolutionNote: string | null;
  createdAt: IsoDateString;
  resolvedAt: IsoDateString | null;
}

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
  /** EN: Where it goes, copied when the buyer paid. / VI: Nơi giao tới, chép lại lúc người mua thanh toán. */
  shipping: OrderShipping | null;
  carrier: string | null;
  trackingNumber: string | null;
  shippedAt: IsoDateString | null;
  completedAt: IsoDateString | null;
  /** EN: Completed because the buyer stayed silent past the wait. / VI: Hoàn tất vì người mua im lặng quá thời gian chờ. */
  autoCompleted: boolean;
  dispute: OrderDispute | null;
  /** EN: The carrier's latest status (GHN), and when it delivered. / VI: Trạng thái mới nhất của đơn vị vận chuyển (GHN), và lúc giao. */
  shippingStatus: string | null;
  deliveredAt: IsoDateString | null;
  /** EN: NexBid booked GHN for it (a label can be printed). / VI: NexBid đã đặt GHN cho đơn này (in được phiếu). */
  ghnBooked: boolean;
  /** EN: The address paid for was picked from GHN's lists. / VI: Địa chỉ đã trả tiền được chọn từ danh sách GHN. */
  ghnAddress: boolean;
}

interface OrderShipping {
  recipient: string;
  phone: string;
  address: string;
}

/** EN: A buyer's delivery address, as edited on the profile. / VI: Địa chỉ nhận hàng của người mua, sửa trong hồ sơ. */
export interface ShippingAddress {
  recipient: string;
  phone: string;
  line: string;
  ward: string;
  district: string;
  province: string;
  /** EN: GHN's ids, when picked from GHN's lists; all three or none. / VI: Mã GHN khi chọn từ danh sách GHN; đủ cả ba hoặc không có. */
  provinceId?: number | null;
  districtId?: number | null;
  wardCode?: string | null;
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
  | "PAYMENT_REFUNDED"
  | "ORDER_SHIPPED"
  | "ORDER_COMPLETED"
  | "USER_BLOCKED"
  | "USER_UNBLOCKED"
  | "SELLER_APPLIED"
  | "SELLER_APPROVED"
  | "SELLER_REJECTED"
  | "OFFER_MADE"
  | "OFFER_ACCEPTED"
  | "OFFER_DECLINED"
  | "OFFER_EXPIRED"
  | "RESERVE_ACCEPTED"
  | "RESERVE_REFUSED"
  | "RESERVE_LAPSED"
  | "BOUGHT_NOW"
  | "DISPUTE_OPENED"
  | "DISPUTE_REFUNDED"
  | "DISPUTE_REJECTED"
  | "PAYOUT_RECORDED"
  | "BANK_ACCOUNT_CHANGED"
  | "STRAY_CHARGE_REFUNDED"
  | "AUCTION_REMOVED"
  | "SELLER_VERIFIED"
  | "ORDER_MESSAGES_READ"
  | "SHILL_PAIR_CLEARED"
  | "TWO_FACTOR_ENABLED"
  | "TWO_FACTOR_DISABLED"
  | "STAFF_ROLES_CHANGED"
  | "AUCTION_RELISTED"
  | "LOT_OFFER_ACCEPTED"
  | "DEPOSIT_FORFEITED"
  | "DEPOSIT_REFUNDED"
  | "MEDIA_REMOVED";

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
