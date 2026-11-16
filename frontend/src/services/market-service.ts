import type { IsoDateString, ProductCondition, UserRole } from "@/types";

import { api } from "./api/http";

/**
 * EN: The calls behind search suggestions, past prices, offers, deposits, relisting, vacations, listing templates, staff
 *     roles and the conversion funnel.
 * VI: Các lệnh gọi cho gợi ý tìm kiếm, giá đã bán, đề nghị, tiền cọc, đăng lại, kỳ nghỉ, mẫu mô tả, vai trò nhân viên và
 *     phễu chuyển đổi.
 */

// ---- search / tìm kiếm

interface LotHint {
  auctionId: string;
  title: string;
  currentPrice: number;
  status: string;
  endTime: IsoDateString;
  coverImageUrl: string | null;
}

export interface Suggestions {
  query: string;
  lots: LotHint[];
  sellers: { id: string; name: string }[];
  /** EN: A near spelling when nothing matched exactly. / VI: Cách viết gần đúng khi không có gì khớp chính xác. */
  correction: string | null;
}

/** `GET /api/auctions/suggest?q=` — public. */
export async function suggest(query: string): Promise<Suggestions> {
  return api<Suggestions>("/api/auctions/suggest", { query: { q: query } });
}

interface PastSale {
  auctionId: string;
  title: string;
  price: number;
  endedAt: IsoDateString;
}

export interface PriceHistory {
  /** EN: SIMILAR or CATEGORY; null below three sales. / VI: SIMILAR hoặc CATEGORY; null khi dưới ba giao dịch. */
  basis: "SIMILAR" | "CATEGORY" | null;
  sales: PastSale[];
  low: number | null;
  median: number | null;
  high: number | null;
}

/** `GET /api/auctions/{id}/price-history` — public. */
export async function priceHistory(auctionId: string): Promise<PriceHistory> {
  return api<PriceHistory>(`/api/auctions/${auctionId}/price-history`);
}

// ---- offers / đề nghị

type OfferStatus =
  "PENDING" | "COUNTERED" | "ACCEPTED" | "DECLINED" | "WITHDRAWN" | "EXPIRED";

export interface LotOffer {
  id: string;
  auctionId: string;
  lotTitle: string;
  /** EN: Masked, "lin***". / VI: Đã che, "lin***". */
  buyer: string;
  amount: number;
  counterAmount: number | null;
  status: OfferStatus;
  message: string | null;
  expiresAt: IsoDateString;
  createdAt: IsoDateString;
  answeredAt: IsoDateString | null;
  mine: boolean;
  buyNowPrice: number | null;
}

export async function makeOffer(
  auctionId: string,
  amount: number,
  message: string,
): Promise<LotOffer> {
  return api<LotOffer>(`/api/auctions/${auctionId}/offers`, {
    method: "POST",
    body: { amount, message: message.trim() || null },
  });
}

export async function myOffersOnLot(auctionId: string): Promise<LotOffer[]> {
  return api<LotOffer[]>(`/api/auctions/${auctionId}/offers/mine`);
}

export async function myOffers(): Promise<LotOffer[]> {
  return api<LotOffer[]>("/api/users/me/lot-offers");
}

export async function answerCounter(
  offerId: string,
  action: "accept-counter" | "decline-counter" | "withdraw",
) {
  return api<LotOffer>(`/api/users/me/lot-offers/${offerId}/${action}`, {
    method: "POST",
  });
}

export async function sellerOffers(): Promise<LotOffer[]> {
  return api<LotOffer[]>("/api/seller/lot-offers");
}

export async function answerOffer(
  offerId: string,
  action: "accept" | "decline",
): Promise<LotOffer> {
  return api<LotOffer>(`/api/seller/lot-offers/${offerId}/${action}`, {
    method: "POST",
  });
}

export async function counterOffer(
  offerId: string,
  amount: number,
): Promise<LotOffer> {
  return api<LotOffer>(`/api/seller/lot-offers/${offerId}/counter`, {
    method: "POST",
    body: { amount },
  });
}

// ---- deposits / tiền cọc

export interface DepositStatus {
  /** EN: Null when the lot asks for none. / VI: Null khi lô không yêu cầu cọc. */
  amount: number | null;
  held: boolean;
}

type DepositState =
  "PENDING" | "FAILED" | "PAID" | "REFUND_DUE" | "REFUNDED" | "FORFEITED";

export interface Deposit {
  id: string;
  auctionId: string;
  lotTitle: string;
  amount: number;
  status: DepositState;
  createdAt: IsoDateString;
  paidAt: IsoDateString | null;
  settledAt: IsoDateString | null;
  reference: string | null;
}

export interface DepositDue {
  deposit: Deposit;
  userId: string;
  userName: string;
  account: {
    bankName: string;
    accountNumber: string;
    accountHolder: string;
  } | null;
}

export async function depositStatus(auctionId: string): Promise<DepositStatus> {
  return api<DepositStatus>(`/api/auctions/${auctionId}/deposit`);
}

/** EN: Returns VNPay's page to send the bidder to. / VI: Trả về trang VNPay để đưa người trả giá tới. */
export async function startDeposit(
  auctionId: string,
  locale: string,
): Promise<string> {
  const { paymentUrl } = await api<{ paymentUrl: string }>(
    `/api/auctions/${auctionId}/deposit/vnpay`,
    {
      method: "POST",
      body: { locale },
    },
  );
  return paymentUrl;
}

/** EN: The bidder back from VNPay with a deposit reference (it starts with D). / VI: Người trả giá quay về từ VNPay với mã cọc (bắt đầu bằng D). */
export async function confirmDepositReturn(
  params: Record<string, string>,
): Promise<Deposit> {
  return api<Deposit>("/api/payments/deposits/vnpay/return", { query: params });
}

export async function myDeposits(): Promise<Deposit[]> {
  return api<Deposit[]>("/api/users/me/deposits");
}

export async function depositsDue(): Promise<DepositDue[]> {
  return api<DepositDue[]>("/api/admin/deposits");
}

export async function markDepositRefunded(
  depositId: string,
  reference: string,
): Promise<Deposit> {
  return api<Deposit>(`/api/admin/deposits/${depositId}/refund`, {
    method: "POST",
    body: { reference },
  });
}

// ---- seller tools / công cụ người bán

/** `POST /api/seller/auctions/{id}/relist` — a new draft, {@code dropPercent} cheaper. */
export async function relistLot(
  auctionId: string,
  dropPercent: number,
): Promise<{ id: string }> {
  return api<{ id: string }>(`/api/seller/auctions/${auctionId}/relist`, {
    method: "POST",
    body: { dropPercent },
  });
}

export interface Vacation {
  until: IsoDateString | null;
  note: string | null;
}

export async function getVacation(): Promise<Vacation> {
  return api<Vacation>("/api/seller/vacation");
}

export async function setVacation(
  until: string | null,
  note: string,
): Promise<Vacation> {
  return api<Vacation>("/api/seller/vacation", {
    method: "PUT",
    body: { until, note: note.trim() || null },
  });
}

export interface ListingTemplate {
  id: string;
  name: string;
  description: string;
  categoryId: string | null;
  condition: ProductCondition | null;
  parcel: {
    weightGrams: number;
    lengthCm: number;
    widthCm: number;
    heightCm: number;
  } | null;
  updatedAt: IsoDateString;
}

export type TemplateInput = Omit<ListingTemplate, "id" | "updatedAt">;

export async function listTemplates(): Promise<ListingTemplate[]> {
  return api<ListingTemplate[]>("/api/seller/templates");
}

export async function saveTemplate(
  input: TemplateInput,
  id?: string,
): Promise<ListingTemplate> {
  return api<ListingTemplate>(
    id ? `/api/seller/templates/${id}` : "/api/seller/templates",
    {
      method: id ? "PUT" : "POST",
      body: input,
    },
  );
}

export async function deleteTemplate(id: string): Promise<void> {
  await api(`/api/seller/templates/${id}`, { method: "DELETE" });
}

// ---- admin / quản trị

export interface StaffMember {
  id: string;
  fullName: string;
  email: string;
  roles: UserRole[];
  status: string;
}

export async function listStaff(): Promise<StaffMember[]> {
  return api<StaffMember[]>("/api/admin/staff");
}

export async function addStaff(
  email: string,
  roles: UserRole[],
): Promise<StaffMember> {
  return api<StaffMember>("/api/admin/staff", {
    method: "POST",
    body: { email: email.trim(), roles },
  });
}

export async function setStaffRoles(
  userId: string,
  roles: UserRole[],
): Promise<StaffMember> {
  return api<StaffMember>(`/api/admin/staff/${userId}`, {
    method: "PUT",
    body: { roles },
  });
}

export interface FunnelStep {
  slug: string | null;
  name: string | null;
  closed: number;
  viewed: number;
  bid: number;
  won: number;
  paid: number;
  shipped: number;
  completed: number;
}

export interface Funnel {
  from: IsoDateString;
  to: IsoDateString;
  total: FunnelStep;
  categories: FunnelStep[];
}

export async function getFunnel(days: number): Promise<Funnel> {
  return api<Funnel>("/api/admin/analytics/funnel", { query: { days } });
}

// ---- seller coupons / mã giảm giá của người bán

export interface SellerCoupon {
  id: string;
  code: string;
  percentOff: number | null;
  amountOff: number | null;
  minPrice: number | null;
  followersOnly: boolean;
  maxUses: number;
  used: number;
  expiresAt: IsoDateString | null;
  active: boolean;
  createdAt: IsoDateString;
}

export interface CouponInput {
  code: string;
  percentOff: number | null;
  amountOff: number | null;
  minPrice: number | null;
  followersOnly: boolean;
  maxUses: number;
  expiresAt: string | null;
}

export async function listCoupons(): Promise<SellerCoupon[]> {
  return api<SellerCoupon[]>("/api/seller/coupons");
}

export async function createCoupon(input: CouponInput): Promise<SellerCoupon> {
  return api<SellerCoupon>("/api/seller/coupons", { method: "POST", body: input });
}

export async function setCouponActive(code: string, active: boolean): Promise<SellerCoupon> {
  return api<SellerCoupon>(`/api/seller/coupons/${encodeURIComponent(code)}/active`, { method: "POST", body: { active } });
}

// ---- quick replies / trả lời nhanh

export interface QuickReply {
  id: string;
  body: string;
  createdAt: IsoDateString;
}

export async function listQuickReplies(): Promise<QuickReply[]> {
  return api<QuickReply[]>("/api/users/me/quick-replies");
}

export async function addQuickReply(body: string): Promise<QuickReply> {
  return api<QuickReply>("/api/users/me/quick-replies", { method: "POST", body: { body: body.trim() } });
}

export async function deleteQuickReply(id: string): Promise<void> {
  await api(`/api/users/me/quick-replies/${id}`, { method: "DELETE" });
}

// ---- media review / duyệt ảnh và video

export interface MediaItem {
  kind: "IMAGE" | "VIDEO";
  id: string;
  productId: string;
  productName: string;
  sellerId: string;
  sellerName: string;
  url: string;
  uploadedAt: IsoDateString;
  reported: boolean;
}

export async function mediaQueue(): Promise<MediaItem[]> {
  return api<MediaItem[]>("/api/admin/media");
}

export async function keepMedia(item: MediaItem): Promise<void> {
  await api(`/api/admin/media/${item.kind}/${item.id}/keep`, { method: "POST" });
}

export async function removeMedia(item: MediaItem, reason: string): Promise<void> {
  await api(`/api/admin/media/${item.kind}/${item.id}/remove`, { method: "POST", body: { reason: reason.trim() || null } });
}
