"use client";

import { Undo2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { formatLot } from "@/components/nexbid/lot-number";
import { useAsyncData } from "@/hooks/use-async-data";
import { useMoney } from "@/hooks/use-money";
import { cn } from "@/lib/utils";
import { retractionLog } from "@/services/market-service";

/**
 * EN: Every bid taken back, newest first: who, on which lot, how much, why, and what the price went back to. A bidder
 *     at or near the monthly limit stands out, since repeated retractions are how price probing looks.
 * VI: Mọi lượt trả giá bị rút, mới nhất trước: ai, trên lô nào, bao nhiêu, vì sao, và giá trở về mức nào. Người gần hoặc
 *     đã chạm giới hạn mỗi tháng được làm nổi bật, vì rút nhiều lần là dấu hiệu của việc dò giá.
 */
export default function AdminBidRetractionsPage() {
  const t = useTranslations("retractLog");
  const tr = useTranslations("retract");
  const money = useMoney();
  const format = useFormatter();
  const { state } = useAsyncData("admin-bid-retractions", retractionLog);

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      <div className="mt-8 overflow-x-auto" tabIndex={0} role="region" aria-label={t("title")} data-testid="retractions">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={Undo2} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={Undo2} title={t("emptyTitle")} description={t("emptyBody")} />
        ) : (
          <table className="w-full min-w-[820px] text-sm">
            <thead>
              <tr className="border-line text-dim border-b text-left">
                <th className="label-sm py-3 pr-4 font-normal">{t("when")}</th>
                <th className="label-sm py-3 pr-4 font-normal">{t("bidder")}</th>
                <th className="label-sm py-3 pr-4 font-normal">{t("lot")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("amount")}</th>
                <th className="label-sm py-3 pr-4 font-normal">{t("reason")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("price")}</th>
              </tr>
            </thead>
            <tbody>
              {state.data.map((row) => (
                <tr key={row.id} className="border-line border-b align-top">
                  <td className="py-3 pr-4 whitespace-nowrap">
                    {format.dateTime(new Date(row.createdAt), { dateStyle: "short", timeStyle: "short" })}
                  </td>
                  <td className="py-3 pr-4">
                    <p className="font-medium">{row.bidderName}</p>
                    <p className="text-dim text-xs">{row.bidderEmail}</p>
                    <p className={cn("text-xs", row.bidderRetractions30d >= 3 ? "text-warning" : "text-dim")}>
                      {t("recent", { count: row.bidderRetractions30d })}
                    </p>
                  </td>
                  <td className="py-3 pr-4">
                    <Link href={`/auctions/${row.auctionId}`} className="hover:underline">
                      <span className="mono-figure text-dim mr-2">{formatLot(row.lotNumber)}</span>
                      {row.lotName}
                    </Link>
                  </td>
                  <td className="py-3 pr-4 text-right tabular-nums">
                    {money.currency(row.amount)}
                    <p className="text-dim text-xs">{t("bids", { count: row.bidsRemoved })}</p>
                  </td>
                  <td className="py-3 pr-4">
                    <p>{tr(`reasons.${row.reason}`)}</p>
                    {row.note ? <p className="text-dim text-xs">“{row.note}”</p> : null}
                    {row.wasLeading ? <p className="text-dim text-xs">{t("wasLeading")}</p> : null}
                  </td>
                  <td className="py-3 pr-4 text-right whitespace-nowrap tabular-nums">
                    {money.currency(row.priceBefore)} → {money.currency(row.priceAfter)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
