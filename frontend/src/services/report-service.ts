import type { AuctionSummary, IsoDateString } from "@/types";

import type { ApiAuctionSummary } from "./api/dto";
import { api } from "./api/http";
import { toSummary } from "./api/mappers";

/**
 * EN: Reports about lots: anyone signed in but the seller reports a scheduled or live lot; admins dismiss the report or
 *     take the lot down.
 * VI: Báo cáo về lô: ai đã đăng nhập, trừ người bán, báo cáo một lô đang chờ mở hoặc đang mở; admin bỏ qua báo cáo hoặc
 *     gỡ lô.
 */
export const REPORT_REASONS = ["COUNTERFEIT", "NOT_AS_DESCRIBED", "PROHIBITED", "OTHER"] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];
export type ReportStatus = "OPEN" | "DISMISSED" | "REMOVED";

export interface LotReport {
  id: string;
  auctionId: string;
  /** EN: Masked, "lin***". / VI: Được che, "lin***". */
  reporter: string;
  reason: ReportReason;
  details: string;
  status: ReportStatus;
  note: string | null;
  createdAt: IsoDateString;
  resolvedAt: IsoDateString | null;
  auction: AuctionSummary | null;
}

interface ApiLotReport extends Omit<LotReport, "auction"> {
  auction: ApiAuctionSummary | null;
}

/** `POST /api/auctions/{id}/reports` */
export async function reportLot(auctionId: string, reason: ReportReason, details: string): Promise<void> {
  await api(`/api/auctions/${auctionId}/reports`, { method: "POST", body: { reason, details: details.trim() } });
}

/** `GET /api/admin/reports?status=` — open ones oldest first, decided ones newest first. */
export async function listReports(status: ReportStatus): Promise<LotReport[]> {
  return (await api<ApiLotReport[]>("/api/admin/reports", { query: { status } })).map((report) => ({
    ...report,
    note: report.note ?? null,
    resolvedAt: report.resolvedAt ?? null,
    auction: report.auction ? toSummary(report.auction) : null,
  }));
}

/** `POST /api/admin/reports/{id}/dismiss` — the lot stays. */
export async function dismissReport(id: string, note: string): Promise<void> {
  await api(`/api/admin/reports/${id}/dismiss`, { method: "POST", body: { note: note.trim() || null } });
}

/** `POST /api/admin/reports/{id}/take-down` — the lot is removed and every open report on it closes. */
export async function takeDownLot(reportId: string, reason: string): Promise<void> {
  await api(`/api/admin/reports/${reportId}/take-down`, { method: "POST", body: { reason: reason.trim() } });
}
