"use client";

import { History } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";

import { useAsyncData } from "@/hooks/use-async-data";
import { useMoney } from "@/hooks/use-money";
import { priceHistory } from "@/services/market-service";

/**
 * EN: What lots like this one sold for in the past year: the low, middle and high price and the latest sales, so a
 *     bidder knows how far to go. Hidden when nothing comparable sold.
 * VI: Những lô giống lô này đã bán với giá bao nhiêu trong năm qua: giá thấp, giữa và cao cùng các giao dịch gần đây,
 *     để người trả giá biết nên trả tới đâu. Ẩn khi chưa có gì tương tự được bán.
 */
export function PriceHistory({ auctionId }: { auctionId: string }) {
  const t = useTranslations("deals");
  const money = useMoney();
  const format = useFormatter();
  const { state } = useAsyncData(`price-history-${auctionId}`, () =>
    priceHistory(auctionId),
  );

  if (state.status !== "success" || state.data.sales.length === 0) return null;
  const history = state.data;

  return (
    <section
      className="border-line mt-10 border-t pt-8"
      aria-labelledby="price-history-heading"
      data-testid="price-history"
    >
      <div className="flex items-center gap-2">
        <History className="text-signal-text size-4" aria-hidden="true" />
        <h2 id="price-history-heading" className="label">
          {t("historyTitle")}
        </h2>
      </div>
      <p className="text-muted-foreground mt-1 text-sm">
        {history.basis === "SIMILAR"
          ? t("historySimilar")
          : history.basis === "CATEGORY"
            ? t("historyCategory")
            : t("historyFew")}
      </p>
      {history.median !== null ? (
        <dl className="mt-4 grid grid-cols-3 gap-3">
          {(
            [
              ["historyLow", history.low],
              ["historyMedian", history.median],
              ["historyHigh", history.high],
            ] as const
          ).map(([key, value]) => (
            <div key={key} className="bg-surface p-3">
              <dt className="label-sm text-dim">{t(key)}</dt>
              <dd className="mono-figure mt-1 text-sm sm:text-base">
                {value === null ? "—" : money.currency(value)}
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
      <ul className="mt-4 text-sm">
        {history.sales.slice(0, 5).map((sale) => (
          <li
            key={sale.auctionId}
            className="border-line flex items-baseline justify-between gap-3 border-b py-2"
          >
            <Link
              href={`/auctions/${sale.auctionId}`}
              className="hover:text-signal-text min-w-0 truncate"
            >
              {sale.title}
            </Link>
            <span className="text-dim shrink-0 text-xs">
              {format.dateTime(new Date(sale.endedAt), { dateStyle: "medium" })}
            </span>
            <span className="mono-figure shrink-0">
              {money.currency(sale.price)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
