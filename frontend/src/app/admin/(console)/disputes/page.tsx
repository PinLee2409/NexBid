"use client";

import { Check, Loader2, ShieldAlert, Undo2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
import { PageHeader } from "@/components/layout/page-header";
import { RefundTransfer, useRefundAccount } from "@/components/payment/refund-transfer";
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
import { useMoney } from "@/hooks/use-money";
import { listDisputes, resolveDispute, type DisputeEntry } from "@/services/admin-service";
import type { DisputeStatus } from "@/types";

const TABS: DisputeStatus[] = ["OPEN", "REFUNDED", "REJECTED"];

/**
 * EN: Buyers' complaints, oldest first. The admin refunds the buyer — through VNPay when they paid that way — or lets
 *     the sale stand, with a note both sides see.
 * VI: Khiếu nại của người mua, cũ nhất trước. Admin hoàn tiền cho người mua — qua VNPay nếu họ đã trả qua đó — hoặc giữ
 *     giao dịch, kèm ghi chú cả hai bên đều thấy.
 */
export default function AdminDisputesPage() {
  const t = useTranslations("admin");
  const [tab, setTab] = useState<DisputeStatus>("OPEN");
  const { state, refresh } = useAsyncData(`admin-disputes-${tab}`, () => listDisputes(tab));
  const [deciding, setDeciding] = useState<{ row: DisputeEntry; refund: boolean } | null>(null);
  const [open, setOpen] = useState(false);
  const format = useFormatter();

  return (
    <>
      <PageHeader title={t("disputesTitle")} description={t("disputesSubtitle")} />

      <Tabs value={tab} onValueChange={(value) => setTab(value as DisputeStatus)} className="mt-6 block">
        <TabsList>
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`disputeTab.${key}`)}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-10">
          {state.status === "loading" ? (
            <div>
              {Array.from({ length: 3 }).map((_, index) => (
                <AuctionListRowSkeleton key={index} />
              ))}
            </div>
          ) : state.status === "error" ? (
            <ApiErrorState icon={ShieldAlert} error={state.error} />
          ) : state.data.length === 0 ? (
            <EmptyState icon={ShieldAlert} title={t("disputesEmptyTitle")} description={t("disputesEmptyBody")} />
          ) : (
            <ul>
              {state.data.map((row) => (
                <OrderRow
                  key={row.dispute.id}
                  entry={row.entry}
                  action={
                    row.dispute.status === "OPEN" ? (
                      <div className="flex shrink-0 flex-col items-end gap-2">
                        <p className="mono-figure text-dim text-[11px]">
                          {t("disputeOpenedAt", {
                            date: format.dateTime(new Date(row.dispute.createdAt), {
                              dateStyle: "medium",
                              timeStyle: "short",
                            }),
                          })}
                        </p>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setDeciding({ row, refund: false });
                              setOpen(true);
                            }}
                          >
                            <Check className="size-4" />
                            {t("disputeReject")}
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => {
                              setDeciding({ row, refund: true });
                              setOpen(true);
                            }}
                          >
                            <Undo2 className="size-4" />
                            {t("disputeRefund")}
                          </Button>
                        </div>
                      </div>
                    ) : null
                  }
                />
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>

      <DecideDialog
        key={deciding ? `${deciding.row.dispute.id}-${deciding.refund}` : "none"}
        deciding={deciding}
        open={open}
        onClose={() => setOpen(false)}
        onDone={refresh}
      />
    </>
  );
}

function DecideDialog({
  deciding,
  open,
  onClose,
  onDone,
}: {
  deciding: { row: DisputeEntry; refund: boolean } | null;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const money = useMoney();
  const errorMessage = useApiErrorMessage();
  const [note, setNote] = useState("");
  const [reference, setReference] = useState("");
  const [referenceError, setReferenceError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const refund = deciding?.refund ?? false;
  const entry = deciding?.row.entry;
  const shipped = entry?.order.shippedAt != null;
  // EN: Only a refund needs the buyer's account. / VI: Chỉ hoàn tiền mới cần tài khoản của người mua.
  const account = useRefundAccount(refund && entry ? entry.order.id : null);

  function decide() {
    if (!deciding) return;
    if (refund && !reference.trim()) {
      setReferenceError(tc("required"));
      return;
    }
    startTransition(async () => {
      try {
        await resolveDispute(deciding.row.dispute.id, refund, note, reference);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      onClose();
      onDone();
      toast.success(refund ? t("disputeRefunded") : t("disputeRejected"));
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{refund ? t("disputeRefundTitle") : t("disputeRejectTitle")}</DialogTitle>
          <DialogDescription>
            {refund
              ? t("disputeRefundBody", {
                  amount: money.currency(entry?.order.amount ?? 0),
                  name: entry?.auction?.product.name ?? "",
                })
              : shipped
                ? t("disputeRejectShippedBody")
                : t("disputeRejectUnshippedBody")}
          </DialogDescription>
        </DialogHeader>

        {refund && entry ? (
          <RefundTransfer
            account={account}
            amount={entry.order.amount}
            reference={reference}
            error={referenceError}
            disabled={isPending}
            onReference={(value) => {
              setReference(value);
              setReferenceError(null);
            }}
          />
        ) : null}

        <FormField label={t("disputeNoteLabel")} hint={t("disputeNoteHint")}>
          {(field) => (
            <Textarea {...field} rows={3} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} />
          )}
        </FormField>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button
            variant={refund ? "destructive" : "default"}
            onClick={decide}
            disabled={isPending || (refund && (account.status !== "success" || account.data === null))}
          >
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {refund ? t("disputeRefund") : t("disputeReject")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
