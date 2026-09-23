"use server";

import { cookies } from "next/headers";

import { DEFAULT_LOCALE, LOCALE_COOKIE, isLocale, type Locale } from "./config";

/**
 * Locale is stored in a cookie rather than the URL.
 *
 * This keeps a single set of routes (`/auctions/[id]` rather than
 * `/[locale]/auctions/[id]`) while still letting the whole tree render in the
 * reader's language. If per-locale URLs are needed later, swapping to
 * next-intl's routing only changes these two functions and the folder layout.
 */
export async function getUserLocale(): Promise<Locale> {
  const store = await cookies();
  const value = store.get(LOCALE_COOKIE)?.value;
  return isLocale(value) ? value : DEFAULT_LOCALE;
}

export async function setUserLocale(locale: Locale): Promise<void> {
  const store = await cookies();
  store.set(LOCALE_COOKIE, locale, {
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
    sameSite: "lax",
  });
}
