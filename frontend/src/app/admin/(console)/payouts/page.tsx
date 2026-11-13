"use client";

import { Banknote, Loader2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { TransferDetails } from "@/components/payment/transfer-details";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { listPayoutsDue, listRecentPayouts, recordPayout, type PayoutDue } from "@/services/payout-service";

async function load() {
  const [due, recent] = await Promise.all([listPayoutsDue(), listRecentPayouts()]);
  return { due, recent };
}

/**
 * EN: Sellers waiting for their money. The admin transfers the balance from NexBid's bank account, then records the
 *     transfer here with the bank's reference; the seller is emailed.
 * VI: Người bán đang chờ tiền. Admin chuyển số dư từ tài khoản ngân hàng của NexBid, rồi ghi nhận lần chuyển ở đây kèm
 *     mã giao dịch của ngân hàng; người bán nhận được email.
 */
export default function AdminPayoutsPage() {
  const t = useTranslations("admin");
  const money = useMoney();
  const format = useFormatter();
  const { state, refresh } = useAsyncData("admin-payouts", load);
  const [paying, setPaying] = useState<PayoutDue | null>(null);
  const [open, setOpen] = useState(false);

  return (
    <>
      <PageHeader title={t("payoutsTitle")} description={t("payoutsSubtitle")} />

      <section aria-labelledby="due-heading" className="mt-10">
        <h2 id="due-heading" className="display border-line border-b pb-4 text-2xl">
          {t("payoutsDue")}
        </h2>
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 3 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <div className="mt-8">
            <ApiErrorState icon={Banknote} error={state.error} />
          </div>
        ) : state.data.due.length === 0 ? (
          <div className="mt-8">
            <EmptyState icon={Banknote} title={t("payoutsEmptyTitle")} description={t("payoutsEmptyBody")} />
          </div>
        ) : (
          <ul>
            {state.data.due.map((row) => (
              <li key={row.sellerId} className="border-line flex flex-wrap items-center gap-x-8 gap-y-3 border-b py-5">
                <div className="min-w-[12rem] flex-1">
                  <p className="font-medium">{row.sellerName}</p>
                  <p className="mono-figure text-dim mt-1 text-[11px]">{row.sellerEmail}</p>
                  <p className="text-muted-foreground mt-2 text-xs">
                    {t("payoutsOrders", {
                      count: row.orders,
                      date: format.dateTime(new Date(row.oldest), { dateStyle: "medium" }),
                    })}
                  </p>
                </div>
                <div className="min-w-[12rem] text-sm">
                  {row.account ? (
                    <>
                      <p>{row.account.bankName}</p>
                      <p className="mono-figure">{row.account.accountNumber}</p>
                      <p className="label-sm text-dim">{row.account.accountHolder}</p>
                    </>
                  ) : (
                    <p className="label-sm text-danger-text">{t("payoutsNoBank")}</p>
                  )}
                </div>
                <p className="figure text-xl">{money.currency(row.balance)}</p>
                <Button
                  size="sm"
                  disabled={!row.account}
                  onClick={() => {
                    setPaying(row);
                    setOpen(true);
                  }}
                >
                  <Banknote className="size-4" />
                  {t("payoutsPay")}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {state.status === "success" && state.data.recent.length > 0 ? (
        <section aria-labelledby="recent-heading" className="mt-16">
          <h2 id="recent-heading" className="display border-line border-b pb-4 text-2xl">
            {t("payoutsRecent")}
          </h2>
          <ul>
            {state.data.recent.map((payout) => (
              <li key={payout.id} className="border-line flex flex-wrap items-baseline gap-x-6 gap-y-1 border-b py-3 text-sm">
                <span className="mono-figure text-dim w-28 text-[11px]">
                  {format.dateTime(new Date(payout.createdAt), { dateStyle: "medium" })}
                </span>
                <span className="flex-1">
                  {payout.account.accountHolder} · {payout.account.bankName}
                </span>
                <span className="mono-figure text-dim text-[11px]">{payout.reference}</span>
                <span className="figure">{money.currency(payout.amount)}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <PayDialog key={paying?.sellerId ?? "none"} row={paying} open={open} onClose={() => setOpen(false)} onPaid={refresh} />
    </>
  );
}

function PayDialog({
  row,
  open,
  onClose,
  onPaid,
}: {
  row: PayoutDue | null;
  open: boolean;
  onClose: () => void;
  onPaid: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function pay() {
    if (!row) return;
    if (!reference.trim()) {
      setError(tc("required"));
      return;
    }
    startTransition(async () => {
      try {
        await recordPayout(row.sellerId, reference);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onPaid();
      toast.success(t("payoutsRecorded"), { description: t("payoutsRecordedBody") });
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("payoutsDialogTitle")}</DialogTitle>
          <DialogDescription>{t("payoutsDialogBody")}</DialogDescription>
        </DialogHeader>

        {row?.account ? <TransferDetails amount={row.balance} account={row.account} /> : null}

        <FormField label={t("payoutsReference")} hint={t("payoutsReferenceHint")} error={error ?? undefined} required>
          {(field) => (
            <Input
              {...field}
              value={reference}
              maxLength={80}
              onChange={(event) => {
                setReference(event.target.value);
                setError(null);
              }}
            />
          )}
        </FormField>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button onClick={pay} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("payoutsConfirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
