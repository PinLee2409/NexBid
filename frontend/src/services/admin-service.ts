"use client";

import type { Auction, AuctionStatus, AuctionSummary, AuditLog, User } from "@/types";

import type { ApiAuction, ApiAuctionSummary, ApiAuditLog, ApiPage, ApiUser } from "./api/dto";
import { api } from "./api/http";
import { toAuction, toAuditLog, toSummary, toUser } from "./api/mappers";

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

/** `GET /api/admin/audit-logs`, newest first. */
export async function listAuditLogs(): Promise<AuditLog[]> {
  const page = await api<ApiPage<ApiAuditLog>>("/api/admin/audit-logs", { query: { size: 100 } });
  return page.items.map(toAuditLog);
}
