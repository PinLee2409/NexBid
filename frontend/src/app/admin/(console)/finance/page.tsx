"use client";

import { AlertTriangle, Download, Landmark } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { BarChart } from "@/components/charts/bar-chart";
import { ApiErrorState } from "@/components/common/api-error-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { downloadCsv, getReconciliation, getRevenue } from "@/services/finance-service";

/** EN: A date in Vietnam, as yyyy-MM-dd. / VI: Một ngày theo giờ Việt Nam, dạng yyyy-MM-dd. */
function vietnamDate(offsetDays = 0): string {
  const at = new Date(Date.now() + offsetDays * 86_400_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(at);
}

/**
 * EN: Money for admins: what VNPay charged each day against what NexBid did with it (applied to a lot, owed back,
 *     refunded, paid out), and revenue by month, category and seller. Both export to CSV for Excel.
 * VI: Tiền cho admin: VNPay đã thu mỗi ngày bao nhiêu so với việc NexBid làm với số đó (áp vào lô, phải trả lại, đã
 *     hoàn, đã chi trả), và doanh thu theo tháng, danh mục, người bán. Cả hai xuất CSV mở bằng Excel.
 */
export default function AdminFinancePage() {
  const t = useTranslations("admin");
  const [tab, setTab] = useState<"reconciliation" | "revenue">("reconciliation");

  return (
    <>
      <PageHeader title={t("financeTitle")} description={t("financeSubtitle")} />
      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mt-6 block">
        <TabsList>
          <TabsTrigger value="reconciliation">{t("financeReconciliation")}</TabsTrigger>
          <TabsTrigger value="revenue">{t("financeRevenue")}</TabsTrigger>
        </TabsList>
        <TabsContent value="reconciliation" className="mt-8">
          <ReconciliationPanel />
        </TabsContent>
        <TabsContent value="revenue" className="mt-8">
          <RevenuePanel />
        </TabsContent>
      </Tabs>
    </>
  );
}

function useDownload() {
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  return {
    isPending,
    download: (path: string, filename: string) =>
      startTransition(async () => {
        try {
          await downloadCsv(path, filename);
        } catch (error) {
          toast.error(errorMessage(error));
        }
      }),
  };
}

function ReconciliationPanel() {
  const t = useTranslations("admin");
  const money = useMoney();
  const format = useFormatter();
  const [from, setFrom] = useState(vietnamDate(-29));
  const [to, setTo] = useState(vietnamDate());
  const { state } = useAsyncData(`finance-${from}-${to}`, () => getReconciliation(from, to));
  const { download, isPending } = useDownload();
  const columns = ["charged", "applied", "stray", "refunded", "strayRefunded", "paidOut"] as const;

  return (
    <div>
      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-2">
          <Label htmlFor="finance-from" className="label-sm text-dim">
            {t("financeFrom")}
          </Label>
          <Input id="finance-from" type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="finance-to" className="label-sm text-dim">
            {t("financeTo")}
          </Label>
          <Input id="finance-to" type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} />
        </div>
        <Button
          variant="outline"
          disabled={isPending}
          onClick={() =>
            download(`/api/admin/finance/charges.csv?from=${from}&to=${to}`, `nexbid-vnpay-${from}-${to}.csv`)
          }
        >
          <Download className="size-4" />
          {t("financeChargesCsv")}
        </Button>
      </div>
      <p className="text-dim mt-3 max-w-2xl text-xs leading-relaxed">{t("financeReconciliationHint")}</p>

      {state.status === "loading" ? (
        <AuctionListRowSkeleton />
      ) : state.status === "error" ? (
        <div className="mt-8">
          <ApiErrorState icon={Landmark} error={state.error} />
        </div>
      ) : (
        <>
          {state.data.total.unmatched > 0 ? (
            <p role="alert" className="bg-danger-dim text-danger-text mt-6 flex items-center gap-2 px-3.5 py-3 text-sm">
              <AlertTriangle className="size-4" aria-hidden="true" />
              {t("financeUnmatched", { count: state.data.total.unmatched })}
            </p>
          ) : null}
          <div className="mt-6 overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-line text-dim border-b text-left">
                  <th className="label-sm py-3 pr-4 font-normal">{t("financeDay")}</th>
                  <th className="label-sm py-3 pr-4 text-right font-normal">{t("financeCharges")}</th>
                  {columns.map((column) => (
                    <th key={column} className="label-sm py-3 pr-4 text-right font-normal">
                      {t(`financeColumn.${column}`)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[...state.data.days].reverse().map((day) => (
                  <tr key={day.day} className="border-line border-b">
                    <td className="py-2.5 pr-4">
                      {day.day ? format.dateTime(new Date(`${day.day}T12:00:00+07:00`), { dateStyle: "medium" }) : ""}
                    </td>
                    <td className="mono-figure py-2.5 pr-4 text-right">{day.charges}</td>
                    {columns.map((column) => (
                      <td key={column} className="mono-figure py-2.5 pr-4 text-right">
                        {day[column] ? money.currency(day[column]) : "—"}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="font-medium">
                  <td className="py-3 pr-4">{t("financeTotal")}</td>
                  <td className="mono-figure py-3 pr-4 text-right">{state.data.total.charges}</td>
                  {columns.map((column) => (
                    <td key={column} className="mono-figure py-3 pr-4 text-right">
                      {money.currency(state.data.total[column])}
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function RevenuePanel() {
  const t = useTranslations("admin");
  const money = useMoney();
  const format = useFormatter();
  const { state } = useAsyncData("finance-revenue", () => getRevenue(12));
  const { download, isPending } = useDownload();

  if (state.status === "loading") return <AuctionListRowSkeleton />;
  if (state.status === "error") return <ApiErrorState icon={Landmark} error={state.error} />;
  const { months, categories, sellers } = state.data;

  return (
    <div className="space-y-12">
      <div>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="display text-2xl">{t("financeByMonth")}</h2>
          <Button
            variant="outline"
            disabled={isPending}
            onClick={() => download("/api/admin/finance/revenue.csv?months=12", "nexbid-revenue.csv")}
          >
            <Download className="size-4" />
            {t("financeRevenueCsv")}
          </Button>
        </div>
        <h3 className="label-sm text-dim mt-6">{t("financeFeesChart")}</h3>
        <BarChart
          className="mt-3"
          label={t("financeFeesChart")}
          format={(value) => money.currency(value)}
          bars={months.map((month) => ({
            key: month.month,
            label: format.dateTime(new Date(`${month.month}-15T12:00:00+07:00`), { month: "short" }),
            value: month.fees,
          }))}
        />
        <div className="mt-8 overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-line text-dim border-b text-left">
                <th className="label-sm py-3 pr-4 font-normal">{t("financeMonth")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("financeSales")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("financeGross")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("financeFees")}</th>
                <th className="label-sm py-3 pr-4 text-right font-normal">{t("financeShipping")}</th>
                <th className="label-sm py-3 text-right font-normal">{t("financeDiscounts")}</th>
              </tr>
            </thead>
            <tbody>
              {[...months].reverse().map((month) => (
                <tr key={month.month} className="border-line border-b">
                  <td className="py-2.5 pr-4">
                    {format.dateTime(new Date(`${month.month}-15T12:00:00+07:00`), { month: "long", year: "numeric" })}
                  </td>
                  <td className="mono-figure py-2.5 pr-4 text-right">{month.sales}</td>
                  <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(month.gross)}</td>
                  <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(month.fees)}</td>
                  <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(month.shipping)}</td>
                  <td className="mono-figure py-2.5 text-right">{money.currency(month.discounts)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid gap-12 lg:grid-cols-2">
        {[
          { title: t("financeByCategory"), rows: categories },
          { title: t("financeBySeller"), rows: sellers },
        ].map(({ title, rows }) => (
          <div key={title}>
            <h2 className="display text-2xl">{title}</h2>
            {rows.length === 0 ? (
              <p className="text-dim mt-4 text-sm">{t("financeNoSales")}</p>
            ) : (
              <ul className="border-line divide-line mt-4 divide-y border-y">
                {rows.map((row) => (
                  <li key={row.name} className="flex items-baseline justify-between gap-4 py-2.5 text-sm">
                    <span className="min-w-0 truncate">{row.name}</span>
                    <span className="text-dim shrink-0 text-xs">
                      {t("financeShareLine", { sales: row.sales, fees: money.currency(row.fees) })}
                    </span>
                    <span className="mono-figure shrink-0">{money.currency(row.gross)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
