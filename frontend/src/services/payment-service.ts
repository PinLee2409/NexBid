"use client";

import type { AuctionSummary, Order, Payment } from "@/types";

import type { ApiOrder, ApiPayment } from "./api/dto";
import { ApiError, api } from "./api/http";
import { toOrder, toPayment, toSummary } from "./api/mappers";

/**
 * Settlement (spec §16, §17).
 *
 * Payment is deliberately a demo switch: the winner picks success or failure,
 * so the whole post-auction flow can be reviewed without a payment provider.
 * The server owns the deadline: an elapsed window comes back EXPIRED.
 */

export interface PaymentEntry {
  payment: Payment;
  order: Order | null;
  auction: AuctionSummary | null;
}

function toEntry(view: ApiPayment, orders: ApiOrder[]): PaymentEntry {
  const order = orders.find((item) => item.order.paymentId === view.payment.id);
  return {
    payment: toPayment(view.payment),
    order: order ? toOrder(order.order) : null,
    auction: view.auction ? toSummary(view.auction) : null,
  };
}

/** Everything this buyer owes or has settled, newest first. */
export async function listPayments(): Promise<PaymentEntry[]> {
  const [payments, orders] = await Promise.all([
    api<ApiPayment[]>("/api/users/me/payments"),
    api<ApiOrder[]>("/api/users/me/orders"),
  ]);
  return payments.map((payment) => toEntry(payment, orders));
}

/** `GET /api/payments/{id}` */
export async function getPayment(paymentId: string): Promise<PaymentEntry | null> {
  try {
    const [payment, orders] = await Promise.all([
      api<ApiPayment>(`/api/payments/${paymentId}`),
      api<ApiOrder[]>("/api/users/me/orders"),
    ]);
    return toEntry(payment, orders);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/**
 * `POST /api/payments/{id}/pay`
 *
 * On success the server moves the order to PAID and the auction to COMPLETED
 * (spec §17). A window that has run out is refused, whichever outcome is asked.
 */
export async function payPayment(
  paymentId: string,
  outcome: "SUCCESS" | "FAILED",
): Promise<{ ok: boolean }> {
  try {
    const result = await api<ApiPayment>(`/api/payments/${paymentId}/pay`, {
      method: "POST",
      body: { outcome },
    });
    return { ok: result.payment.status === "SUCCESS" };
  } catch {
    return { ok: false };
  }
}
