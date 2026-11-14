"use client";

import { BellRing, Search, Store, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage, useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import {
  deleteSavedSearch,
  listFollowedSellers,
  listSavedSearches,
  savedSearchHref,
  setFollowing,
  type SavedSearch,
} from "@/services/alert-service";

/**
 * EN: The caller's saved searches and followed sellers, each removable; new lots for them arrive as notifications and
 *     in a daily email.
 * VI: Tìm kiếm đã lưu và người bán đang theo dõi của người gọi, cái nào cũng gỡ được; lô mới khớp tới dưới dạng thông báo
 *     và trong email hằng ngày.
 */
export default function AlertsPage() {
  const t = useTranslations("account");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const searches = useAsyncData("saved-searches", listSavedSearches);
  const sellers = useAsyncData("followed-sellers", listFollowedSellers);
  const [isPending, startTransition] = useTransition();

  function remove(search: SavedSearch) {
    startTransition(async () => {
      try {
        await deleteSavedSearch(search.id);
        toast.success(t("alertsDeleted"));
        searches.refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  function unfollow(sellerId: string) {
    startTransition(async () => {
      try {
        await setFollowing(sellerId, false);
        sellers.refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <PageHeader title={t("alertsTitle")} description={t("alertsSubtitle")} />
      <p className="text-muted-foreground mt-3 text-sm">
        {t("alertsDigestNote")}{" "}
        <Link href="/profile#email" className="text-signal-text underline-offset-4 hover:underline">
          {t("alertsDigestSettings")}
        </Link>
      </p>

      <section className="mt-10" aria-labelledby="searches-heading">
        <h2 id="searches-heading" className="display text-2xl">
          {t("alertsSearchesTitle")}
        </h2>
        <div className="mt-4">
          {searches.state.status === "loading" ? (
            <AuctionListRowSkeleton />
          ) : searches.state.status === "error" ? (
            <ApiErrorState icon={Search} error={searches.state.error} />
          ) : searches.state.data.length === 0 ? (
            <EmptyState
              icon={Search}
              title={t("alertsSearchesTitle")}
              description={t("alertsSearchesEmpty")}
              action={{ label: t("alertsBrowse"), href: "/auctions" }}
            />
          ) : (
            <ul className="border-line divide-line divide-y border-y">
              {searches.state.data.map((search) => (
                <li key={search.id} className="flex flex-wrap items-center gap-x-6 gap-y-3 py-4">
                  <div className="min-w-0 flex-1">
                    <SearchSummary search={search} />
                    <p className="mono-figure text-dim mt-1 text-[11px]">
                      {t("alertsSince", { date: format.dateTime(new Date(search.createdAt), { dateStyle: "medium" }) })}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button asChild size="sm" variant="outline">
                      <Link href={savedSearchHref(search)}>{t("alertsView")}</Link>
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => remove(search)}
                      disabled={isPending}
                      aria-label={t("alertsDelete")}
                      title={t("alertsDelete")}
                    >
                      <Trash2 className="size-4" aria-hidden="true" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className="mt-14" aria-labelledby="sellers-heading">
        <h2 id="sellers-heading" className="display text-2xl">
          {t("alertsSellersTitle")}
        </h2>
        <div className="mt-4">
          {sellers.state.status === "loading" ? (
            <AuctionListRowSkeleton />
          ) : sellers.state.status === "error" ? (
            <ApiErrorState icon={Store} error={sellers.state.error} />
          ) : sellers.state.data.length === 0 ? (
            <EmptyState icon={BellRing} title={t("alertsSellersTitle")} description={t("alertsSellersEmpty")} />
          ) : (
            <ul className="border-line divide-line divide-y border-y">
              {sellers.state.data.map((seller) => (
                <li key={seller.sellerId} className="flex items-center gap-4 py-4">
                  <span
                    className="bg-foreground text-background flex size-10 shrink-0 items-center justify-center text-sm font-semibold"
                    aria-hidden="true"
                  >
                    {seller.displayName.slice(0, 2).toUpperCase()}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">{seller.displayName}</p>
                    <p className="mono-figure text-dim text-[11px]">
                      {t("alertsSince", { date: format.dateTime(new Date(seller.since), { dateStyle: "medium" }) })}
                    </p>
                  </div>
                  <Button size="sm" variant="outline" onClick={() => unfollow(seller.sellerId)} disabled={isPending}>
                    {t("alertsUnfollow")}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </>
  );
}

/** EN: The filters in words: "seiko" · Watches · Like new · from ₫1,000,000. / VI: Bộ lọc bằng chữ. */
function SearchSummary({ search }: { search: SavedSearch }) {
  const t = useTranslations("account");
  const labels = useEnumLabels();
  const categoryLabels = useCategoryLabels();
  const money = useMoney();

  const parts: string[] = [
    ...search.category.map((slug) => categoryLabels.name({ slug, name: slug })),
    ...search.condition.map((condition) => labels.condition(condition)),
  ];
  if (search.minPrice !== null) parts.push(t("alertsPriceFrom", { price: money.currency(search.minPrice) }));
  if (search.maxPrice !== null) parts.push(t("alertsPriceTo", { price: money.currency(search.maxPrice) }));

  return (
    <p className="text-sm">
      {search.q ? <span className="font-semibold">“{search.q}”</span> : null}
      {search.q && parts.length ? <span className="text-dim"> · </span> : null}
      <span className="text-muted-foreground">{parts.join(" · ")}</span>
      {!search.q && !parts.length ? <span>{t("alertsAny")}</span> : null}
    </p>
  );
}
