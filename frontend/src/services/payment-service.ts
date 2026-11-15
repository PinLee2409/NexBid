"use client";

import type { AuctionSummary, Order, Payment } from "@/types";

import type { ApiOrder, ApiPayment } from "./api/dto";
import { api } from "./api/http";
import { toOrder, toPayment, toSummary } from "./api/mappers";

/**
 * Settlement (spec §16, §17).
 *
 * Payment goes through VNPay: the server signs a checkout link, the buyer pays
 * on VNPay's page, and VNPay reports back to the server (IPN) and through the
 * buyer's browser on /payments/vnpay-return. The server owns the deadline: an
 * elapsed window comes back EXPIRED.
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

/** EN: Goods, the shipping fixed for the buyer's address, and what VNPay will charge. / VI: Tiền hàng, phí ship chốt cho địa chỉ người mua, và số VNPay sẽ thu. */
export interface ShippingQuote {
  amount: number;
  shippingFee: number;
  source: "GHN" | "FLAT" | "SELLER";
  total: number;
  /** EN: Taken off the shipping by a voucher. / VI: Phần voucher trừ vào phí ship. */
  shippingDiscount: number;
  promoCode: string | null;
}

function toQuote(quote: ShippingQuote): ShippingQuote {
  return {
    ...quote,
    amount: Number(quote.amount),
    shippingFee: Number(quote.shippingFee),
    total: Number(quote.total),
    shippingDiscount: Number(quote.shippingDiscount ?? 0),
    promoCode: quote.promoCode ?? null,
  };
}

/** `POST /api/payments/{id}/promo` — applies a shipping voucher, or removes it with an empty code. */
export async function applyPromo(paymentId: string, code: string): Promise<ShippingQuote> {
  return toQuote(await api<ShippingQuote>(`/api/payments/${paymentId}/promo`, { method: "POST", body: { code } }));
}

/**
 * `POST /api/payments/{id}/shipping-quote` — fixes the shipping to the buyer's
 * current address (GHN when both addresses are on GHN, otherwise the flat fee)
 * and returns the total VNPay will charge. Kept until the address changes.
 */
export async function quoteShipping(paymentId: string): Promise<ShippingQuote> {
  return toQuote(await api<ShippingQuote>(`/api/payments/${paymentId}/shipping-quote`, { method: "POST" }));
}

/**
 * `POST /api/payments/{id}/vnpay` — the signed link to VNPay's payment page, in
 * the reader's language. The caller sends the browser there.
 */
export async function startVnpayCheckout(paymentId: string, locale: string): Promise<string> {
  const { paymentUrl } = await api<{ paymentUrl: string }>(`/api/payments/${paymentId}/vnpay`, {
    method: "POST",
    body: { locale },
  });
  return paymentUrl;
}

/**
 * `GET /api/payments/vnpay/return` — hands VNPay's signed reply, exactly as it
 * arrived in the address bar, to the server, which checks it and settles the
 * attempt (idempotently, so the IPN may already have done so).
 */
export async function confirmVnpayReturn(query: string): Promise<Payment> {
  const view = await api<ApiPayment>(`/api/payments/vnpay/return?${query.replace(/^\?/, "")}`);
  return toPayment(view.payment);
}
