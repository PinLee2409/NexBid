"use client";

import { Flag, Loader2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { dismissReport, listReports, takeDownLot, type LotReport, type ReportStatus } from "@/services/report-service";

const TABS: ReportStatus[] = ["OPEN", "REMOVED", "DISMISSED"];

type Decision = { report: LotReport; action: "dismiss" | "takeDown" } | null;

/**
 * EN: Reports about lots. An open report is dismissed (the lot stays) or upheld (the lot is taken down, every open report
 *     on it closes, and the seller is told why). Reporters hear the outcome either way.
 * VI: Báo cáo về lô. Báo cáo đang mở được bỏ qua (lô vẫn giữ) hoặc chấp nhận (lô bị gỡ, mọi báo cáo đang mở của lô đóng
 *     lại, và người bán được báo lý do). Người báo cáo được báo kết quả trong cả hai trường hợp.
 */
export default function AdminReportsPage() {
  const t = useTranslations("admin");
  const ta = useTranslations("auction");
  const format = useFormatter();
  const [tab, setTab] = useState<ReportStatus>("OPEN");
  const { state, refresh } = useAsyncData(`admin-reports-${tab}`, () => listReports(tab));
  const [decision, setDecision] = useState<Decision>(null);

  return (
    <>
      <PageHeader title={t("reportsTitle")} description={t("reportsSubtitle")} />

      <Tabs value={tab} onValueChange={(value) => setTab(value as ReportStatus)} className="mt-6 block">
        <TabsList>
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`reportStatus.${key}`)}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-8">
          {state.status === "loading" ? (
            <AuctionListRowSkeleton />
          ) : state.status === "error" ? (
            <ApiErrorState icon={Flag} error={state.error} />
          ) : state.data.length === 0 ? (
            <EmptyState icon={Flag} title={t("reportsEmptyTitle")} description={t("reportsEmptyBody")} />
          ) : (
            <ul className="border-line divide-line divide-y border-y">
              {state.data.map((report) => (
                <li key={report.id} className="flex flex-wrap items-start gap-x-6 gap-y-3 py-4">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">
                      {report.auction && report.status === "OPEN" ? (
                        <Link href={`/auctions/${report.auctionId}`} className="hover:underline">
                          {report.auction.product.name}
                        </Link>
                      ) : (
                        (report.auction?.product.name ?? report.auctionId)
                      )}
                    </p>
                    <p className="text-muted-foreground mt-0.5 text-xs">
                      {ta(`reportReason.${report.reason}`)} · {report.reporter} ·{" "}
                      {format.dateTime(new Date(report.createdAt), { dateStyle: "medium", timeStyle: "short" })}
                    </p>
                    <p className="mt-2 text-sm leading-relaxed whitespace-pre-line">{report.details}</p>
                    {report.note ? (
                      <p className="text-muted-foreground mt-2 text-xs">
                        <span className="label-sm text-dim">{t("reportNote")}</span> {report.note}
                      </p>
                    ) : null}
                  </div>
                  {report.status === "OPEN" ? (
                    <div className="flex shrink-0 items-center gap-2">
                      <Button size="sm" variant="outline" onClick={() => setDecision({ report, action: "dismiss" })}>
                        {t("reportDismiss")}
                      </Button>
                      <Button size="sm" variant="destructive" onClick={() => setDecision({ report, action: "takeDown" })}>
                        {t("reportTakeDown")}
                      </Button>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>

      <DecisionDialog
        key={decision ? `${decision.report.id}-${decision.action}` : "none"}
        decision={decision}
        onClose={() => setDecision(null)}
        onDone={refresh}
      />
    </>
  );
}

function DecisionDialog({ decision, onClose, onDone }: { decision: Decision; onClose: () => void; onDone: () => void }) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const takeDown = decision?.action === "takeDown";

  function submit() {
    if (!decision) return;
    if (takeDown && !text.trim()) {
      setError(tc("required"));
      return;
    }
    startTransition(async () => {
      try {
        if (takeDown) await takeDownLot(decision.report.id, text);
        else await dismissReport(decision.report.id, text);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onDone();
      toast.success(takeDown ? t("reportTakenDown") : t("reportDismissed"));
    });
  }

  return (
    <Dialog open={decision !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{takeDown ? t("reportTakeDownTitle") : t("reportDismissTitle")}</DialogTitle>
          <DialogDescription>
            {takeDown
              ? t("reportTakeDownBody", { name: decision?.report.auction?.product.name ?? "—" })
              : t("reportDismissBody")}
          </DialogDescription>
        </DialogHeader>
        <FormField
          label={takeDown ? t("reportTakeDownReason") : t("reportDismissNote")}
          error={error ?? undefined}
          required={takeDown}
        >
          {(field) => (
            <Textarea
              {...field}
              value={text}
              maxLength={500}
              rows={3}
              disabled={isPending}
              onChange={(event) => {
                setText(event.target.value);
                setError(null);
              }}
            />
          )}
        </FormField>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant={takeDown ? "destructive" : "default"} onClick={submit} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {takeDown ? t("reportTakeDown") : t("reportDismiss")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
