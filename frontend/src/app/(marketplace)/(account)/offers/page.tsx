"use client";

import { HandCoins, ShieldCheck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OfferRow } from "@/components/common/offer-row";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import {
  answerCounter,
  myDeposits,
  myOffers,
  type LotOffer,
} from "@/services/market-service";

/**
 * EN: The buyer's offers on buy-now lots, with the seller's counters to answer, and their deposits on expensive lots.
 * VI: Các đề nghị của người mua trên lô có giá mua ngay, kèm giá người bán trả lại để trả lời, và tiền cọc trên lô đắt.
 */
export default function MyOffersPage() {
  const t = useTranslations("deals");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const offers = useAsyncData("my-lot-offers", myOffers);
  const deposits = useAsyncData("my-deposits", myDeposits);

  function act(
    offer: LotOffer,
    action: "accept-counter" | "decline-counter" | "withdraw",
  ) {
    startTransition(async () => {
      try {
        await answerCounter(offer.id, action);
        toast.success(
          t(
            action === "accept-counter"
              ? "counterAccepted"
              : action === "withdraw"
                ? "offerWithdrawn"
                : "counterDeclined",
          ),
        );
        offers.refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <PageHeader title={t("myTitle")} description={t("mySubtitle")} />

      <section className="mt-8" aria-labelledby="my-offers-heading">
        <h2 id="my-offers-heading" className="label text-muted-foreground">
          {t("myOffers")}
        </h2>
        {offers.state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : offers.state.status === "error" ? (
          <ApiErrorState icon={HandCoins} error={offers.state.error} />
        ) : offers.state.data.length === 0 ? (
          <EmptyState
            icon={HandCoins}
            title={t("myOffersEmpty")}
            description={t("myOffersEmptyBody")}
            className="mt-4"
          />
        ) : (
          <ul className="mt-2">
            {offers.state.data.map((offer) => (
              <OfferRow key={offer.id} offer={offer}>
                {offer.status === "COUNTERED" ? (
                  <>
                    <Button
                      size="sm"
                      onClick={() => act(offer, "accept-counter")}
                      disabled={isPending}
                    >
                      {t("acceptCounter")}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => act(offer, "decline-counter")}
                      disabled={isPending}
                    >
                      {t("declineCounter")}
                    </Button>
                  </>
                ) : offer.status === "PENDING" ? (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => act(offer, "withdraw")}
                    disabled={isPending}
                  >
                    {t("withdraw")}
                  </Button>
                ) : offer.status === "ACCEPTED" ? (
                  <Button size="sm" asChild>
                    <Link href="/payments">{t("goPay")}</Link>
                  </Button>
                ) : null}
              </OfferRow>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-12" aria-labelledby="my-deposits-heading">
        <h2 id="my-deposits-heading" className="label text-muted-foreground">
          {t("myDeposits")}
        </h2>
        <p className="text-dim mt-1 text-sm">{t("myDepositsBody")}</p>
        {deposits.state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : deposits.state.status === "error" ? (
          <ApiErrorState icon={ShieldCheck} error={deposits.state.error} />
        ) : deposits.state.data.length === 0 ? (
          <p className="text-dim mt-4 text-sm">{t("myDepositsEmpty")}</p>
        ) : (
          <ul className="mt-2" data-testid="my-deposits">
            {deposits.state.data.map((deposit) => (
              <li
                key={deposit.id}
                className="border-line flex flex-wrap items-baseline justify-between gap-3 border-b py-3 text-sm"
              >
                <Link
                  href={`/auctions/${deposit.auctionId}`}
                  className="hover:text-signal-text min-w-0 [overflow-wrap:anywhere]"
                >
                  {deposit.lotTitle}
                </Link>
                <span className="label-sm text-dim">
                  {t(`depositStatus.${deposit.status}`)}
                </span>
                <span className="text-dim text-xs">
                  {format.dateTime(
                    new Date(
                      deposit.settledAt ?? deposit.paidAt ?? deposit.createdAt,
                    ),
                    { dateStyle: "medium" },
                  )}
                </span>
                <span className="mono-figure">
                  {money.currency(deposit.amount)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
