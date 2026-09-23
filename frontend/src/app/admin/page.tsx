"use client";

import { Gavel, ShieldAlert, UserRound, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";

import { StatTileSkeleton } from "@/components/common/loading-skeleton";
import { StatTile } from "@/components/common/stat-tile";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { getAdminStats, listAuditLogs } from "@/services/admin-service";

export default function AdminOverviewPage() {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const stats = useAsyncData("admin-stats", getAdminStats);
  const logs = useAsyncData("admin-recent-logs", listAuditLogs);

  const tiles =
    stats.state.status === "success"
      ? [
          {
            icon: ShieldAlert,
            label: t("statPending"),
            value: String(stats.state.data.pendingApproval),
            urgent: stats.state.data.pendingApproval > 0,
            href: "/admin/auctions",
          },
          {
            icon: Gavel,
            label: t("statActive"),
            value: String(stats.state.data.activeAuctions),
          },
          {
            icon: Users,
            label: t("statUsers"),
            value: String(stats.state.data.totalUsers),
            href: "/admin/users",
          },
          {
            icon: UserRound,
            label: t("statBlocked"),
            value: String(stats.state.data.blockedUsers),
          },
        ]
      : null;

  return (
    <>
      <PageHeader
        title={t("overviewTitle")}
        description={t("overviewSubtitle")}
        actions={
          <Button asChild>
            <Link href="/admin/auctions">{t("needsReview")}</Link>
          </Button>
        }
      />

      <div className="mt-10 grid gap-x-8 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
        {tiles === null
          ? Array.from({ length: 4 }).map((_, index) => (
              <StatTileSkeleton key={index} />
            ))
          : tiles.map((tile) => <StatTile key={tile.label} {...tile} />)}
      </div>

      <section className="mt-16" aria-labelledby="activity-heading">
        <div className="border-line flex items-baseline justify-between gap-4 border-b pb-4">
          <h2 id="activity-heading" className="display text-2xl">
            {t("recentActivity")}
          </h2>
          <Link
            href="/admin/audit-logs"
            className="label text-muted-foreground hover:text-signal-text group inline-flex items-center gap-2 transition-colors"
          >
            {tc("viewAll")}
            <span className="transition-transform group-hover:translate-x-1">
              →
            </span>
          </Link>
        </div>

        {logs.state.status === "loading" ? (
          <div>
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton
                key={index}
                className="border-line mt-0 h-14 w-full rounded-none border-b"
              />
            ))}
          </div>
        ) : logs.state.status === "success" ? (
          <ul>
            {logs.state.data.slice(0, 6).map((log) => (
              <li
                key={log.id}
                className="border-line flex flex-wrap items-baseline gap-x-5 gap-y-1 border-b py-3.5"
              >
                <span className="label">{labels.auditAction(log.action)}</span>
                <span className="text-muted-foreground text-sm">
                  {log.actorDisplayName === "system"
                    ? t("system")
                    : log.actorDisplayName}
                </span>
                <span className="mono-figure text-dim ml-auto text-[11px]">
                  {format.relativeTime(new Date(log.createdAt))}
                </span>
              </li>
            ))}
          </ul>
        ) : null}
      </section>
    </>
  );
}
