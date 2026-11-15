"use client";

import { api, ApiError } from "./api/http";
import { getToken } from "./api/token-store";

interface ReconciliationDay {
  day: string | null;
  charges: number;
  charged: number;
  applied: number;
  stray: number;
  refunded: number;
  strayRefunded: number;
  paidOut: number;
  /** EN: Successful charges neither applied nor owed back; should be 0. / VI: Lần thu thành công không áp vào lô cũng không chờ trả lại; phải bằng 0. */
  unmatched: number;
}

export interface Reconciliation {
  from: string;
  to: string;
  days: ReconciliationDay[];
  total: ReconciliationDay;
}

interface Share {
  name: string;
  sales: number;
  gross: number;
  fees: number;
}

export interface Revenue {
  months: { month: string; sales: number; gross: number; fees: number; shipping: number }[];
  categories: Share[];
  sellers: Share[];
}

const numbers = <T extends object>(row: T): T =>
  Object.fromEntries(Object.entries(row).map(([key, value]) => [key, typeof value === "string" && /^-?\d+(\.\d+)?$/.test(value) ? Number(value) : value])) as T;

/** `GET /api/admin/finance/reconciliation` */
export async function getReconciliation(from: string, to: string): Promise<Reconciliation> {
  const data = await api<Reconciliation>("/api/admin/finance/reconciliation", { query: { from, to } });
  return { ...data, days: data.days.map(numbers), total: numbers(data.total) };
}

/** `GET /api/admin/finance/revenue` */
export async function getRevenue(months = 12): Promise<Revenue> {
  const data = await api<Revenue>("/api/admin/finance/revenue", { query: { months } });
  return {
    months: data.months.map(numbers),
    categories: data.categories.map(numbers),
    sellers: data.sellers.map(numbers),
  };
}

/**
 * EN: Saves a CSV export; it needs the admin's token, so it is fetched rather than linked.
 * VI: Lưu một file CSV xuất ra; cần token của admin, nên được tải bằng fetch thay vì link.
 */
export async function downloadCsv(path: string, filename: string): Promise<void> {
  const token = getToken();
  const response = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!response.ok) throw new ApiError(response.status, "INTERNAL_ERROR", "The export could not be downloaded");
  const url = URL.createObjectURL(await response.blob());
  const link = Object.assign(document.createElement("a"), { href: url, download: filename });
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
