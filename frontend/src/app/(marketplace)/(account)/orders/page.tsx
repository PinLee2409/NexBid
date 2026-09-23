"use client";

import { CreditCard, Loader2, Receipt } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
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
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { isLocalImage } from "@/lib/images";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { listOrders, type OrderEntry } from "@/services/order-service";
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
                onPay={() => setPayingFor(entry)}
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

function OrderRow({
  entry,
  onPay,
}: {
  entry: OrderEntry;
  onPay: () => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const cover = entry.auction?.product.images[0];
  const needsPayment =
    entry.payment?.status === "PENDING" || entry.payment?.status === "FAILED";

  return (
    <li
      className={cn(
        "border-line flex flex-wrap items-center gap-x-6 gap-y-4 border-t py-5 last:border-b",
      )}
    >
      <div className="on-media bg-surface relative size-16 shrink-0 overflow-hidden">
        {cover ? (
          <Image
            unoptimized={isLocalImage(cover.url)}
            src={cover.url}
            alt={cover.alt}
            fill
            sizes="64px"
            className="object-cover"
          />
        ) : null}
      </div>

      <div className="min-w-[10rem] flex-1">
        <p className="mono-figure text-dim text-[11px]">
          {t("orderNumber", { id: entry.order.id.slice(-8).toUpperCase() })}
        </p>
        <h3 className="display mt-1 text-lg sm:text-xl">
          {entry.auction ? (
            <Link
              href={`/auctions/${entry.auction.id}`}
              className="hover:text-signal-text transition-colors"
            >
              {entry.auction.product.name}
            </Link>
          ) : (
            "—"
          )}
        </h3>
        <p className="mono-figure text-dim mt-1.5 text-[11px]">
          {t("orderPlaced", {
            date: format.dateTime(new Date(entry.order.createdAt), {
              dateStyle: "medium",
            }),
          })}
        </p>
      </div>

      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{t("orderTotal")}</p>
        <p className="figure text-lg">
          {formatCurrency(entry.order.amount)}
        </p>
      </div>

      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{tc("status")}</p>
        <p
          className={cn(
            "label",
            entry.order.status === "COMPLETED" || entry.order.status === "PAID"
              ? "text-success"
              : needsPayment
                ? "text-danger-text"
                : undefined,
          )}
        >
          {labels.orderStatus(entry.order.status)}
        </p>
      </div>

      {needsPayment ? (
        <Button size="sm" onClick={onPay}>
          <CreditCard className="size-4" />
          {t("payNow")}
        </Button>
      ) : null}
    </li>
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
