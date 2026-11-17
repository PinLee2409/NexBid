"use client";

import { api } from "./api/http";

/** EN: A shipping voucher as admins see it. / VI: Một voucher phí ship như admin thấy. */
export interface PromoCode {
  code: string;
  amount: number;
  maxUses: number;
  used: number;
  expiresAt: string | null;
  active: boolean;
  note: string | null;
  createdAt: string;
}

const toPromo = (promo: PromoCode): PromoCode => ({ ...promo, amount: Number(promo.amount) });

/** `GET /api/admin/promo-codes` */
export async function listPromoCodes(): Promise<PromoCode[]> {
  return (await api<PromoCode[]>("/api/admin/promo-codes")).map(toPromo);
}

/** `POST /api/admin/promo-codes` */
export async function createPromoCode(input: {
  code: string;
  amount: number;
  maxUses: number;
  expiresAt: string | null;
  note: string;
}): Promise<PromoCode> {
  return toPromo(await api<PromoCode>("/api/admin/promo-codes", { method: "POST", body: input }));
}

/** `POST /api/admin/promo-codes/{code}/active` */
export async function setPromoActive(code: string, active: boolean): Promise<void> {
  await api(`/api/admin/promo-codes/${encodeURIComponent(code)}/active`, { method: "POST", body: { active } });
}
