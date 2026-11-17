"use client";

import { Download, FileText, Loader2, Printer } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { downloadCsv } from "@/services/finance-service";
import { getStatement } from "@/services/payout-service";

/** EN: This month in Vietnam, yyyy-MM. / VI: Tháng hiện tại theo giờ Việt Nam, yyyy-MM. */
function thisMonth(): string {
  return new Date(Date.now() + 7 * 3_600_000).toISOString().slice(0, 7);
}

/**
 * EN: A seller's monthly statement for their books and tax return: every sale completed that month and the transfers
 *     NexBid made. Download it as CSV for a spreadsheet, or print it ("Save as PDF").
 * VI: Bảng kê tháng của người bán để ghi sổ và kê khai thuế: mọi giao dịch hoàn tất trong tháng và các lần NexBid chuyển
 *     khoản. Tải CSV cho bảng tính, hoặc in ("Lưu dưới dạng PDF").
 */
export default function SellerStatementsPage() {
  const t = useTranslations("seller");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [month, setMonth] = useState(thisMonth);
  const { state } = useAsyncData(`statement-${month}`, () => getStatement(month));
  const [isPending, startTransition] = useTransition();

  function download() {
    startTransition(async () => {
      try {
        await downloadCsv(`/api/seller/statements.csv?month=${month}`, `nexbid-statement-${month}.csv`);
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <div className="print:hidden">
        <PageHeader
          title={t("statementsTitle")}
          description={t("statementsSubtitle")}
          actions={
            <>
              <Button variant="outline" onClick={download} disabled={isPending}>
                {isPending ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                {t("statementsCsv")}
              </Button>
              <Button variant="outline" onClick={() => window.print()}>
                <Printer className="size-4" />
                {t("statementsPrint")}
              </Button>
            </>
          }
        />
        <div className="mt-6 flex items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="statement-month" className="label-sm text-dim">
              {t("statementsMonth")}
            </Label>
            <Input
              id="statement-month"
              type="month"
              value={month}
              max={thisMonth()}
              onChange={(event) => event.target.value && setMonth(event.target.value)}
              className="h-10 w-44"
            />
          </div>
        </div>
      </div>

      <div className="mt-10" data-testid="statement">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={FileText} error={state.error} />
        ) : (
          <>
            <h2 className="display text-2xl">
              {t("statementsHeading", {
                month: format.dateTime(new Date(`${month}-15T12:00:00+07:00`), { month: "long", year: "numeric" }),
              })}
            </h2>
            <p className="text-dim mt-1 text-sm">{state.data.seller}</p>
            {state.data.lines.length === 0 ? (
              <p className="text-dim mt-6 text-sm">{t("statementsEmpty")}</p>
            ) : (
              <div className="mt-6 overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead>
                    <tr className="border-line text-dim border-b text-left">
                      <th className="label-sm py-3 pr-4 font-normal">{t("statementsDate")}</th>
                      <th className="label-sm py-3 pr-4 font-normal">{t("statementsLot")}</th>
                      <th className="label-sm py-3 pr-4 text-right font-normal">{t("statementsSale")}</th>
                      <th className="label-sm py-3 pr-4 text-right font-normal">{t("statementsFee")}</th>
                      <th className="label-sm py-3 pr-4 text-right font-normal">{t("statementsShipping")}</th>
                      <th className="label-sm py-3 text-right font-normal">{t("statementsNet")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {state.data.lines.map((line) => (
                      <tr key={line.orderId} className="border-line border-b">
                        <td className="mono-figure py-2.5 pr-4">{format.dateTime(new Date(line.at), { dateStyle: "short" })}</td>
                        <td className="py-2.5 pr-4">{line.lot}</td>
                        <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(line.gross)}</td>
                        <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(line.fee)}</td>
                        <td className="mono-figure py-2.5 pr-4 text-right">{money.currency(line.shipping)}</td>
                        <td className="mono-figure py-2.5 text-right">{money.currency(line.net)}</td>
                      </tr>
                    ))}
                    <tr className="font-medium">
                      <td className="py-3 pr-4" colSpan={2}>
                        {t("statementsTotal")}
                      </td>
                      <td className="mono-figure py-3 pr-4 text-right">{money.currency(state.data.gross)}</td>
                      <td className="mono-figure py-3 pr-4 text-right">{money.currency(state.data.fees)}</td>
                      <td className="mono-figure py-3 pr-4 text-right">{money.currency(state.data.shipping)}</td>
                      <td className="mono-figure py-3 text-right">{money.currency(state.data.net)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            )}
            <p className="mt-6 text-sm">
              {t("statementsTransferred", { amount: money.currency(state.data.transferred), count: state.data.transfers.length })}
            </p>
            <p className="text-dim mt-6 text-xs leading-relaxed">{t("statementsNote")}</p>
          </>
        )}
      </div>
    </>
  );
}
