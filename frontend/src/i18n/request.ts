import { getRequestConfig } from "next-intl/server";

import { LOCALE_INTL } from "./config";
import { getUserLocale } from "./locale";

export default getRequestConfig(async () => {
  const locale = await getUserLocale();

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
    timeZone: "UTC",
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
