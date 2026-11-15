"use client";

import { BarChart3, Eye, Gavel, Heart, Percent } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton, StatTileSkeleton } from "@/components/common/loading-skeleton";
import { StatTile } from "@/components/common/stat-tile";
import { PageHeader } from "@/components/layout/page-header";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { getSellerStats } from "@/services/stats-service";

/**
 * EN: How the seller's lots are doing: visitors (one a day each), watchers, bidders and bids per lot, and the share of
 *     closed lots that sold.
 * VI: Các lô của người bán đang thế nào: người xem (mỗi người một lần mỗi ngày), người theo dõi, người trả giá và lượt
 *     trả giá của từng lô, và tỉ lệ lô đã đóng bán được.
 */
export default function SellerStatsPage() {
  const t = useTranslations("seller");
  const money = useMoney();
  const labels = useEnumLabels();
  const { state } = useAsyncData("seller-stats", getSellerStats);

  if (state.status === "error") {
    return (
      <>
        <PageHeader title={t("statsTitle")} description={t("statsSubtitle")} />
        <div className="mt-10">
          <ApiErrorState icon={BarChart3} error={state.error} />
        </div>
      </>
    );
  }

  const stats = state.status === "success" ? state.data : null;
  const rate = stats?.sellThrough;

  return (
    <>
      <PageHeader title={t("statsTitle")} description={t("statsSubtitle")} />

      <div className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
        {!stats ? (
          Array.from({ length: 4 }).map((_, index) => <StatTileSkeleton key={index} />)
        ) : (
          <>
            <StatTile icon={Eye} label={t("statsViews")} value={stats.views.toLocaleString()} />
            <StatTile icon={Heart} label={t("statsWatchers")} value={stats.watchers.toLocaleString()} />
            <StatTile icon={Gavel} label={t("statsBids")} value={stats.bids.toLocaleString()} />
            <StatTile
              icon={Percent}
              label={t("statsSellThrough")}
              value={rate == null ? "—" : `${Math.round(rate * 100)}%`}
            />
          </>
        )}
      </div>

      {stats ? (
        <p className="text-dim mt-4 text-sm">
          {t("statsSellThroughHint", { sold: stats.sold, closed: stats.closed })}
        </p>
      ) : null}

      <section className="mt-16" aria-labelledby="stats-heading">
        <h2 id="stats-heading" className="display border-line border-b pb-4 text-2xl">
          {t("statsLots")}
        </h2>
        {!stats ? (
          Array.from({ length: 3 }).map((_, index) => <AuctionListRowSkeleton key={index} />)
        ) : stats.lots.length === 0 ? (
          <div className="mt-8">
            <EmptyState icon={BarChart3} title={t("statsEmptyTitle")} description={t("statsEmptyBody")} />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-line text-dim border-b text-left">
                  <th className="label-sm py-3 pr-4 font-normal">{t("statsLot")}</th>
                  <th className="label-sm py-3 pr-4 font-normal">{t("statsStatus")}</th>
                  <th className="label-sm py-3 pr-4 text-right font-normal">{t("statsPrice")}</th>
                  <th className="label-sm py-3 pr-4 text-right font-normal">{t("statsViews")}</th>
                  <th className="label-sm py-3 pr-4 text-right font-normal">{t("statsWatchers")}</th>
                  <th className="label-sm py-3 pr-4 text-right font-normal">{t("statsBidders")}</th>
                  <th className="label-sm py-3 text-right font-normal">{t("statsBids")}</th>
                </tr>
              </thead>
              <tbody>
                {stats.lots.map((lot) => (
                  <tr key={lot.auctionId} className="border-line border-b">
                    <td className="py-3 pr-4">
                      <Link href={`/auctions/${lot.auctionId}`} className="hover:text-signal-text transition-colors">
                        {lot.name}
                      </Link>
                    </td>
                    <td className="text-dim py-3 pr-4">{labels.auctionStatus(lot.status)}</td>
                    <td className="mono-figure py-3 pr-4 text-right">{money.currency(lot.currentPrice)}</td>
                    <td className="mono-figure py-3 pr-4 text-right">{lot.views}</td>
                    <td className="mono-figure py-3 pr-4 text-right">{lot.watchers}</td>
                    <td className="mono-figure py-3 pr-4 text-right">{lot.bidders}</td>
                    <td className="mono-figure py-3 text-right">{lot.bids}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
