"use client";

import { CreditCard, Loader2, PackageCheck, Receipt } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
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
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { formatCurrency } from "@/lib/format";
import { confirmReceipt, listOrders, type OrderEntry } from "@/services/order-service";
import { payPayment } from "@/services/payment-service";

export default function OrdersPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("orders", listOrders);
  const [payingFor, setPayingFor] = useState<OrderEntry | null>(null);

  return (
    <>
      <PageHeader title={t("ordersTitle")} description={t("ordersSubtitle")} />

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState
            tone="error"
            icon={Receipt}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title={t("ordersEmptyTitle")}
            description={t("ordersEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <OrderRow
                key={entry.order.id}
                entry={entry}
                action={
                  <OrderActions
                    entry={entry}
                    onPay={() => setPayingFor(entry)}
                    onReceived={refresh}
                  />
                }
              />
            ))}
          </ul>
        )}
      </div>

      <PaymentDialog
        entry={payingFor}
        onClose={() => setPayingFor(null)}
        onSettled={refresh}
      />
    </>
  );
}

function OrderActions({
  entry,
  onPay,
  onReceived,
}: {
  entry: OrderEntry;
  onPay: () => void;
  onReceived: () => void;
}) {
  const t = useTranslations("account");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  const needsPayment = entry.payment?.status === "PENDING" || entry.payment?.status === "FAILED";

  if (needsPayment) {
    return (
      <Button size="sm" onClick={onPay}>
        <CreditCard className="size-4" />
        {t("payNow")}
      </Button>
    );
  }

  if (entry.order.status !== "PROCESSING") return null;

  // EN: The seller has shipped; only the buyer can say it arrived. / VI: Người bán đã gửi; chỉ người mua mới xác nhận được là đã nhận.
  function confirm() {
    startTransition(async () => {
      try {
        await confirmReceipt(entry.order.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(t("receiptConfirmed"), { description: t("receiptConfirmedBody") });
      onReceived();
    });
  }

  return (
    <Button size="sm" variant="outline" onClick={confirm} disabled={isPending}>
      {isPending ? <Loader2 className="size-4 animate-spin" /> : <PackageCheck className="size-4" />}
      {t("confirmReceipt")}
    </Button>
  );
}

function PaymentDialog({
  entry,
  onClose,
  onSettled,
}: {
  entry: OrderEntry | null;
  onClose: () => void;
  onSettled: () => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const [isPending, startTransition] = useTransition();

  function settle(outcome: "SUCCESS" | "FAILED") {
    if (!entry?.payment) return;

    startTransition(async () => {
      const result = await payPayment(entry.payment!.id, outcome);
      onClose();
      onSettled();

      if (result.ok) {
        toast.success(t("paymentSucceeded"), {
          description: t("paymentSucceededBody"),
        });
      } else {
        toast.error(t("paymentFailed"), { description: t("paymentFailedBody") });
      }
    });
  }

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("payTitle")}</DialogTitle>
          <DialogDescription>{t("payBody")}</DialogDescription>
        </DialogHeader>

        {entry ? (
          <div className="border-line flex items-center justify-between border-y px-4 py-4">
            <span className="text-muted-foreground text-sm">
              {t("orderTotal")}
            </span>
            <span className="figure text-2xl">
              {formatCurrency(entry.order.amount)}
            </span>
          </div>
        ) : null}

        <DialogFooter className="flex-col gap-2 sm:flex-col">
          <Button
            className="w-full"
            disabled={isPending}
            onClick={() => settle("SUCCESS")}
          >
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("paySuccess")}
          </Button>
          <Button
            variant="outline"
            className="w-full"
            disabled={isPending}
            onClick={() => settle("FAILED")}
          >
            {t("payFailed")}
          </Button>
          <Button
            variant="ghost"
            className="w-full"
            disabled={isPending}
            onClick={onClose}
          >
            {tc("cancel")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
