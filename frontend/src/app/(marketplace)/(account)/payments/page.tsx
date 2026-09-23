"use client";

import { CreditCard, Loader2, Wallet } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { AuctionClock } from "@/components/nexbid/auction-clock";
import { formatLot } from "@/components/nexbid/lot-number";
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
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import { cn } from "@/lib/utils";
import { listPayments, payPayment, type PaymentEntry } from "@/services/payment-service";

/**
 * Settlement, split from the order record (spec §36 lists both).
 *
 * Where Orders answers "what did I buy", this answers "what do I still owe,
 * and how long have I got" — so the outstanding total leads, and each row
 * carries the closing window rather than a fulfilment state.
 */
export default function PaymentsPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("payments", listPayments);
  const [settling, setSettling] = useState<PaymentEntry | null>(null);

  const entries = state.status === "success" ? state.data : [];
  const outstanding = entries
    .filter((entry) => entry.payment.status === "PENDING")
    .reduce((total, entry) => total + entry.payment.amount, 0);

  return (
    <>
      <PageHeader
        title={t("paymentsTitle")}
        description={t("paymentsSubtitle")}
      />

      {outstanding > 0 ? (
        <div className="border-line mt-10 flex flex-wrap items-baseline justify-between gap-x-8 gap-y-2 border-b pb-6">
          <span className="label-sm text-dim">{t("paymentTotalDue")}</span>
          <span className="display text-signal-text text-[clamp(2rem,5vw,3.5rem)]">
            {formatCurrency(outstanding)}
          </span>
        </div>
      ) : null}

      <div className={outstanding > 0 ? "mt-8" : "mt-10"}>
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState
            tone="error"
            icon={Wallet}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : entries.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title={t("paymentsEmptyTitle")}
            description={t("paymentsEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {entries.map((entry) => (
              <PaymentRow
                key={entry.payment.id}
                entry={entry}
                onSettle={() => setSettling(entry)}
              />
            ))}
          </ul>
        )}
      </div>

      <SettleDialog
        entry={settling}
        onClose={() => setSettling(null)}
        onSettled={refresh}
      />
    </>
  );
}

function PaymentRow({
  entry,
  onSettle,
}: {
  entry: PaymentEntry;
  onSettle: () => void;
}) {
  const t = useTranslations("account");
  const labels = useEnumLabels();
  const format = useFormatter();

  const { payment, order, auction } = entry;
  const cover = auction?.product.images[0];

  const due = payment.status === "PENDING";
  const expired = payment.status === "EXPIRED";

  return (
    <li className="border-line group border-t py-5 last:border-b">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-4">
        {auction ? (
          <span className="mono-figure text-dim hidden w-10 shrink-0 text-xs sm:block">
            {formatLot(auction.lotNumber)}
          </span>
        ) : null}

        <span className="on-media bg-surface relative size-16 shrink-0 overflow-hidden">
          {cover ? (
            <Image
              unoptimized={isLocalImage(cover.url)}
              src={cover.url}
              alt=""
              fill
              sizes="64px"
              className="object-cover"
            />
          ) : null}
        </span>

        <span className="min-w-[10rem] flex-1">
          <span className="mono-figure text-dim block text-[11px]">
            {t("paymentReference", { id: payment.id.slice(-8).toUpperCase() })}
          </span>
          <h3 className="display mt-1 text-lg sm:text-xl">
            {auction ? (
              <Link
                href={`/auctions/${auction.id}`}
                className="hover:text-signal-text transition-colors"
              >
                {auction.product.name}
              </Link>
            ) : (
              "—"
            )}
          </h3>
        </span>

        <span className="shrink-0">
          <span className="label-sm text-dim mb-1 block">
            {t("paymentAmount")}
          </span>
          <span className="figure block text-lg">
            {formatCurrency(payment.amount)}
          </span>
        </span>

        {/* The window is the whole point of this screen, so it is a clock. */}
        <span className="w-32 shrink-0">
          <span className="label-sm text-dim mb-1 block">
            {due ? t("paymentWindow") : t("paymentWindowClosed")}
          </span>
          {due ? (
            <AuctionClock endTime={payment.expiredAt} variant="inline" />
          ) : (
            <span className="mono-figure text-dim text-xs">
              {format.dateTime(new Date(payment.updatedAt), {
                dateStyle: "medium",
              })}
            </span>
          )}
        </span>

        <span className="w-28 shrink-0">
          <span
            className={cn(
              "label",
              payment.status === "SUCCESS" && "text-success",
              (due || payment.status === "FAILED") && "text-danger-text",
              expired && "text-dim",
            )}
          >
            {labels.paymentStatus(payment.status)}
          </span>
        </span>

        <span className="flex shrink-0 items-center gap-2">
          {due || payment.status === "FAILED" ? (
            <Button onClick={onSettle}>
              <CreditCard className="size-4" />
              {t("payNow")}
            </Button>
          ) : order ? (
            <Button asChild variant="outline">
              <Link href="/orders">{t("viewOrder")}</Link>
            </Button>
          ) : null}
        </span>
      </div>

      {expired ? (
        <p className="text-dim mt-3 max-w-xl text-sm leading-relaxed">
          {t("paymentExpiredNotice")}
        </p>
      ) : null}
    </li>
  );
}

function SettleDialog({
  entry,
  onClose,
  onSettled,
}: {
  entry: PaymentEntry | null;
  onClose: () => void;
  onSettled: () => void;
}) {
  const t = useTranslations("account");
  const [isPending, startTransition] = useTransition();

  function settle(outcome: "SUCCESS" | "FAILED") {
    if (!entry) return;
    const paymentId = entry.payment.id;

    startTransition(async () => {
      const result = await payPayment(paymentId, outcome);
      onClose();
      onSettled();

      if (result.ok) {
        toast.success(t("paymentSucceeded"), {
          description: t("paymentSucceededBody"),
        });
      } else {
        toast.error(t("paymentFailed"), {
          description: t("paymentFailedBody"),
        });
      }
    });
  }

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="display text-2xl">{t("payTitle")}</DialogTitle>
          <DialogDescription>{t("payBody")}</DialogDescription>
        </DialogHeader>

        {entry ? (
          <div className="border-line flex items-center justify-between border-y px-4 py-4">
            <span className="label-sm text-dim">{t("paymentAmount")}</span>
            <span className="figure text-2xl">
              {formatCurrency(entry.payment.amount)}
            </span>
          </div>
        ) : null}

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => settle("FAILED")}
            disabled={isPending}
          >
            {t("payFailed")}
          </Button>
          <Button onClick={() => settle("SUCCESS")} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("paySuccess")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
