import { LOCALE_INTL, type Locale } from "@/i18n/config";

/**
 * EN: Display currency for the storefront: VND, the single currency the spec prices in (§3.2 leaves
 *     multi-currency out). The amount never changes with the reader's language, but how it is written does:
 *     `₫18,500,000` in English, `18.500.000 ₫` in Vietnamese. The locale is always passed in — client
 *     components get it from `useMoney()`, server components from `getLocale()` — because a module-level
 *     "current locale" would be shared between requests on the server.
 * VI: Tiền tệ hiển thị: VND, đơn vị duy nhất mà spec dùng (§3.2 không làm đa tiền tệ). Số tiền không đổi
 *     theo ngôn ngữ người đọc, nhưng cách viết thì có: `₫18,500,000` khi tiếng Anh, `18.500.000 ₫` khi tiếng
 *     Việt. Locale luôn được truyền vào — component client lấy từ `useMoney()`, component server lấy từ
 *     `getLocale()` — vì một "locale hiện tại" cấp module sẽ bị dùng chung giữa các request trên server.
 */
export const CURRENCY = {
  code: "VND",
  symbol: "₫",
} as const;

const cache = new Map<string, Intl.NumberFormat>();

function formatter(locale: Locale, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  const key = `${locale}:${JSON.stringify(options)}`;
  let found = cache.get(key);
  if (!found) {
    found = new Intl.NumberFormat(LOCALE_INTL[locale], options);
    cache.set(key, found);
  }
  return found;
}

/** `₫18,500,000` (en) · `18.500.000 ₫` (vi) — the canonical price rendering across the product. */
export function formatCurrency(amount: number, locale: Locale): string {
  return formatter(locale, { style: "currency", currency: CURRENCY.code, maximumFractionDigits: 0 }).format(amount);
}

/** `₫18.5M` (en) · `18,5 Tr ₫` (vi) — for dense surfaces such as dashboard stat tiles. */
export function formatCompactCurrency(amount: number, locale: Locale): string {
  return formatter(locale, {
    style: "currency",
    currency: CURRENCY.code,
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(amount);
}

/** `18,500,000` (en) · `18.500.000` (vi) — the figure alone, for layouts that place the symbol themselves. */
export function formatAmount(amount: number, locale: Locale): string {
  return formatter(locale, { maximumFractionDigits: 0 }).format(amount);
}

/** EN: Whether the locale writes the symbol after the figure (`18.500.000 ₫`). / VI: Locale có viết ký hiệu sau con số không. */
export function currencySymbolAfter(locale: Locale): boolean {
  const parts = formatter(locale, { style: "currency", currency: CURRENCY.code, maximumFractionDigits: 0 }).formatToParts(1);
  return parts.findIndex((part) => part.type === "currency") > parts.findIndex((part) => part.type === "integer");
}

/** `1,284` viewers → `1.3K` (en) · `1,3 N` (vi). Keeps live counters from wrapping. */
export function formatCompactNumber(value: number, locale: Locale): string {
  if (value < 1000) return String(value);
  return formatter(locale, { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

/**
 * Parses user input like `19,000,000`, `₫19,000,000` or `19.000.000` back
 * into a number. VND has no minor unit, so every separator is grouping,
 * whichever locale typed it.
 */
export function parseCurrencyInput(value: string): number | null {
  const normalized = value.replace(/[^0-9]/g, "");
  if (normalized === "") return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

export interface Duration {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  totalMs: number;
}

/** Splits a millisecond remainder into calendar-free countdown parts. */
export function toDuration(totalMs: number): Duration {
  const clamped = Math.max(0, totalMs);
  const totalSeconds = Math.floor(clamped / 1000);

  return {
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
    totalMs: clamped,
  };
}

export function padTwo(value: number): string {
  return String(value).padStart(2, "0");
}

/**
 * Splits an ISO timestamp into the `YYYY-MM-DD` and `HH:mm` halves the seller
 * scheduling form edits, and recombines them.
 */
export function toDateAndTimeParts(iso: string): { date: string; time: string } {
  const value = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");

  return {
    date: `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`,
    time: `${pad(value.getHours())}:${pad(value.getMinutes())}`,
  };
}

export function fromDateAndTimeParts(date: string, time: string): string | null {
  if (!date || !time) return null;
  const parsed = new Date(`${date}T${time}`);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}
