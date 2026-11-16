"use client";

import { HandCoins } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OfferRow } from "@/components/common/offer-row";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  answerOffer,
  counterOffer,
  sellerOffers,
  type LotOffer,
} from "@/services/market-service";

/**
 * EN: Offers buyers made on the seller's buy-now lots: accept (the lot sells at once), decline, or counter.
 * VI: Các đề nghị người mua gửi trên lô có giá mua ngay của người bán: nhận (lô bán ngay), từ chối, hoặc trả giá lại.
 */
export default function SellerOffersPage() {
  const t = useTranslations("deals");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [countering, setCountering] = useState<string | null>(null);
  const [counter, setCounter] = useState("");
  const { state, refresh } = useAsyncData("seller-lot-offers", sellerOffers);

  function run(task: () => Promise<unknown>, done: string) {
    startTransition(async () => {
      try {
        await task();
        toast.success(done);
        setCountering(null);
        setCounter("");
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  function actions(offer: LotOffer) {
    if (offer.status !== "PENDING") return null;
    if (countering === offer.id) {
      return (
        <form
          className="flex flex-wrap gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            run(
              () => counterOffer(offer.id, Number(counter.replace(/\D/g, ""))),
              t("counterSent"),
            );
          }}
        >
          <Input
            aria-label={t("counterAmount")}
            inputMode="numeric"
            value={counter}
            onChange={(event) => setCounter(event.target.value)}
            className="mono-figure h-9 w-36"
            autoFocus
          />
          <Button size="sm" type="submit" disabled={isPending || !counter}>
            {t("sendCounter")}
          </Button>
          <Button
            size="sm"
            type="button"
            variant="ghost"
            onClick={() => setCountering(null)}
          >
            {t("cancel")}
          </Button>
        </form>
      );
    }
    return (
      <>
        <Button
          size="sm"
          onClick={() =>
            run(() => answerOffer(offer.id, "accept"), t("offerAccepted"))
          }
          disabled={isPending}
        >
          {t("accept")}
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => setCountering(offer.id)}
          disabled={isPending}
        >
          {t("counter")}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={() =>
            run(() => answerOffer(offer.id, "decline"), t("offerDeclined"))
          }
          disabled={isPending}
        >
          {t("decline")}
        </Button>
      </>
    );
  }

  return (
    <>
      <PageHeader title={t("sellerTitle")} description={t("sellerSubtitle")} />
      <div className="mt-6">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={HandCoins} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={HandCoins}
            title={t("sellerEmpty")}
            description={t("sellerEmptyBody")}
          />
        ) : (
          <ul>
            {state.data.map((offer) => (
              <OfferRow key={offer.id} offer={offer} showBuyer>
                {actions(offer)}
              </OfferRow>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
