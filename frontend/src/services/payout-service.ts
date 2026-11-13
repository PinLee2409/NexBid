"use client";

import type { AuctionSummary, IsoDateString } from "@/types";

import type { ApiAuctionSummary } from "./api/dto";
import { api } from "./api/http";
import { toSummary } from "./api/mappers";
import type { BankAccount } from "./bank-account-service";

/**
 * EN: Seller payouts. Buyers pay NexBid; each completed order then owes its seller the price less the platform's fee,
 *     and an admin pays a seller's whole balance with one bank transfer.
 * VI: Chi trả cho người bán. Người mua trả tiền cho NexBid; mỗi đơn hoàn tất khiến sàn nợ người bán giá bán trừ phí sàn,
 *     và admin trả toàn bộ số dư của một người bán bằng một lần chuyển khoản.
 */

interface Earning {
  id: string;
  orderId: string;
  auctionId: string;
  gross: number;
  fee: number;
  net: number;
  /** EN: Set once the money was transferred. / VI: Có giá trị khi tiền đã được chuyển. */
  payoutId: string | null;
  createdAt: IsoDateString;
  auction: AuctionSummary | null;
}

export interface Payout {
  id: string;
  sellerId: string;
  amount: number;
  account: BankAccount;
  reference: string;
  createdAt: IsoDateString;
}

export interface SellerEarnings {
  balance: number;
  paidOut: number;
  feePercent: number;
  account: BankAccount | null;
  earnings: Earning[];
  payouts: Payout[];
}

export interface PayoutDue {
  sellerId: string;
  sellerName: string;
  sellerEmail: string;
  balance: number;
  orders: number;
  oldest: IsoDateString;
  account: BankAccount | null;
}

interface ApiEarning extends Omit<Earning, "auction"> {
  auction: ApiAuctionSummary | null;
}

function toPayout(payout: Payout): Payout {
  return { ...payout, amount: Number(payout.amount) };
}

/** `GET /api/seller/earnings` */
export async function getEarnings(): Promise<SellerEarnings> {
  const data = await api<Omit<SellerEarnings, "earnings"> & { earnings: ApiEarning[] }>("/api/seller/earnings");
  return {
    balance: Number(data.balance),
    paidOut: Number(data.paidOut),
    feePercent: Number(data.feePercent),
    account: data.account ?? null,
    earnings: data.earnings.map((earning) => ({
      ...earning,
      gross: Number(earning.gross),
      fee: Number(earning.fee),
      net: Number(earning.net),
      auction: earning.auction ? toSummary(earning.auction) : null,
    })),
    payouts: data.payouts.map(toPayout),
  };
}

/** `GET /api/admin/payouts/due` — sellers with money waiting, longest-waiting first. */
export async function listPayoutsDue(): Promise<PayoutDue[]> {
  const due = await api<PayoutDue[]>("/api/admin/payouts/due");
  return due.map((row) => ({ ...row, balance: Number(row.balance) }));
}

/** `GET /api/admin/payouts` — the latest transfers. */
export async function listRecentPayouts(): Promise<Payout[]> {
  return (await api<Payout[]>("/api/admin/payouts")).map(toPayout);
}

/** `POST /api/admin/payouts` — the admin made the transfer. */
export async function recordPayout(sellerId: string, reference: string): Promise<Payout> {
  return toPayout(await api<Payout>("/api/admin/payouts", { method: "POST", body: { sellerId, reference: reference.trim() } }));
}
