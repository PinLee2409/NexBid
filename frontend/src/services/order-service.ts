"use client";

import type { AuctionSummary, DisputeReason, Order, Payment, SellerReview } from "@/types";

import type { ApiOrder, ApiReview } from "./api/dto";
import { api } from "./api/http";
import { toOrder, toPayment, toReview, toSummary } from "./api/mappers";

/**
 * Orders (spec §16).
 *
 * Settlement itself lives in `payment-service`, because the spec puts it on
 * `/api/payments/{id}/vnpay` — the order is the record, the payment is the act.
 */

export interface OrderEntry {
  order: Order;
  payment: Payment | null;
  auction: AuctionSummary | null;
  /** EN: The buyer's review of this sale, once written. / VI: Đánh giá của người mua cho giao dịch này, khi đã viết. */
  review: SellerReview | null;
  /** EN: For sellers and admins: their rating of the buyer and the buyer's record. / VI: Cho người bán và admin: đánh giá người mua và hồ sơ của họ. */
  buyer: BuyerSide | null;
}

/** EN: The seller's rating of this buyer, and the buyer's record across all sellers. / VI: Đánh giá của người bán về người mua, và hồ sơ của người mua trên mọi người bán. */
interface BuyerSide {
  review: { rating: number; comment: string | null; createdAt: string } | null;
  record: { completed: number; unpaidWins: number; averageRating: number | null; ratings: number } | null;
}

export function toEntry(view: ApiOrder): OrderEntry {
  return {
    order: toOrder(view.order),
    payment: view.payment ? toPayment(view.payment) : null,
    auction: view.auction ? toSummary(view.auction) : null,
    review: view.review ? toReview(view.review) : null,
    buyer: view.buyer
      ? {
          review: view.buyer.review ?? null,
          record: view.buyer.record
            ? {
                ...view.buyer.record,
                averageRating:
                  view.buyer.record.averageRating === null ? null : Number(view.buyer.record.averageRating),
              }
            : null,
        }
      : null,
  };
}

/** `GET /api/users/me/orders` */
export async function listOrders(): Promise<OrderEntry[]> {
  const orders = await api<ApiOrder[]>("/api/users/me/orders");
  return orders.map(toEntry);
}

/** `POST /api/users/me/orders/{id}/received` — the buyer confirms the goods arrived. */
export async function confirmReceipt(orderId: string): Promise<OrderEntry> {
  return toEntry(await api<ApiOrder>(`/api/users/me/orders/${orderId}/received`, { method: "POST" }));
}

/**
 * EN: `POST /api/users/me/orders/{id}/dispute` — the buyer reports a problem; an admin decides.
 * VI: `POST /api/users/me/orders/{id}/dispute` — người mua báo có vấn đề; admin quyết định.
 */
export async function openDispute(orderId: string, reason: DisputeReason, details: string): Promise<OrderEntry> {
  return toEntry(
    await api<ApiOrder>(`/api/users/me/orders/${orderId}/dispute`, {
      method: "POST",
      body: { reason, details: details.trim() },
    }),
  );
}

/**
 * EN: `POST /api/users/me/orders/{id}/review` — once the goods arrived, once per order.
 * VI: `POST /api/users/me/orders/{id}/review` — khi đã nhận hàng, mỗi đơn một lần.
 */
export async function reviewOrder(orderId: string, rating: number, comment: string): Promise<SellerReview> {
  return toReview(
    await api<ApiReview>(`/api/users/me/orders/${orderId}/review`, {
      method: "POST",
      body: { rating, comment: comment.trim() || undefined },
    }),
  );
}
