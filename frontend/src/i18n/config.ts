/** Locales NexBid ships with. English is the source of truth for copy. */
export const LOCALES = ["en", "vi"] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "en";

export const LOCALE_COOKIE = "NEXBID_LOCALE";

export const LOCALE_LABELS: Record<Locale, { name: string; short: string }> = {
  en: { name: "English", short: "EN" },
  vi: { name: "Tiếng Việt", short: "VI" },
};

/**
 * Number and date formatting follows the locale, but prices stay in a single
 * currency — an auction has one price regardless of the reader's language.
 */
export const LOCALE_INTL: Record<Locale, string> = {
  en: "en-US",
  vi: "vi-VN",
};

/**
 * Times are shown in the reader's own zone. The browser reports it through this
 * cookie; until it has, the server renders in UTC.
 */
export const TIME_ZONE_COOKIE = "NEXBID_TZ";

export const DEFAULT_TIME_ZONE = "UTC";

export function isTimeZone(value: string | undefined): value is string {
  if (!value) return false;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value });
    return true;
  } catch {
    return false;
  }
}

export function isLocale(value: string | undefined): value is Locale {
  return value !== undefined && LOCALES.includes(value as Locale);
}
