"use client";

import { Gavel, Package, Plus, TrendingUp } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { EmptyState } from "@/components/common/empty-state";
import {
  AuctionListRowSkeleton,
  StatTileSkeleton,
} from "@/components/common/loading-skeleton";
import { StatTile } from "@/components/common/stat-tile";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { formatCompactCurrency } from "@/lib/format";
import { listMyAuctions, getSellerStats } from "@/services/seller-service";

export default function SellerDashboardPage() {
  const t = useTranslations("seller");
  const tc = useTranslations("common");

  const stats = useAsyncData("seller-stats", getSellerStats);
  const auctions = useAsyncData("seller-auctions", listMyAuctions);

  const statTiles =
    stats.state.status === "success"
      ? [
          {
            icon: Gavel,
            label: t("statActive"),
            value: String(stats.state.data.activeAuctions),
          },
          {
            icon: Package,
            label: t("statUpcoming"),
            value: String(stats.state.data.upcomingAuctions),
          },
          {
            icon: TrendingUp,
            label: t("statBids"),
            value: String(stats.state.data.totalBids),
          },
          {
            icon: TrendingUp,
            label: t("statSales"),
            value: `${stats.state.data.completedSales} · ${formatCompactCurrency(stats.state.data.grossSales)}`,
          },
        ]
      : null;

  const recent =
    auctions.state.status === "success" ? auctions.state.data.slice(0, 5) : [];

  return (
    <>
      <PageHeader
        title={t("dashboardTitle")}
        description={t("dashboardSubtitle")}
        actions={
          <Button asChild>
            <Link href="/seller/auctions/create">
              <Plus className="size-4" />
              {t("newAuction")}
            </Link>
          </Button>
        }
      />

      <div className="mt-10 grid gap-x-8 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
        {statTiles === null
          ? Array.from({ length: 4 }).map((_, index) => (
              <StatTileSkeleton key={index} />
            ))
          : statTiles.map((tile) => <StatTile key={tile.label} {...tile} />)}
      </div>

      <div className="mt-16 grid gap-12 lg:grid-cols-[minmax(0,1fr)_280px] lg:gap-16">
        <section aria-labelledby="recent-heading">
          <div className="border-line flex items-baseline justify-between gap-4 border-b pb-4">
            <h2 id="recent-heading" className="display text-2xl">
              {t("recentAuctions")}
            </h2>
            <Link
              href="/seller/auctions"
              className="label text-muted-foreground hover:text-signal-text group inline-flex items-center gap-2 transition-colors"
            >
              {tc("viewAll")}
              <span className="transition-transform group-hover:translate-x-1">
                →
              </span>
            </Link>
          </div>

          <div className="mt-2">
            {auctions.state.status === "loading" ? (
              <div>
                {Array.from({ length: 3 }).map((_, index) => (
                  <AuctionListRowSkeleton key={index} />
                ))}
              </div>
            ) : recent.length === 0 ? (
              <EmptyState
                icon={Package}
                title={t("dashboardEmptyTitle")}
                description={t("dashboardEmptyBody")}
                action={{
                  label: t("quickCreateProduct"),
                  href: "/seller/products/create",
                }}
              />
            ) : (
              <ul>
                {recent.map((auction) => (
                  <AuctionListItem key={auction.id} auction={auction} />
                ))}
              </ul>
            )}
          </div>
        </section>

        <aside aria-labelledby="quick-heading">
          <h2
            id="quick-heading"
            className="border-line label-sm text-dim border-b pb-4"
          >
            {t("quickActions")}
          </h2>
          <div>
            <QuickAction
              href="/seller/products/create"
              label={t("quickCreateProduct")}
              icon={Package}
            />
            <QuickAction
              href="/seller/auctions/create"
              label={t("quickCreateAuction")}
              icon={Gavel}
            />
            <QuickAction
              href="/seller/auctions"
              label={t("quickViewAuctions")}
              icon={TrendingUp}
            />
          </div>
        </aside>
      </div>
    </>
  );
}

function QuickAction({
  href,
  label,
  icon: Icon,
}: {
  href: string;
  label: string;
  icon: typeof Package;
}) {
  return (
    <Link
      href={href}
      className="label border-line text-muted-foreground hover:text-signal-text group flex items-center gap-3 border-b py-4 transition-colors"
    >
      <Icon className="size-4 shrink-0" aria-hidden="true" />
      {label}
      <span className="ml-auto transition-transform group-hover:translate-x-1">
        →
      </span>
    </Link>
  );
}
