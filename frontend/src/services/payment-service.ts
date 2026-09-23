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
 * Settlement (spec §16, §17).
 *
 * Payment is deliberately a demo switch: the winner picks success or failure,
 * so the whole post-auction flow can be reviewed without a payment provider.
 * The shape mirrors `GET /api/payments/{id}` and `POST /api/payments/{id}/pay`
 * so swapping the mock for the real client changes nothing above this layer.
 */

export interface PaymentEntry {
  payment: Payment;
  order: Order | null;
  auction: AuctionSummary | null;
}

/**
 * A payment window closes on its own (spec §17). The server would expire it on
 * a schedule; here it is derived on read, which produces the same answer for
 * anyone looking at the ledger.
 */
function expireIfElapsed(payment: Payment): Payment {
  if (payment.status !== "PENDING") return payment;
  if (new Date(payment.expiredAt).getTime() > Date.now()) return payment;

  payment.status = "EXPIRED";
  payment.updatedAt = new Date().toISOString();

  const order = db.orders.find((item) => item.paymentId === payment.id);
  if (order && order.status === "PENDING_PAYMENT") {
    order.status = "CANCELLED";
    order.updatedAt = payment.updatedAt;
  }

  return payment;
}

function toEntry(payment: Payment): PaymentEntry {
  const auction = findAuction(payment.auctionId);

  return {
    payment: expireIfElapsed(payment),
    order: db.orders.find((item) => item.paymentId === payment.id) ?? null,
    auction: auction ? toAuctionSummary(auction) : null,
  };
}

/** Everything this buyer owes or has settled, newest first. */
export async function listPayments(): Promise<PaymentEntry[]> {
  await delay();
  reconcileAuctionStatuses();

  return db.payments
    .filter((payment) => payment.userId === CURRENT_USER_ID)
    .map(toEntry)
    .sort(
      (a, b) =>
        new Date(b.payment.createdAt).getTime() -
        new Date(a.payment.createdAt).getTime(),
    );
}

/** `GET /api/payments/{id}` */
export async function getPayment(
  paymentId: string,
): Promise<PaymentEntry | null> {
  await delay();
  reconcileAuctionStatuses();

  const payment = db.payments.find(
    (item) => item.id === paymentId && item.userId === CURRENT_USER_ID,
  );

  return payment ? toEntry(payment) : null;
}

/**
 * `POST /api/payments/{id}/pay`
 *
 * On success the order moves to PAID and the auction to COMPLETED, matching
 * the server-side flow in spec §17.
 */
export async function payPayment(
  paymentId: string,
  outcome: "SUCCESS" | "FAILED",
): Promise<{ ok: boolean }> {
  await delay(900);

  const payment = db.payments.find((item) => item.id === paymentId);
  if (!payment) return { ok: false };

  // An elapsed window cannot be paid, whichever outcome the demo asks for.
  if (expireIfElapsed(payment).status === "EXPIRED") return { ok: false };

  const timestamp = new Date().toISOString();
  payment.status = outcome;
  payment.updatedAt = timestamp;

  const order = db.orders.find((item) => item.paymentId === paymentId);

  if (outcome === "SUCCESS") {
    if (order) {
      order.status = "PAID";
      order.updatedAt = timestamp;
    }

    const auction = findAuction(payment.auctionId);
    if (auction) {
      auction.status = "COMPLETED";
      auction.updatedAt = timestamp;
    }
  }

  return { ok: outcome === "SUCCESS" };
}
