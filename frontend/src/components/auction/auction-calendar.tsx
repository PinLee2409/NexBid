"use client";

import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { WatchButton } from "@/components/auction/watch-button";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useMoney } from "@/hooks/use-money";
import { listCalendar } from "@/services/auction-service";
import type { AuctionSummary } from "@/types";

const DAY_MS = 86_400_000;

/** EN: A Vietnam calendar date, yyyy-MM-dd. / VI: Ngày theo lịch Việt Nam, yyyy-MM-dd. */
function vietnamDate(at: number): string {
  return new Date(at + 7 * 3_600_000).toISOString().slice(0, 10);
}

/**
 * EN: Two weeks at a time of lots opening, grouped by Vietnam day. Watching a lot is the reminder: watchers hear
 *     shortly before it opens, in the bell, by email and in the browser if they turned that on.
 * VI: Mỗi lần hai tuần các lô sắp mở, gom theo ngày giờ Việt Nam. Theo dõi một lô chính là đặt nhắc: người theo dõi
 *     được báo ngay trước khi lô mở, qua chuông, email và trình duyệt nếu đã bật.
 */
export function AuctionCalendar() {
  const t = useTranslations("calendar");
  const format = useFormatter();
  const money = useMoney();
  const [start] = useState(() => Date.now());
  const [week, setWeek] = useState(0);
  const from = vietnamDate(start + week * 14 * DAY_MS);
  const to = vietnamDate(start + (week * 14 + 13) * DAY_MS);
  const { state } = useAsyncData(`calendar-${from}`, () => listCalendar(from, to));

  const days = new Map<string, AuctionSummary[]>();
  if (state.status === "success") {
    for (const lot of state.data) {
      const day = vietnamDate(new Date(lot.startTime).getTime());
      days.set(day, [...(days.get(day) ?? []), lot]);
    }
  }

  return (
    <div className="mt-10">
      <div className="border-line flex items-center justify-between gap-4 border-y py-3">
        <Button variant="ghost" size="sm" onClick={() => setWeek((w) => w - 1)} disabled={week === 0}>
          <ChevronLeft className="size-4" />
          {t("earlier")}
        </Button>
        {/* EN: The server may format in another time zone than the browser. / VI: Server có thể định dạng theo múi giờ khác trình duyệt. */}
        <p className="label-sm text-dim" data-testid="calendar-range" suppressHydrationWarning>
          {format.dateTimeRange(new Date(`${from}T12:00:00+07:00`), new Date(`${to}T12:00:00+07:00`), {
            day: "numeric",
            month: "short",
          })}
        </p>
        <Button variant="ghost" size="sm" onClick={() => setWeek((w) => w + 1)} disabled={week >= 3}>
          {t("later")}
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {state.status === "loading" ? (
        <Skeleton className="mt-8 h-60 w-full" />
      ) : state.status === "error" ? (
        <ApiErrorState icon={CalendarDays} error={state.error} />
      ) : days.size === 0 ? (
        <EmptyState icon={CalendarDays} title={t("emptyTitle")} description={t("emptyBody")} />
      ) : (
        <ol className="mt-4" data-testid="calendar">
          {[...days.entries()].map(([day, lots]) => (
            <li key={day} className="border-line grid gap-4 border-b py-6 md:grid-cols-[180px_1fr]">
              <h2 className="display text-xl">
                {format.dateTime(new Date(`${day}T12:00:00+07:00`), { weekday: "long", day: "numeric", month: "long" })}
              </h2>
              <ul className="space-y-3">
                {lots.map((lot) => (
                  <li key={lot.id} className="flex items-center gap-4">
                    <span className="mono-figure text-dim w-14 shrink-0 text-sm">
                      {format.dateTime(new Date(lot.startTime), { hour: "2-digit", minute: "2-digit" })}
                    </span>
                    <Link href={`/auctions/${lot.id}`} className="min-w-0 flex-1 hover:underline">
                      <span className="block truncate font-medium">{lot.product.name}</span>
                      <span className="text-dim text-xs">
                        {t("startsAt", { price: money.currency(lot.startingPrice) })}
                        {lot.freeShipping ? ` · ${t("freeShipping")}` : ""}
                      </span>
                    </Link>
                    <WatchButton auctionId={lot.id} productName={lot.product.name} />
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      )}
      <p className="text-dim mt-6 text-xs">{t("reminderHint")}</p>
    </div>
  );
}
