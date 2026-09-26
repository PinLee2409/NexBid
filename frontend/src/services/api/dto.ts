import type {
  AuctionStatus,
  AuditAction,
  IsoDateString,
  NotificationType,
  OrderStatus,
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
}

export interface ApiLogin {
  accessToken: string;
  expiresAt: IsoDateString;
  user: ApiUser;
}

export interface ApiCategory {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  status: "ACTIVE" | "INACTIVE";
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
  createdAt: IsoDateString;
  updatedAt: IsoDateString;
}

export interface ApiAuctionSummary {
  auction: ApiAuction;
  product: { id: string; name: string; coverImageUrl: string | null; condition: ProductCondition | null };
  category: ApiCategory | null;
  seller: { id: string; displayName: string };
}

export interface ApiImage {
  id: string;
  url: string;
  alt: string | null;
  sortOrder: number;
}

export interface ApiAuctionDetail {
  auction: ApiAuction;
  product: { id: string; name: string; description: string; condition: ProductCondition };
  category: ApiCategory | null;
  images: ApiImage[];
  seller: { id: string; displayName: string };
  minimumNextBid: number;
  openForBidding: boolean;
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

export type ApiBidStanding = "WINNING" | "OUTBID" | "WON" | "LOST";

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
}

export interface ApiPayment {
  payment: ApiPaymentDetails;
  auction: ApiAuctionSummary | null;
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
  };
  payment: ApiPaymentDetails | null;
  auction: ApiAuctionSummary | null;
}

export interface ApiProduct {
  id: string;
  sellerId: string;
  category: ApiCategory | null;
  name: string;
  description: string;
  condition: ProductCondition;
  status: ProductStatus;
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
    }
  | {
      type: "AUCTION_STARTED" | "AUCTION_ENDED" | "AUCTION_EXTENDED";
      auctionId: string;
      status: AuctionStatus;
      startTime: IsoDateString;
      endTime: IsoDateString;
      serverTime: IsoDateString;
    };
