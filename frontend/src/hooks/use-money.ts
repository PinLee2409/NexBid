import { useLocale } from "next-intl";

import type { Locale } from "@/i18n/config";
import {
  currencySymbolAfter,
  formatAmount,
  formatCompactCurrency,
  formatCompactNumber,
  formatCurrency,
} from "@/lib/format";

/**
 * EN: Money and counters written the way the reader's language writes them; the amount is always VND.
 *     Works in client and server components alike (next-intl's `useLocale` does), and the formatters
 *     behind it are cached, so there is nothing to memoise.
 * VI: Tiền và bộ đếm được viết theo cách của ngôn ngữ người đọc; số tiền luôn là VND. Dùng được ở cả
 *     component client lẫn server (vì `useLocale` của next-intl dùng được), và các formatter phía sau đã
 *     được cache nên không cần memo.
 */
export function useMoney() {
  const locale = useLocale() as Locale;
  return {
    locale,
    currency: (amount: number) => formatCurrency(amount, locale),
    compactCurrency: (amount: number) => formatCompactCurrency(amount, locale),
    amount: (amount: number) => formatAmount(amount, locale),
    compactNumber: (value: number) => formatCompactNumber(value, locale),
    symbolAfter: currencySymbolAfter(locale),
  };
}
