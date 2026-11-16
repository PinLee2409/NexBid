"use client";

import { HandCoins, Loader2, ShieldCheck } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { loginHref } from "@/lib/redirect";
import {
  answerCounter,
  depositStatus,
  makeOffer,
  myOffersOnLot,
  startDeposit,
  type LotOffer,
} from "@/services/market-service";
import type { User } from "@/types";

interface LotDealsProps {
  auctionId: string;
  sellerId: string;
  startingPrice: number;
  buyNowPrice: number | null;
  /** EN: Open for bidding right now. / VI: Đang mở trả giá. */
  open: boolean;
  /** EN: Closed for good: no deposit can be taken any more. / VI: Đã đóng hẳn: không nhận cọc nữa. */
  closed: boolean;
  bidCount: number;
  user: User | null;
}

/**
 * EN: What sits under the bid terminal: the deposit an expensive lot asks for, and an offer box on a buy-now lot nobody
 *     has bid on yet.
 * VI: Những gì nằm dưới khung trả giá: tiền cọc mà lô đắt yêu cầu, và ô gửi đề nghị trên lô có giá mua ngay mà chưa ai
 *     trả giá.
 */
export function LotDeals(props: LotDealsProps) {
  const isSeller = props.user?.id === props.sellerId;
  return (
    <>
      {!isSeller ? <DepositPanel {...props} /> : null}
      {!isSeller &&
      props.open &&
      props.bidCount === 0 &&
      props.buyNowPrice !== null ? (
        <OfferPanel {...props} />
      ) : null}
    </>
  );
}

function DepositPanel({ auctionId, user, closed }: LotDealsProps) {
  const t = useTranslations("deals");
  const money = useMoney();
  const locale = useLocale();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const { state } = useAsyncData(
    `deposit-${auctionId}-${user?.id ?? "guest"}`,
    () => depositStatus(auctionId),
  );

  if (state.status !== "success" || state.data.amount === null) return null;
  const { amount, held } = state.data;

  function pay() {
    startTransition(async () => {
      try {
        window.location.assign(await startDeposit(auctionId, locale));
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <section
      className="border-line bg-surface mt-6 border p-5"
      data-testid="deposit-panel"
      aria-labelledby="deposit-heading"
    >
      <div className="flex items-start gap-3">
        <ShieldCheck
          className={
            held ? "text-signal-text mt-0.5 size-5" : "text-dim mt-0.5 size-5"
          }
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <h2 id="deposit-heading" className="label text-foreground">
            {held
              ? t("depositHeld", { amount: money.currency(amount) })
              : t("depositNeeded", { amount: money.currency(amount) })}
          </h2>
          <p className="text-muted-foreground mt-1 text-sm">
            {held ? t("depositHeldBody") : t("depositBody")}
          </p>
        </div>
      </div>
      {!held && !closed ? (
        user ? (
          <Button className="mt-4 w-full" onClick={pay} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("depositPay", { amount: money.currency(amount) })}
          </Button>
        ) : (
          <Button asChild variant="outline" className="mt-4 w-full">
            <Link href={loginHref(`/auctions/${auctionId}`)}>
              {t("signInToBid")}
            </Link>
          </Button>
        )
      ) : null}
    </section>
  );
}

function OfferPanel({
  auctionId,
  startingPrice,
  buyNowPrice,
  user,
}: LotDealsProps) {
  const router = useRouter();
  const t = useTranslations("deals");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [amount, setAmount] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const mine = useAsyncData(`offers-${auctionId}-${user?.id ?? "guest"}`, () =>
    user ? myOffersOnLot(auctionId) : Promise.resolve([] as LotOffer[]),
  );
  const offers = mine.state.status === "success" ? mine.state.data : [];
  const open = offers.find(
    (offer) => offer.status === "PENDING" || offer.status === "COUNTERED",
  );

  function send(event: React.FormEvent) {
    event.preventDefault();
    const value = Number(amount.replace(/\D/g, ""));
    if (!value) return;
    startTransition(async () => {
      try {
        await makeOffer(auctionId, value, message);
        setError(null);
        setAmount("");
        setMessage("");
        toast.success(t("offerSent"));
        mine.refresh();
      } catch (failure) {
        setError(errorMessage(failure));
      }
    });
  }

  function answer(
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
        if (action === "accept-counter") router.push("/payments");
        mine.refresh();
      } catch (failure) {
        toast.error(errorMessage(failure));
      }
    });
  }

  return (
    <section
      className="border-line mt-6 border p-5"
      data-testid="offer-panel"
      aria-labelledby="offer-heading"
    >
      <div className="flex items-center gap-2">
        <HandCoins className="text-signal-text size-5" aria-hidden="true" />
        <h2 id="offer-heading" className="label">
          {t("offerTitle")}
        </h2>
      </div>
      <p className="text-muted-foreground mt-1 text-sm">
        {t("offerBody", {
          min: money.currency(startingPrice),
          max: money.currency(buyNowPrice ?? 0),
        })}
      </p>

      {open ? (
        <div className="bg-surface mt-4 p-4 text-sm" data-testid="my-offer">
          <p>
            {open.status === "COUNTERED"
              ? t("offerCountered", {
                  amount: money.currency(open.amount),
                  counter: money.currency(open.counterAmount ?? 0),
                })
              : t("offerWaiting", { amount: money.currency(open.amount) })}
          </p>
          <p className="text-dim mt-1 text-xs">
            {t("offerExpires", {
              when: format.dateTime(new Date(open.expiresAt), {
                dateStyle: "medium",
                timeStyle: "short",
              }),
            })}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {open.status === "COUNTERED" ? (
              <>
                <Button
                  size="sm"
                  onClick={() => answer(open, "accept-counter")}
                  disabled={isPending}
                >
                  {t("acceptCounter")}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => answer(open, "decline-counter")}
                  disabled={isPending}
                >
                  {t("declineCounter")}
                </Button>
              </>
            ) : (
              <Button
                size="sm"
                variant="outline"
                onClick={() => answer(open, "withdraw")}
                disabled={isPending}
              >
                {t("withdraw")}
              </Button>
            )}
          </div>
        </div>
      ) : null}

      {!user ? (
        <Button asChild variant="outline" className="mt-4 w-full">
          <Link href={loginHref(`/auctions/${auctionId}`)}>
            {t("signInToOffer")}
          </Link>
        </Button>
      ) : (
        <form onSubmit={send} className="mt-4 space-y-3">
          <FormField label={t("offerAmount")} error={error}>
            {(field) => (
              <Input
                {...field}
                inputMode="numeric"
                value={amount}
                onChange={(event) =>
                  setAmount(event.target.value.replace(/[^\d.,\s]/g, ""))
                }
                placeholder={String(startingPrice)}
                className="mono-figure"
              />
            )}
          </FormField>
          <FormField label={t("offerMessage")}>
            {(field) => (
              <Textarea
                {...field}
                value={message}
                maxLength={300}
                rows={2}
                onChange={(event) => setMessage(event.target.value)}
              />
            )}
          </FormField>
          <Button
            type="submit"
            variant="outline"
            className="w-full"
            disabled={isPending || !amount}
          >
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {open ? t("offerReplace") : t("offerSend")}
          </Button>
        </form>
      )}
    </section>
  );
}
