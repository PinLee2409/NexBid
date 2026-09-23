/**
 * Display currency for the storefront.
 *
 * Prices are rendered the same way in every locale — an auction has one price,
 * and a bidder comparing lots should not see the number reshaped by their UI
 * language. Dates and relative times *do* follow the locale; those go through
 * next-intl's `useFormatter()` rather than this module.
 */
export const CURRENCY = {
  code: "USD",
  locale: "en-US",
  symbol: "$",
} as const;

const currencyFormatter = new Intl.NumberFormat(CURRENCY.locale, {
  style: "currency",
  currency: CURRENCY.code,
  maximumFractionDigits: 0,
});

const compactCurrencyFormatter = new Intl.NumberFormat(CURRENCY.locale, {
  style: "currency",
  currency: CURRENCY.code,
  notation: "compact",
  maximumFractionDigits: 1,
});

const numberFormatter = new Intl.NumberFormat(CURRENCY.locale, {
  maximumFractionDigits: 0,
});

/** `$1,850` — the canonical price rendering across the product. */
export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

/** `$1.9K` — for dense surfaces such as dashboard stat tiles. */
export function formatCompactCurrency(amount: number): string {
  return compactCurrencyFormatter.format(amount);
}

/** `1,850` — bare number, used inside inputs where the symbol sits outside. */
export function formatNumber(value: number): string {
  return numberFormatter.format(value);
}

/** `1,284` viewers → `1.3K`. Keeps live counters from wrapping. */
export function formatCompactNumber(value: number): string {
  if (value < 1000) return String(value);
  return new Intl.NumberFormat(CURRENCY.locale, {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

/** Parses user input like `1,900` or `$1,900` back into a number. */
export function parseCurrencyInput(value: string): number | null {
  const normalized = value.replace(/[^0-9.]/g, "");
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
