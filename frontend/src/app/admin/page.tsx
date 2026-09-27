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
import { formatCompactCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { getActivity, getAdminStats, listAuditLogs } from "@/services/admin-service";
import type { ApiAnalyticsHour } from "@/services/api/dto";

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

      <ActivitySection />

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

/**
 * The last 24 hours from the Analytics Consumer (spec §19): four headline
 * figures and bids per hour. One series, so the heading names it and no
 * legend is needed; each hour shows its exact count on hover.
 */
function ActivitySection() {
  const t = useTranslations("admin");
  const format = useFormatter();
  const activity = useAsyncData("admin-activity", () => getActivity(24));

  return (
    <section className="mt-16" aria-labelledby="last-day-heading">
      <div className="border-line flex items-baseline justify-between gap-4 border-b pb-4">
        <h2 id="last-day-heading" className="display text-2xl">
          {t("activityTitle")}
        </h2>
        <span className="label-sm text-dim">{t("activityHint")}</span>
      </div>

      {activity.state.status === "loading" ? (
        <Skeleton className="mt-6 h-48 w-full rounded-none" />
      ) : activity.state.status === "error" ? (
        <p className="text-muted-foreground mt-6 text-sm">{t("activityUnavailable")}</p>
      ) : (
        <>
          <dl className="mt-6 grid grid-cols-2 gap-x-8 gap-y-6 lg:grid-cols-4">
            {[
              { label: t("activityBids"), value: format.number(activity.state.data.totals.bids) },
              { label: t("activityClosed"), value: format.number(activity.state.data.totals.auctionsEnded) },
              {
                label: t("activitySellThrough"),
                value:
                  activity.state.data.totals.sellThrough === null
                    ? "—"
                    : format.number(activity.state.data.totals.sellThrough, { style: "percent" }),
              },
              { label: t("activityRevenue"), value: formatCompactCurrency(activity.state.data.totals.revenue) },
            ].map((figure) => (
              <div key={figure.label}>
                <dt className="label-sm text-dim">{figure.label}</dt>
                <dd className="figure mt-1.5 text-2xl">{figure.value}</dd>
              </div>
            ))}
          </dl>
          <BidsPerHour hours={activity.state.data.hours} />
        </>
      )}
    </section>
  );
}

function BidsPerHour({ hours }: { hours: ApiAnalyticsHour[] }) {
  const t = useTranslations("admin");
  const format = useFormatter();

  const peak = hours.reduce((best, hour) => (hour.bids > best.bids ? hour : best), hours[0]);
  const max = Math.max(1, peak?.bids ?? 0);
  const total = hours.reduce((sum, hour) => sum + hour.bids, 0);
  const timeOf = (iso: string) => format.dateTime(new Date(iso), { hour: "2-digit", minute: "2-digit" });

  return (
    <figure className="mt-10">
      <figcaption className="label-sm text-dim mb-3">{t("activityChartLabel")}</figcaption>

      <div
        className="flex h-36 items-end gap-0.5"
        role="img"
        aria-label={t("activityChartSummary", { total, peak: peak ? timeOf(peak.hour) : "—" })}
      >
        {hours.map((hour, index) => (
          // The whole column is the hover target, not just the bar.
          <div key={hour.hour} className="group relative flex h-full flex-1 items-end">
            <div
              className="bg-signal w-full rounded-t-[4px] opacity-80 transition-opacity group-hover:opacity-100"
              style={{ height: hour.bids === 0 ? 0 : `${Math.max(3, (hour.bids / max) * 100)}%` }}
            />
            <span
              className={cn(
                "bg-popover text-popover-foreground ring-foreground/10 pointer-events-none absolute bottom-full z-10 mb-2 whitespace-nowrap px-2 py-1 text-xs opacity-0 ring-1 transition-opacity group-hover:opacity-100",
                // Near either edge the tooltip hugs that edge instead of spilling off the page.
                index < 3 ? "left-0" : index >= hours.length - 3 ? "right-0" : "left-1/2 -translate-x-1/2",
              )}
            >
              {timeOf(hour.hour)} · {t("activityHourBids", { count: hour.bids })}
            </span>
          </div>
        ))}
      </div>

      <div className="border-line mono-figure text-dim flex gap-0.5 border-t pt-2 text-[11px]" aria-hidden="true">
        {hours.map((hour, index) => (
          <span key={hour.hour} className="flex-1 overflow-visible whitespace-nowrap">
            {index % 6 === 0 ? timeOf(hour.hour) : ""}
          </span>
        ))}
      </div>

      <table className="sr-only">
        <caption>{t("activityChartLabel")}</caption>
        <tbody>
          {hours.map((hour) => (
            <tr key={hour.hour}>
              <th scope="row">{timeOf(hour.hour)}</th>
              <td>{hour.bids}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
