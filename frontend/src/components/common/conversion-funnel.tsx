"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useCategoryLabels } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import { getFunnel, type FunnelStep } from "@/services/market-service";

const STEPS = [
  "closed",
  "viewed",
  "bid",
  "won",
  "paid",
  "shipped",
  "completed",
] as const;
const RANGES = [7, 30, 90] as const;

/**
 * EN: Lots that closed in the chosen window, step by step: one bar per step as a share of the closed lots, then the same
 *     steps per category. Bars of one series, so a single colour; every bar carries its count and share as text.
 * VI: Các lô đóng trong khoảng đã chọn, từng bước một: mỗi bước một thanh theo tỉ lệ trên số lô đã đóng, rồi cùng các
 *     bước đó theo từng danh mục. Một chuỗi dữ liệu nên một màu; thanh nào cũng có số lượng và tỉ lệ dạng chữ.
 */
export function ConversionFunnel() {
  const t = useTranslations("funnel");
  const categoryLabels = useCategoryLabels();
  const [days, setDays] = useState<(typeof RANGES)[number]>(30);
  const { state } = useAsyncData(`funnel-${days}`, () => getFunnel(days));

  const share = (value: number, total: number) =>
    total === 0 ? 0 : Math.round((value / total) * 100);

  return (
    <section
      className="mt-16"
      aria-labelledby="funnel-heading"
      data-testid="funnel"
    >
      <div className="border-line flex flex-wrap items-baseline justify-between gap-4 border-b pb-4">
        <h2 id="funnel-heading" className="display text-2xl">
          {t("title")}
        </h2>
        <div className="flex gap-1" role="group" aria-label={t("range")}>
          {RANGES.map((range) => (
            <button
              key={range}
              type="button"
              aria-pressed={days === range}
              onClick={() => setDays(range)}
              className={cn(
                "label-sm border px-3 py-1.5 transition-colors",
                days === range
                  ? "border-signal-text text-signal-text"
                  : "border-line text-muted-foreground hover:text-foreground",
              )}
            >
              {t("days", { count: range })}
            </button>
          ))}
        </div>
      </div>
      <p className="text-muted-foreground mt-3 text-sm">{t("lead")}</p>

      {state.status === "loading" ? (
        <Skeleton className="mt-6 h-64 w-full rounded-none" />
      ) : state.status === "error" ? null : state.data.total.closed === 0 ? (
        <p className="text-dim mt-6 text-sm">{t("empty")}</p>
      ) : (
        <>
          <ol className="mt-6 space-y-2">
            {STEPS.map((step) => {
              const value = state.data.total[step];
              const percent = share(value, state.data.total.closed);
              return (
                <li
                  key={step}
                  className="grid grid-cols-[7rem_1fr_6rem] items-center gap-3 text-sm sm:grid-cols-[9rem_1fr_7rem]"
                >
                  <span className="text-muted-foreground">
                    {t(`step.${step}`)}
                  </span>
                  <span className="bg-surface h-6" aria-hidden="true">
                    <span
                      className="bg-signal-text block h-full"
                      style={{
                        width: `${Math.max(percent, value > 0 ? 1 : 0)}%`,
                      }}
                    />
                  </span>
                  <span className="mono-figure text-right">
                    {value} <span className="text-dim">· {percent}%</span>
                  </span>
                </li>
              );
            })}
          </ol>

          <div className="mt-8 overflow-x-auto" tabIndex={0} role="region" aria-label={t("byCategory")}>
            <table className="w-full min-w-[640px] text-sm">
              <caption className="sr-only">{t("byCategory")}</caption>
              <thead>
                <tr className="border-line text-dim border-b text-left">
                  <th className="label-sm py-2 pr-3 font-normal">
                    {t("category")}
                  </th>
                  {STEPS.map((step) => (
                    <th
                      key={step}
                      className="label-sm py-2 pr-3 text-right font-normal"
                    >
                      {t(`step.${step}`)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {state.data.categories.map((row: FunnelStep) => (
                  <tr key={row.slug ?? "none"} className="border-line border-b">
                    <td className="py-2 pr-3">
                      {row.slug
                        ? categoryLabels.name({
                            slug: row.slug,
                            name: row.name ?? row.slug,
                          })
                        : "—"}
                    </td>
                    {STEPS.map((step) => (
                      <td
                        key={step}
                        className="mono-figure py-2 pr-3 text-right"
                      >
                        {row[step]}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
