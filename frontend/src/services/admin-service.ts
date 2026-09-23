"use client";

import type { AuctionSummary, AuditLog, User } from "@/types";

import {
  db,
  delay,
  findAuction,
  nextId,
  reconcileAuctionStatuses,
  toAuctionSummary,
} from "./mock/db";

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

export async function getAdminStats(): Promise<AdminStats> {
  await delay();
  reconcileAuctionStatuses();

  return {
    pendingApproval: db.auctions.filter(
      (auction) => auction.status === "PENDING_APPROVAL",
    ).length,
    activeAuctions: db.auctions.filter((auction) => auction.status === "ACTIVE")
      .length,
    totalUsers: db.users.length,
    blockedUsers: db.users.filter((user) => user.status === "BLOCKED").length,
  };
}

/** `GET /api/admin/auctions/pending` (and the approved/rejected tabs). */
export async function listAuctionsForReview(
  status: "PENDING_APPROVAL" | "SCHEDULED" | "REJECTED",
): Promise<AuctionSummary[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.auctions
    .filter((auction) =>
      status === "SCHEDULED"
        ? auction.status === "SCHEDULED" || auction.status === "ACTIVE"
        : auction.status === status,
    )
    .map(toAuctionSummary)
    .sort(
      (a, b) =>
        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    );
}

function recordAudit(entry: Omit<AuditLog, "id" | "createdAt">): void {
  db.auditLogs.unshift({
    ...entry,
    id: nextId("audit"),
    createdAt: new Date().toISOString(),
  });
}

/**
 * `POST /api/admin/auctions/{id}/approve`
 *
 * Spec §7.5: approving before the start time schedules the lot; approving
 * after it has passed opens bidding immediately.
 */
export async function approveAuction(
  auctionId: string,
): Promise<AuctionSummary | null> {
  await delay(600);

  const auction = findAuction(auctionId);
  if (!auction) return null;

  const previous = auction.status;
  const startMs = new Date(auction.startTime).getTime();
  auction.status = startMs <= Date.now() ? "ACTIVE" : "SCHEDULED";
  auction.rejectionReason = undefined;
  auction.updatedAt = new Date().toISOString();

  recordAudit({
    userId: "admin-root",
    actorDisplayName: "nora***",
    action: "AUCTION_APPROVED",
    entityType: "Auction",
    entityId: auctionId,
    oldValue: previous,
    newValue: auction.status,
    ipAddress: "10.4.18.22",
  });

  return toAuctionSummary(auction);
}

/** `POST /api/admin/auctions/{id}/reject` */
export async function rejectAuction(
  auctionId: string,
  reason: string,
): Promise<AuctionSummary | null> {
  await delay(600);

  const auction = findAuction(auctionId);
  if (!auction) return null;

  const previous = auction.status;
  auction.status = "REJECTED";
  auction.rejectionReason = reason.trim();
  auction.updatedAt = new Date().toISOString();

  recordAudit({
    userId: "admin-root",
    actorDisplayName: "nora***",
    action: "AUCTION_REJECTED",
    entityType: "Auction",
    entityId: auctionId,
    oldValue: previous,
    newValue: "REJECTED",
    ipAddress: "10.4.18.22",
  });

  return toAuctionSummary(auction);
}

/** `GET /api/admin/users` */
export async function listUsers(search = ""): Promise<User[]> {
  await delay();

  const needle = search.trim().toLowerCase();

  return db.users.filter((user) =>
    needle === ""
      ? true
      : `${user.fullName} ${user.email}`.toLowerCase().includes(needle),
  );
}

/** `PATCH /api/admin/users/{id}/block` */
export async function setUserBlocked(
  userId: string,
  blocked: boolean,
): Promise<User | null> {
  await delay(500);

  const user = db.users.find((item) => item.id === userId);
  if (!user) return null;

  const previous = user.status;
  user.status = blocked ? "BLOCKED" : "ACTIVE";

  if (blocked) {
    recordAudit({
      userId: "admin-root",
      actorDisplayName: "nora***",
      action: "USER_BLOCKED",
      entityType: "User",
      entityId: userId,
      oldValue: previous,
      newValue: user.status,
      ipAddress: "10.4.18.22",
    });
  }

  return { ...user };
}

/** `GET /api/admin/audit-logs` */
export async function listAuditLogs(): Promise<AuditLog[]> {
  await delay();
  return [...db.auditLogs].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );
}
