"use client";

import type { AuctionStatus } from "@/types";

import { api } from "./api/http";

/** EN: One of the seller's lots and how it is doing. / VI: Một lô của người bán và tình hình của nó. */
interface LotStats {
  auctionId: string;
  name: string;
  status: AuctionStatus;
  currentPrice: number;
  endTime: string;
  views: number;
  watchers: number;
  bidders: number;
  bids: number;
}

export interface SellerStats {
  views: number;
  watchers: number;
  bids: number;
  closed: number;
  sold: number;
  /** EN: Share of closed lots whose winner paid; null before any lot closed. / VI: Tỉ lệ lô đã đóng mà người thắng đã trả; null khi chưa lô nào đóng. */
  sellThrough: number | null;
  lots: LotStats[];
}

/** `GET /api/seller/auctions/stats` */
export async function getSellerStats(): Promise<SellerStats> {
  const data = await api<SellerStats>("/api/seller/auctions/stats");
  return { ...data, lots: data.lots.map((lot) => ({ ...lot, currentPrice: Number(lot.currentPrice) })) };
}
