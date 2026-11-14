import type { IsoDateString } from "@/types";

import { api } from "./api/http";

/**
 * EN: The email outbox for admins: counts per state, the latest emails in one state, and resending a failed one.
 * VI: Hàng chờ email cho admin: số lượng theo trạng thái, các email mới nhất của một trạng thái, và gửi lại email hỏng.
 */
export type EmailStatus = "FAILED" | "PENDING" | "SENT" | "SKIPPED";

export interface OutboxEmail {
  id: string;
  userId: string | null;
  toEmail: string;
  kind: string;
  subject: string;
  status: EmailStatus;
  attempts: number;
  lastError: string | null;
  createdAt: IsoDateString;
  sentAt: IsoDateString | null;
}

export interface EmailQueue {
  counts: Record<EmailStatus, number>;
  items: OutboxEmail[];
}

/** `GET /api/admin/emails?status=` */
export async function listEmails(status: EmailStatus): Promise<EmailQueue> {
  return api<EmailQueue>("/api/admin/emails", { query: { status } });
}

/** `POST /api/admin/emails/{id}/retry` — a failed email goes back in the queue. */
export async function retryEmail(id: string): Promise<void> {
  await api(`/api/admin/emails/${id}/retry`, { method: "POST" });
}
