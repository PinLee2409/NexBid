import { cookies } from "next/headers";
import { getRequestConfig } from "next-intl/server";

import { DEFAULT_TIME_ZONE, LOCALE_INTL, TIME_ZONE_COOKIE, isTimeZone } from "./config";
import { getUserLocale } from "./locale";

/** The zone the browser reported (see TimeZoneSync), or UTC before it has. */
async function getUserTimeZone(): Promise<string> {
  const value = (await cookies()).get(TIME_ZONE_COOKIE)?.value;
  return isTimeZone(value) ? value : DEFAULT_TIME_ZONE;
}

export default getRequestConfig(async () => {
  const locale = await getUserLocale();

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
    timeZone: await getUserTimeZone(),
    formats: {
      dateTime: {
        short: { dateStyle: "medium" },
        full: { dateStyle: "medium", timeStyle: "short" },
      },
    },
    // Intl locale used for dates and numbers inside message placeholders.
    now: new Date(),
    onError(error) {
      if (process.env.NODE_ENV === "development") {
        console.warn(`[i18n:${LOCALE_INTL[locale]}]`, error.message);
      }
    },
  };
});
