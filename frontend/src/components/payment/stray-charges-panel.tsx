"use client";

import { AlertTriangle, Loader2, Undo2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { listStrayCharges, refundStrayCharge, type StrayCharge } from "@/services/admin-service";

import { RefundTransfer } from "./refund-transfer";

/**
 * EN: Money VNPay took that no payment could use, waiting to be sent back. Shown above the orders only while there is
 *     some; each is refunded like an order, by transfer to the buyer's refund account and the bank's reference.
 * VI: Tiền VNPay đã thu mà không khoản nào dùng được, đang chờ trả lại. Chỉ hiện phía trên danh sách đơn khi còn khoản
 *     nào; mỗi khoản được hoàn như một đơn, bằng chuyển khoản vào tài khoản nhận hoàn tiền của người mua kèm mã giao dịch.
 */
export function StrayChargesPanel() {
  const t = useTranslations("admin");
  const money = useMoney();
  const format = useFormatter();
  const { state, refresh } = useAsyncData("admin-stray-charges", listStrayCharges);
  const [refunding, setRefunding] = useState<StrayCharge | null>(null);

  if (state.status !== "success") return null;
  const waiting = state.data.filter((charge) => charge.refundedAt === null);
  if (waiting.length === 0) return null;

  return (
    <section
      className="border-warning mt-6 border-l-2 py-2 pl-4"
      aria-labelledby="stray-heading"
    >
      <h2 id="stray-heading" className="flex items-center gap-2 text-base font-semibold">
        <AlertTriangle className="text-warning size-4" aria-hidden="true" />
        {t("strayTitle", { count: waiting.length })}
      </h2>
      <p className="text-muted-foreground mt-1 max-w-2xl text-sm">{t("strayBody")}</p>
      <ul className="divide-line mt-3 divide-y">
        {waiting.map((charge) => (
          <li key={charge.id} className="flex flex-wrap items-center gap-x-6 gap-y-2 py-3">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold">
                {charge.auction ? (
                  <Link href={`/auctions/${charge.auctionId}`} className="hover:underline">
                    {charge.auction.product.name}
                  </Link>
                ) : (
                  charge.auctionId
                )}
              </p>
              <p className="text-muted-foreground text-xs">
                {charge.buyerName} · {charge.buyerEmail} ·{" "}
                {charge.reason === "LATE" ? t("strayLate") : t("strayDuplicate")} ·{" "}
                {format.dateTime(new Date(charge.createdAt), { dateStyle: "medium", timeStyle: "short" })}
              </p>
            </div>
            <span className="mono-figure text-sm font-semibold">{money.currency(charge.amount)}</span>
            <Button size="sm" variant="outline" onClick={() => setRefunding(charge)}>
              <Undo2 className="size-4" />
              {t("refund")}
            </Button>
          </li>
        ))}
      </ul>

      <StrayRefundDialog
        key={refunding?.id ?? "none"}
        charge={refunding}
        onClose={() => setRefunding(null)}
        onRefunded={refresh}
      />
    </section>
  );
}

function StrayRefundDialog({
  charge,
  onClose,
  onRefunded,
}: {
  charge: StrayCharge | null;
  onClose: () => void;
  onRefunded: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function refund() {
    if (!charge) return;
    if (!reference.trim()) {
      setError(tc("required"));
      return;
    }
    startTransition(async () => {
      try {
        await refundStrayCharge(charge.id, reference);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onRefunded();
      toast.success(t("strayRefunded"));
    });
  }

  return (
    <Dialog open={charge !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("strayRefundTitle")}</DialogTitle>
          <DialogDescription>
            {t("strayRefundBody", { name: charge?.auction?.product.name ?? "—" })}
          </DialogDescription>
        </DialogHeader>

        {charge ? (
          <RefundTransfer
            account={{ status: "success", data: charge.buyerAccount, error: null }}
            amount={charge.amount}
            reference={reference}
            error={error}
            disabled={isPending}
            onReference={(value) => {
              setReference(value);
              setError(null);
            }}
          />
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant="destructive" onClick={refund} disabled={isPending || !charge?.buyerAccount}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("confirmRefund")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
