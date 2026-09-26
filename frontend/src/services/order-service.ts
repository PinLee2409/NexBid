"use client";

import type { AuctionSummary, Order, Payment } from "@/types";

import type { ApiOrder } from "./api/dto";
import { ApiError, api } from "./api/http";
import { toOrder, toPayment, toSummary } from "./api/mappers";

/**
 * Orders (spec §16).
 *
 * Settlement itself lives in `payment-service`, because the spec puts it on
 * `/api/payments/{id}/pay` — the order is the record, the payment is the act.
 */

export interface OrderEntry {
  order: Order;
  payment: Payment | null;
  auction: AuctionSummary | null;
}

function toEntry(view: ApiOrder): OrderEntry {
  return {
    order: toOrder(view.order),
    payment: view.payment ? toPayment(view.payment) : null,
    auction: view.auction ? toSummary(view.auction) : null,
  };
}

/** `GET /api/users/me/orders` */
export async function listOrders(): Promise<OrderEntry[]> {
  const orders = await api<ApiOrder[]>("/api/users/me/orders");
  return orders.map(toEntry);
}

/** `GET /api/users/me/orders/{id}` */
export async function getOrder(orderId: string): Promise<OrderEntry | null> {
  try {
    return toEntry(await api<ApiOrder>(`/api/users/me/orders/${orderId}`));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}
