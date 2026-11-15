/**
 * EN: Carriers a seller ships with, and labels for the status words GHN reports.
 * VI: Các hãng người bán gửi hàng, và nhãn cho các trạng thái GHN báo về.
 */

export const GHN_CARRIER = "GHN";
export const OTHER_CARRIER = "__other__";
/** EN: The carriers most Vietnamese sellers use. / VI: Các hãng người bán ở Việt Nam hay dùng. */
export const CARRIERS = [GHN_CARRIER, "GHTK", "Viettel Post", "J&T Express", "VNPost", "Ninja Van"] as const;

const GHN_STATUSES = new Set([
  "ready_to_pick",
  "picking",
  "picked",
  "storing",
  "transporting",
  "sorting",
  "delivering",
  "delivered",
  "delivery_fail",
  "waiting_to_return",
  "return",
  "returned",
  "cancel",
  "exception",
  "damage",
  "lost",
]);

/**
 * EN: The translation key for a carrier's status word, or null for a word we have no label for (shown as GHN wrote it).
 * VI: Khoá dịch cho trạng thái của hãng, hoặc null khi chưa có nhãn (hiện nguyên chữ GHN gửi).
 */
export function carrierStatusKey(status: string): string | null {
  const word = status.trim().toLowerCase();
  if (word.startsWith("return_") || word === "returning") return "return";
  return GHN_STATUSES.has(word) ? word : null;
}
