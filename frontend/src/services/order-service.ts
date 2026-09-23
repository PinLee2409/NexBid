"use client";

import type { AuctionSummary, Order, Payment } from "@/types";

import {
  CURRENT_USER_ID,
  db,
  delay,
  findAuction,
  reconcileAuctionStatuses,
  toAuctionSummary,
} from "./mock/db";

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

/** `GET /api/orders` */
export async function listOrders(): Promise<OrderEntry[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.orders
    .filter((order) => order.buyerId === CURRENT_USER_ID)
    .map((order) => {
      const auction = findAuction(order.auctionId);
      return {
        order,
        payment: db.payments.find((item) => item.id === order.paymentId) ?? null,
        auction: auction ? toAuctionSummary(auction) : null,
      };
    })
    .sort(
      (a, b) =>
        new Date(b.order.createdAt).getTime() -
        new Date(a.order.createdAt).getTime(),
    );
}

export async function getOrder(orderId: string): Promise<OrderEntry | null> {
  const orders = await listOrders();
  return orders.find((entry) => entry.order.id === orderId) ?? null;
}
