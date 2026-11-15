"use client";

import { api } from "./api/http";

/** EN: The signals behind a suspicion; see ShillSignals on the server. / VI: Các dấu hiệu dẫn tới nghi vấn; xem ShillSignals ở server. */
type ShillSignal = "SHARED_DEVICE" | "SHARED_NETWORK" | "MOSTLY_THIS_SELLER" | "NEVER_WINS" | "RUNNER_UP" | "NEW_ACCOUNT";

interface Party {
  id: string;
  name: string;
  email: string;
}

export interface ShillSuspect {
  seller: Party;
  bidder: Party;
  score: number;
  signals: ShillSignal[];
  bids: number;
  lots: number;
  wins: number;
  lastBid: string;
}

/** `GET /api/admin/shill-suspects` */
export async function listShillSuspects(): Promise<ShillSuspect[]> {
  return api<ShillSuspect[]>("/api/admin/shill-suspects");
}

/** `POST /api/admin/shill-suspects/clear` — the pair stays hidden until the bidder bids on that seller again. */
export async function clearShillPair(sellerId: string, bidderId: string, note: string): Promise<void> {
  await api("/api/admin/shill-suspects/clear", { method: "POST", body: { sellerId, bidderId, note: note.trim() || null } });
}
