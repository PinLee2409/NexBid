"use client";

import type {
  Auction,
  AuctionStatus,
  AuctionSummary,
  AuditLog,
  Category,
  CategoryStatus,
  OrderDispute,
  OrderStatus,
  SellerApplication,
  SellerApplicationStatus,
  User,
} from "@/types";

import type {
  ApiAnalyticsReport,
  ApiAuction,
  ApiAuctionSummary,
  ApiAuditLog,
  ApiCategory,
  ApiOrder,
  ApiPage,
  ApiUser,
} from "./api/dto";
import { api } from "./api/http";
import { toAuction, toAuditLog, toCategory, toSummary, toUser } from "./api/mappers";
import type { BankAccount } from "./bank-account-service";
import { toEntry, type OrderEntry } from "./order-service";

/**
 * Admin APIs (spec §7.5, §21, §27).
 *
 * Approval is the console's reason to exist: nothing opens for bidding until
 * an admin has looked at it.
 */

export interface AdminStats {
  pendingApproval: number;
  activeAuctions: number;
  totalUsers: number;
  blockedUsers: number;
}

/** Counts only: each question is one list call asking for a single row. */
export async function getAdminStats(): Promise<AdminStats> {
  const count = async (path: string, query: Record<string, string | string[]>) =>
    (await api<ApiPage<unknown>>(path, { query: { ...query, size: 1 } })).totalItems;

  const [pendingApproval, activeAuctions, totalUsers, blockedUsers] = await Promise.all([
    count("/api/admin/auctions", { status: ["PENDING_APPROVAL"] }),
    count("/api/auctions", { status: ["ACTIVE"] }),
    count("/api/admin/users", {}),
    count("/api/admin/users", { status: "BLOCKED" }),
  ]);
  return { pendingApproval, activeAuctions, totalUsers, blockedUsers };
}

/** `GET /api/admin/auctions?status=…` — the pending, approved and rejected tabs. */
export async function listAuctionsForReview(
  status: "PENDING_APPROVAL" | "SCHEDULED" | "REJECTED",
): Promise<AuctionSummary[]> {
  const statuses: AuctionStatus[] = status === "SCHEDULED" ? ["SCHEDULED", "ACTIVE"] : [status];
  const page = await api<ApiPage<ApiAuctionSummary>>("/api/admin/auctions", {
    query: { status: statuses, size: 100 },
  });
  return page.items.map((item) => toSummary(item));
}

/**
 * `POST /api/admin/auctions/{id}/approve`
 *
 * Spec §7.5: approving before the start time schedules the lot; approving
 * after it has passed opens bidding immediately.
 */
export async function approveAuction(auctionId: string): Promise<Auction> {
  return toAuction(await api<ApiAuction>(`/api/admin/auctions/${auctionId}/approve`, { method: "POST" }));
}

/** `POST /api/admin/auctions/{id}/reject` */
export async function rejectAuction(auctionId: string, reason: string): Promise<Auction> {
  return toAuction(
    await api<ApiAuction>(`/api/admin/auctions/${auctionId}/reject`, {
      method: "POST",
      body: { reason: reason.trim() },
    }),
  );
}

/** `GET /api/admin/users` */
export async function listUsers(search = ""): Promise<User[]> {
  const page = await api<ApiPage<ApiUser>>("/api/admin/users", {
    query: { search: search.trim() || undefined, size: 100 },
  });
  return page.items.map(toUser);
}

/** `PATCH /api/admin/users/{id}/block` */
export async function setUserBlocked(userId: string, blocked: boolean): Promise<User> {
  return toUser(
    await api<ApiUser>(`/api/admin/users/${userId}/block`, {
      method: "PATCH",
      body: { blocked },
    }),
  );
}

/** `GET /api/admin/orders?status=…` — every order when no status is given, most recently changed first. */
export async function listOrders(statuses: OrderStatus[] = []): Promise<OrderEntry[]> {
  const page = await api<ApiPage<ApiOrder>>("/api/admin/orders", { query: { status: statuses, size: 100 } });
  return page.items.map(toEntry);
}

/** `GET /api/admin/orders/{id}/refund-account` — where to transfer a refund; null when the buyer has not said. */
export async function getRefundAccount(orderId: string): Promise<BankAccount | null> {
  return (await api<BankAccount | null>(`/api/admin/orders/${orderId}/refund-account`)) ?? null;
}

/**
 * `POST /api/admin/orders/{id}/refund` — records a transfer the admin already made to the buyer: the order is
 * cancelled and the payment marked refunded.
 */
export async function refundOrder(orderId: string, reference: string): Promise<OrderEntry> {
  return toEntry(
    await api<ApiOrder>(`/api/admin/orders/${orderId}/refund`, { method: "POST", body: { reference: reference.trim() } }),
  );
}

export interface DisputeEntry {
  dispute: OrderDispute;
  entry: OrderEntry;
}

/** `GET /api/admin/disputes?status=…` — open disputes come oldest first. */
export async function listDisputes(status: OrderDispute["status"]): Promise<DisputeEntry[]> {
  const rows = await api<{ dispute: OrderDispute; entry: ApiOrder }[]>("/api/admin/disputes", { query: { status } });
  return rows.map((row) => ({ dispute: row.dispute, entry: toEntry(row.entry) }));
}

/**
 * `POST /api/admin/disputes/{id}/resolve` — refund the buyer (a transfer already made, with its bank reference), or let
 * the sale stand.
 */
export async function resolveDispute(
  disputeId: string,
  refund: boolean,
  note: string,
  reference?: string,
): Promise<OrderEntry> {
  return toEntry(
    await api<ApiOrder>(`/api/admin/disputes/${disputeId}/resolve`, {
      method: "POST",
      body: { refund, note: note.trim() || undefined, reference: refund ? reference?.trim() : undefined },
    }),
  );
}

/** `GET /api/admin/analytics?hours=…` — hourly totals from the Analytics Consumer, oldest hour first. */
export async function getActivity(hours = 24): Promise<ApiAnalyticsReport> {
  const report = await api<ApiAnalyticsReport>("/api/admin/analytics", { query: { hours } });
  // EN: Money arrives as JSON numbers already, but BigDecimal may come as a string. / VI: Tiền có thể tới dạng chuỗi từ BigDecimal.
  return {
    hours: report.hours.map((hour) => ({ ...hour, revenue: Number(hour.revenue) })),
    totals: { ...report.totals, revenue: Number(report.totals.revenue) },
  };
}

/** `GET /api/admin/audit-logs`, newest first. */
export async function listAuditLogs(): Promise<AuditLog[]> {
  const page = await api<ApiPage<ApiAuditLog>>("/api/admin/audit-logs", { query: { size: 100 } });
  return page.items.map(toAuditLog);
}

/**
 * EN: `GET /api/admin/seller-applications?status=…` — waiting requests oldest first, decided ones newest first.
 * VI: `GET /api/admin/seller-applications?status=…` — yêu cầu đang chờ cũ nhất trước, đã quyết định thì mới nhất trước.
 */
export async function listSellerApplications(status: SellerApplicationStatus): Promise<SellerApplication[]> {
  const page = await api<ApiPage<SellerApplication>>("/api/admin/seller-applications", {
    query: { status, size: 100 },
  });
  return page.items;
}

/** EN: Grants the SELLER role at once. / VI: Cấp vai trò SELLER ngay lập tức. */
export async function approveSellerApplication(id: string): Promise<SellerApplication> {
  return api<SellerApplication>(`/api/admin/seller-applications/${id}/approve`, { method: "POST" });
}

export async function rejectSellerApplication(id: string, reason: string): Promise<SellerApplication> {
  return api<SellerApplication>(`/api/admin/seller-applications/${id}/reject`, {
    method: "POST",
    body: { reason: reason.trim() },
  });
}

/** EN: What an admin may set on a category. / VI: Những gì admin được đặt cho một danh mục. */
export interface CategoryInput {
  name: string;
  /** EN: Blank lets the server derive it from the name. / VI: Để trống thì server tự sinh từ tên. */
  slug: string;
  description: string;
  imageUrl: string;
}

function toCategoryBody(input: CategoryInput) {
  const orNull = (value: string) => value.trim() || null;
  return {
    name: input.name.trim(),
    slug: orNull(input.slug),
    description: orNull(input.description),
    imageUrl: orNull(input.imageUrl),
  };
}

/** EN: `GET /api/admin/categories` — archived ones included, unlike the public list. / VI: Gồm cả mục đã lưu trữ. */
export async function listAllCategories(): Promise<Category[]> {
  const categories = await api<ApiCategory[]>("/api/admin/categories");
  return categories.map(toCategory);
}

/** `POST /api/admin/categories` */
export async function createCategory(input: CategoryInput): Promise<Category> {
  return toCategory(await api<ApiCategory>("/api/admin/categories", { method: "POST", body: toCategoryBody(input) }));
}

/** `PUT /api/admin/categories/{id}` */
export async function updateCategory(id: string, input: CategoryInput): Promise<Category> {
  return toCategory(
    await api<ApiCategory>(`/api/admin/categories/${id}`, { method: "PUT", body: toCategoryBody(input) }),
  );
}

/** EN: Archiving keeps the products but hides the category from browsing. / VI: Lưu trữ giữ sản phẩm nhưng ẩn danh mục khỏi trang duyệt. */
export async function setCategoryStatus(id: string, status: CategoryStatus): Promise<Category> {
  return toCategory(
    await api<ApiCategory>(`/api/admin/categories/${id}/status`, { method: "PUT", body: { status } }),
  );
}
