import type {
  AuctionStatus,
  AuditAction,
  CategoryStatus,
  IsoDateString,
  ReserveDecision,
  NotificationType,
  OfferStatus,
  OrderDispute,
  OrderStatus,
  Parcel,
  PaymentStatus,
  ProductCondition,
  ProductStatus,
  UserRole,
  UserStatus,
} from "@/types";

/**
 * EN: What the backend actually sends (its Java view records), before mapping into the domain types in
 *     `@/types`. Only these files and `mappers.ts` should know these shapes.
 * VI: Thứ backend thực sự gửi về (các record view bên Java), trước khi đổi sang kiểu dữ liệu miền trong
 *     `@/types`. Chỉ file này và `mappers.ts` được biết các dạng này.
 */

export interface ApiPage<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface ApiUser {
  id: string;
  fullName: string;
  email: string;
  roles: UserRole[];
  status: UserStatus;
  createdAt?: IsoDateString;
  verified?: boolean;
}

export interface ApiLogin {
  accessToken: string;
  expiresAt: IsoDateString;
  user: ApiUser;
  /** EN: Instead of the token when a code from the authenticator app is needed. / VI: Thay cho token khi cần mã từ ứng dụng xác thực. */
  twoFactorChallenge?: string | null;
}

export interface ApiCategory {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  status: CategoryStatus;
}

export interface ApiAuction {
  id: string;
  lotNumber: number | null;
  productId: string;
  sellerId: string;
  startingPrice: number;
  currentPrice: number;
  minimumIncrement: number;
  startTime: IsoDateString;
  endTime: IsoDateString;
  status: AuctionStatus;
  antiSniping: { enabled: boolean; windowSeconds: number; extensionSeconds: number };
  extensionCount: number;
  bidCount: number;
  winnerId: string | null;
  rejectionReason: string | null;
  reservePrice?: number | null;
  hasReserve?: boolean;
  reserveMet?: boolean | null;
  buyNowPrice?: number | null;
  boughtNow?: boolean;
  freeShipping?: boolean;
  sealed?: boolean;
  submitAt?: IsoDateString | null;
  reserveDecision?: ReserveDecision | null;
  reserveDecisionUntil?: IsoDateString | null;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

export interface ApiAuctionSummary {
  auction: ApiAuction;
  product: { id: string; name: string; coverImageUrl: string | null; condition: ProductCondition | null };
  category: ApiCategory | null;
  seller: { id: string; displayName: string; verified?: boolean };
}

export interface ApiImage {
  id: string;
  url: string;
  alt: string | null;
  sortOrder: number;
}

/** EN: One UTC hour of the Analytics Consumer's totals. / VI: Một giờ UTC trong số liệu tổng của Analytics Consumer. */
export interface ApiAnalyticsHour {
  hour: IsoDateString;
  bids: number;
  auctionsEnded: number;
  auctionsSold: number;
  payments: number;
  revenue: number;
}

export interface ApiAnalyticsReport {
  hours: ApiAnalyticsHour[];
  totals: Omit<ApiAnalyticsHour, "hour"> & { sellThrough: number | null };
}

export interface ApiAuctionDetail {
  auction: ApiAuction;
  product: {
    id: string;
    name: string;
    description: string;
    condition: ProductCondition;
    items?: { name: string; quantity: number }[];
    videoUrl?: string | null;
    spinView?: boolean;
  };
  category: ApiCategory | null;
  images: ApiImage[];
  seller: { id: string; displayName: string; verified?: boolean };
  minimumNextBid: number;
  openForBidding: boolean;
  /** EN: Null when the server cannot count right now. / VI: Null khi server tạm thời không đếm được. */
  viewerCount: number | null;
  serverTime: IsoDateString;
}

export interface ApiBid {
  id: string;
  auctionId: string;
  bidderMask: string;
  /** EN: True when the caller placed it (needs a token). / VI: True khi chính người gọi đặt (cần token). */
  mine: boolean;
  amount: number;
  createdAt: IsoDateString;
}

export interface ApiPlacedBid {
  bid: ApiBid;
  currentPrice: number;
  bidCount: number;
  minimumNextBid: number;
  endTime: IsoDateString;
  serverTime: IsoDateString;
  leading: boolean;
}

export interface ApiAutoBid {
  auctionId: string;
  maxAmount: number;
  active: boolean;
  currentPrice: number;
  minimumNextBid: number;
  leading: boolean;
  updatedAt: IsoDateString;
}

type ApiBidStanding = "WINNING" | "OUTBID" | "WON" | "LOST";

export interface ApiMyBid {
  auction: ApiAuctionSummary;
  yourBid: number;
  yourLastBidAt: IsoDateString;
  standing: ApiBidStanding;
}

export interface ApiNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  auctionId: string | null;
  read: boolean;
  createdAt: IsoDateString;
}

export interface ApiNotificationInbox {
  notifications: ApiPage<ApiNotification>;
  unreadCount: number;
}

export interface ApiPaymentDetails {
  id: string;
  auctionId: string;
  userId: string;
  amount: number;
  status: PaymentStatus;
  expiredAt: IsoDateString;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
  shippingFee?: number | null;
  shippingSource?: "GHN" | "FLAT" | "SELLER" | "COMBINED" | null;
  total?: number;
  shippingDiscount?: number;
  promoCode?: string | null;
  sellerDiscount?: number;
  sellerCoupon?: string | null;
  shipsWithAuctionId?: string | null;
}

export interface ApiPayment {
  payment: ApiPaymentDetails;
  auction: ApiAuctionSummary | null;
}

/** `OfferView.Details` (spec §17). */
export interface ApiOfferDetails {
  id: string;
  auctionId: string;
  amount: number;
  status: OfferStatus;
  expiresAt: IsoDateString;
  createdAt: IsoDateString;
  decidedAt: IsoDateString | null;
}

/** `GET /api/users/me/offers` */
export interface ApiOffer {
  offer: ApiOfferDetails;
  auction: ApiAuctionSummary | null;
}

/** `GET /api/seller/second-chances` — one unpaid lot of the seller's. */
export interface ApiSecondChance {
  auctionId: string;
  runnerUpBid: number | null;
  canOffer: boolean;
  offer: ApiOfferDetails | null;
}

/** `ReviewView` */
export interface ApiReview {
  id: string;
  orderId: string;
  auctionId: string;
  rating: number;
  comment?: string | null;
  packaging?: number | null;
  accuracy?: number | null;
  speed?: number | null;
  reviewer: string;
  lotName?: string | null;
  createdAt: IsoDateString;
}

/** `GET /api/sellers/{id}/reviews` */
export interface ApiSellerReviews {
  average?: number | string | null;
  reviewCount: number;
  sales: number;
  aspects?: { packaging?: number | string | null; accuracy?: number | string | null; speed?: number | string | null };
  reviews: ApiPage<ApiReview>;
}

export interface ApiOrder {
  order: {
    id: string;
    auctionId: string;
    buyerId: string;
    sellerId: string;
    paymentId: string | null;
    amount: number;
    status: OrderStatus;
    createdAt: IsoDateString;
    updatedAt: IsoDateString;
    shipping?: { recipient: string; phone: string; address: string } | null;
    carrier?: string | null;
    trackingNumber?: string | null;
    shippedAt?: IsoDateString | null;
    completedAt?: IsoDateString | null;
    autoCompleted?: boolean;
    dispute?: OrderDispute | null;
    shippingStatus?: string | null;
    deliveredAt?: IsoDateString | null;
    ghnBooked?: boolean;
    ghnAddress?: boolean;
  };
  payment: ApiPaymentDetails | null;
  auction: ApiAuctionSummary | null;
  review?: ApiReview | null;
  /** EN: Only in the seller's and the admin's lists. / VI: Chỉ có trong danh sách của người bán và admin. */
  buyer?: {
    review: { rating: number; comment: string | null; createdAt: IsoDateString } | null;
    record: { completed: number; unpaidWins: number; averageRating: number | string | null; ratings: number } | null;
  } | null;
}

export interface ApiProduct {
  id: string;
  sellerId: string;
  category: ApiCategory | null;
  name: string;
  description: string;
  condition: ProductCondition;
  status: ProductStatus;
  parcel?: Parcel | null;
  items?: { name: string; quantity: number }[];
  videoUrl?: string | null;
  spinView?: boolean;
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

export interface ApiAuditLog {
  id: string;
  userId: string | null;
  actorDisplayName: string;
  action: AuditAction;
  entityType: string;
  entityId: string;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string;
  createdAt: IsoDateString;
}

/** EN: Messages on /topic/auctions/{id}. / VI: Bản tin trên /topic/auctions/{id}. */
export type ApiAuctionMessage =
  | {
      type: "BID_PLACED";
      auctionId: string;
      currentPrice: number;
      bidCount: number;
      minimumNextBid: number;
      bidder: string;
      createdAt: IsoDateString;
      reserveMet?: boolean | null;
    }
  | {
      type: "AUCTION_STARTED" | "AUCTION_ENDED" | "AUCTION_EXTENDED" | "AUCTION_REMOVED";
      auctionId: string;
      status: AuctionStatus;
      startTime: IsoDateString;
      endTime: IsoDateString;
      sold?: boolean;
      serverTime: IsoDateString;
    }
  | { type: "VIEWER_COUNT"; auctionId: string; viewerCount: number }
  | {
      type: "BID_RETRACTED";
      auctionId: string;
      currentPrice: number;
      bidCount: number;
      minimumNextBid: number;
      reserveMet?: boolean | null;
      serverTime: IsoDateString;
    };

/** EN: `/api/users/me/address`; empty parts arrive as null. / VI: Các phần trống tới dưới dạng null. */
export interface ApiShippingAddress {
  recipient: string;
  phone: string;
  line: string;
  ward: string | null;
  district: string | null;
  province: string;
  provinceId?: number | null;
  districtId?: number | null;
  wardCode?: string | null;
}
