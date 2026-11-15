"use client";

import { Loader2, ShieldQuestion } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
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
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { setUserBlocked } from "@/services/admin-service";
import { clearShillPair, listShillSuspects, type ShillSuspect } from "@/services/trust-service";

type Decision = { suspect: ShillSuspect; action: "clear" | "block" } | null;

/**
 * EN: Seller–bidder pairs whose bidding looks like a seller pushing up their own prices. The admin either clears the pair
 *     (hidden until the bidder bids on that seller again) or blocks the bidder's account. Nothing happens on its own.
 * VI: Các cặp người bán–người trả giá có cách trả giá giống người bán tự đẩy giá lô của mình. Admin xác nhận không có vấn
 *     đề (ẩn tới khi người đó lại trả giá lô của người bán này) hoặc khoá tài khoản người trả giá. Không có gì tự xảy ra.
 */
export default function AdminTrustPage() {
  const t = useTranslations("admin");
  const format = useFormatter();
  const { state, refresh } = useAsyncData("admin-shill", listShillSuspects);
  const [decision, setDecision] = useState<Decision>(null);

  return (
    <>
      <PageHeader title={t("trustTitle")} description={t("trustSubtitle")} />

      <div className="mt-8">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={ShieldQuestion} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={ShieldQuestion} title={t("trustEmptyTitle")} description={t("trustEmptyBody")} />
        ) : (
          <ul className="border-line divide-line divide-y border-y">
            {state.data.map((suspect) => (
              <li
                key={`${suspect.seller.id}-${suspect.bidder.id}`}
                className="flex flex-wrap items-start gap-x-6 gap-y-3 py-4"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm">
                    <span className="label-sm text-dim">{t("trustBidder")}</span>{" "}
                    <span className="font-semibold">{suspect.bidder.name}</span>{" "}
                    <span className="text-dim">{suspect.bidder.email}</span>
                  </p>
                  <p className="mt-1 text-sm">
                    <span className="label-sm text-dim">{t("trustSeller")}</span> {suspect.seller.name}{" "}
                    <span className="text-dim">{suspect.seller.email}</span>
                  </p>
                  <ul className="mt-2 flex flex-wrap gap-1.5">
                    {suspect.signals.map((signal) => (
                      <li key={signal} className="border-line label-sm border px-2 py-0.5">
                        {t(`trustSignal.${signal}`)}
                      </li>
                    ))}
                  </ul>
                  <p className="text-muted-foreground mt-2 text-xs">
                    {t("trustNumbers", { bids: suspect.bids, lots: suspect.lots, wins: suspect.wins })} ·{" "}
                    {format.dateTime(new Date(suspect.lastBid), { dateStyle: "medium", timeStyle: "short" })}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <span className="figure text-lg" title={t("trustScore")}>
                    {suspect.score}
                  </span>
                  <Button size="sm" variant="outline" onClick={() => setDecision({ suspect, action: "clear" })}>
                    {t("trustClear")}
                  </Button>
                  <Button size="sm" variant="destructive" onClick={() => setDecision({ suspect, action: "block" })}>
                    {t("trustBlock")}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <DecisionDialog
        key={decision ? `${decision.suspect.bidder.id}-${decision.action}` : "none"}
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
  const [note, setNote] = useState("");
  const [isPending, startTransition] = useTransition();

  function confirm() {
    if (!decision) return;
    const { suspect, action } = decision;
    startTransition(async () => {
      try {
        if (action === "clear") await clearShillPair(suspect.seller.id, suspect.bidder.id, note);
        else await setUserBlocked(suspect.bidder.id, true);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(action === "clear" ? t("trustCleared") : t("trustBlocked", { name: suspect.bidder.name }));
      onClose();
      onDone();
    });
  }

  return (
    <Dialog open={decision !== null} onOpenChange={(open) => (open ? null : onClose())}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{decision?.action === "block" ? t("trustBlockTitle") : t("trustClearTitle")}</DialogTitle>
          <DialogDescription>
            {decision?.action === "block"
              ? t("trustBlockBody", { name: decision.suspect.bidder.name })
              : t("trustClearBody")}
          </DialogDescription>
        </DialogHeader>
        {decision?.action === "clear" ? (
          <FormField label={t("trustNote")}>
            {(field) => (
              <Textarea {...field} value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />
            )}
          </FormField>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant={decision?.action === "block" ? "destructive" : "default"} onClick={confirm} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {decision?.action === "block" ? t("trustBlock") : t("trustClear")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
