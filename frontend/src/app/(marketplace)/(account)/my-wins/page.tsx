"use client";

import { Loader2, Trophy } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { ApiErrorState } from "@/components/common/api-error-state";
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
import { useApiErrorMessage, useEnumLabels } from "@/hooks/use-labels";
import { formatCurrency } from "@/lib/format";

import { cn } from "@/lib/utils";
import {
  acceptOffer,
  declineOffer,
  listMyOffers,
  listMyWins,
  type MyWinEntry,
  type OfferEntry,
} from "@/services/account-service";

export default function MyWinsPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("my-wins", listMyWins);

  return (
    <>
      <PageHeader title={t("winsTitle")} description={t("winsSubtitle")} />

      <Offers onAccepted={refresh} />

      <div className="mt-6">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <ApiErrorState icon={Trophy} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Trophy}
            title={t("winsEmptyTitle")}
            description={t("winsEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <WinRow key={entry.auction.id} entry={entry} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

/**
 * EN: Lots offered to the reader because their winner did not pay (spec §17). Shown only while one is open.
 * VI: Các lô được đề nghị cho người đọc vì người thắng không trả (spec §17). Chỉ hiện khi còn đề nghị đang mở.
 */
function Offers({ onAccepted }: { onAccepted: () => void }) {
  const t = useTranslations("account");
  const { state, refresh } = useAsyncData("my-offers", listMyOffers);
  // EN: Kept after closing, so the dialog does not change its words while it fades out.
  // VI: Giữ lại sau khi đóng, để hộp thoại không đổi chữ trong lúc mờ dần.
  const [answering, setAnswering] = useState<{ entry: OfferEntry; accept: boolean } | null>(null);
  const [open, setOpen] = useState(false);

  if (state.status !== "success") return null;
  const pending = state.data.filter((entry) => entry.offer.status === "PENDING" && entry.auction);
  if (pending.length === 0) return null;

  return (
    <section className="mt-10" aria-labelledby="offers-heading">
      <h2 id="offers-heading" className="display text-2xl">
        {t("offersTitle")}
      </h2>
      <p className="text-muted-foreground mt-2 max-w-xl text-sm">{t("offersBody")}</p>
      <ul className="mt-6">
        {pending.map((entry) => (
          <OfferRow
            key={entry.offer.id}
            entry={entry}
            onAnswer={(accept) => {
              setAnswering({ entry, accept });
              setOpen(true);
            }}
          />
        ))}
      </ul>

      <AnswerDialog
        open={open}
        answering={answering}
        onClose={() => setOpen(false)}
        onAnswered={(accepted) => {
          refresh();
          if (accepted) onAccepted();
        }}
      />
    </section>
  );
}

function OfferRow({ entry, onAnswer }: { entry: OfferEntry; onAnswer: (accept: boolean) => void }) {
  const t = useTranslations("account");
  const format = useFormatter();

  return (
    <AuctionListItem
      auction={entry.auction!}
      meta={{
        label: t("offerYourBid"),
        value: (
          <span className="flex flex-col">
            <span>{formatCurrency(entry.offer.amount)}</span>
            <span className="text-muted-foreground text-[11px] font-normal">
              {t("offerAnswerBy", {
                date: format.dateTime(new Date(entry.offer.expiresAt), { dateStyle: "medium", timeStyle: "short" }),
              })}
            </span>
          </span>
        ),
      }}
      actions={
        <>
          <Button size="sm" variant="outline" onClick={() => onAnswer(false)}>
            {t("offerDecline")}
          </Button>
          <Button size="sm" onClick={() => onAnswer(true)}>
            {t("offerAccept")}
          </Button>
        </>
      }
      className="border-signal-text/30"
    />
  );
}

function AnswerDialog({
  open,
  answering,
  onClose,
  onAnswered,
}: {
  open: boolean;
  answering: { entry: OfferEntry; accept: boolean } | null;
  onClose: () => void;
  onAnswered: (accepted: boolean) => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  const accept = answering?.accept ?? false;
  const name = answering?.entry.auction?.product.name ?? "";
  const amount = formatCurrency(answering?.entry.offer.amount ?? 0);

  function answer() {
    if (!answering) return;
    startTransition(async () => {
      try {
        await (accept ? acceptOffer : declineOffer)(answering.entry.offer.id);
      } catch (error) {
        toast.error(errorMessage(error));
        onClose();
        onAnswered(false);
        return;
      }
      onClose();
      onAnswered(accept);
      if (accept) toast.success(t("offerAccepted"), { description: t("offerAcceptedBody") });
      else toast.success(t("offerDeclined"));
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{accept ? t("offerAcceptTitle", { name, amount }) : t("offerDeclineTitle")}</DialogTitle>
          <DialogDescription>{accept ? t("offerAcceptBody") : t("offerDeclineBody")}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant={accept ? "default" : "destructive"} onClick={answer} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {accept ? t("offerAccept") : t("offerDecline")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function WinRow({ entry }: { entry: MyWinEntry }) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const status = entry.payment?.status ?? "PENDING";
  const needsPayment = status === "PENDING" || status === "FAILED";
  // EN: A second-chance win is paid at the buyer's own bid, below the hammer price shown on the row.
  // VI: Thắng nhờ cơ hội thứ hai thì trả theo giá của chính người mua, thấp hơn giá chốt hiện trên dòng.
  const ownPrice = entry.winningBid !== entry.auction.currentPrice;

  return (
    <AuctionListItem
      auction={entry.auction}
      meta={{
        label: t("paymentStatus"),
        value: (
          <span className="flex flex-col">
            <span
              className={cn(
                "text-[13px]",
                status === "SUCCESS" && "text-success",
                needsPayment && "text-danger-text",
              )}
            >
              {labels.paymentStatus(status)}
            </span>
            {ownPrice ? (
              <span className="text-[11px] font-normal">
                {t("secondChancePrice", { amount: formatCurrency(entry.winningBid) })}
              </span>
            ) : null}
            {entry.payment ? (
              <span className="text-muted-foreground text-[11px] font-normal">
                {status === "SUCCESS"
                  ? t("paidOn", {
                      date: format.dateTime(new Date(entry.payment.updatedAt), {
                        dateStyle: "medium",
                      }),
                    })
                  : t("paymentDue", {
                      date: format.dateTime(new Date(entry.payment.expiredAt), {
                        dateStyle: "medium",
                      }),
                    })}
              </span>
            ) : null}
          </span>
        ),
      }}
      actions={
        needsPayment && entry.order ? (
          <Button asChild size="sm">
            <Link href={`/orders?highlight=${entry.order.id}`}>
              {t("payNow")}
            </Link>
          </Button>
        ) : (
          <Button asChild size="sm" variant="outline">
            <Link href="/orders">{tc("viewDetails")}</Link>
          </Button>
        )
      }
      className={needsPayment ? "border-danger-text/30" : undefined}
    />
  );
}
