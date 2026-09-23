"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Check, Eye, Gavel, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState, useTransition } from "react";
import { toast } from "sonner";

import { AutoBidDialog } from "@/components/auction/auto-bid-dialog";
import { AuctionClock } from "@/components/nexbid/auction-clock";
import { BidConfirmLayer } from "@/components/nexbid/bid-confirm-layer";
import { LiveTag } from "@/components/nexbid/live-pulse";
import { formatLot } from "@/components/nexbid/lot-number";
import { AuctionPriceTicker, PriceDelta } from "@/components/nexbid/price-ticker";
import type { AuctionRoom } from "@/hooks/use-auction-room";
import { useBidRejectionMessage } from "@/hooks/use-labels";
import {
  getMinimumNextBid,
  validateBid,
  type BidRejection,
} from "@/lib/auction-rules";
import {
  CURRENCY,
  formatCompactNumber,
  formatCurrency,
  parseCurrencyInput,
} from "@/lib/format";
import { cn } from "@/lib/utils";
import { placeBid } from "@/services/bid-service";
import { toggleWatch, useIsWatched } from "@/services/watchlist-service";
import type { AuctionDetail, User } from "@/types";

interface BidTerminalProps {
  auction: AuctionDetail;
  room: AuctionRoom;
  user: User | null;
  serverTime: string;
  className?: string;
}

/**
 * The trading terminal.
 *
 * Structured with rules and hierarchy rather than a card: every value sits in
 * a labelled band, the price is the largest figure on the panel, and the bid
 * action is a full-width bar that inverts on hover. Inside the final minute
 * the whole panel changes temperature.
 */
export function BidTerminal({
  auction,
  room,
  user,
  serverTime,
  className,
}: BidTerminalProps) {
  const t = useTranslations("terminal");
  const ts = useTranslations("stage");
  const rejectionMessage = useBidRejectionMessage();

  const liveAuction = {
    ...auction,
    currentPrice: room.currentPrice,
    bidCount: room.bidCount,
    endTime: room.endTime,
    status: room.ended ? ("ENDED" as const) : auction.status,
  };

  const minimumNextBid = getMinimumNextBid(liveAuction);
  const watched = useIsWatched(auction.id);

  const [amountInput, setAmountInput] = useState(String(minimumNextBid));
  const [touched, setTouched] = useState(false);
  const [lastMinimum, setLastMinimum] = useState(minimumNextBid);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [isPending, startTransition] = useTransition();

  // A rival bid raises the floor; follow it until the bidder types their own.
  if (lastMinimum !== minimumNextBid) {
    setLastMinimum(minimumNextBid);
    if (!touched) setAmountInput(String(minimumNextBid));
  }

  /** Server-aligned clock, so the panel closes exactly when the lot does. */
  const [now, setNow] = useState(() => new Date(serverTime).getTime());
  useEffect(() => {
    const offset = new Date(serverTime).getTime() - Date.now();
    const interval = window.setInterval(() => setNow(Date.now() + offset), 1000);
    return () => window.clearInterval(interval);
  }, [serverTime]);

  const amount = parseCurrencyInput(amountInput);
  const validation = validateBid({ auction: liveAuction, user, amount, now });
  const isSeller = user !== null && auction.sellerId === user.id;
  const blocking: BidRejection | null = validation.ok ? null : validation;

  // Only show an input-level error once the bidder has actually typed.
  const showInputError =
    touched &&
    blocking !== null &&
    (blocking.code === "BID_TOO_LOW" || blocking.code === "BID_EMPTY");

  // Everything except the amount is a state of the auction, not of the form.
  const contextBlock =
    blocking !== null &&
    blocking.code !== "BID_TOO_LOW" &&
    blocking.code !== "BID_EMPTY"
      ? blocking
      : null;

  const remaining = Math.max(0, new Date(room.endTime).getTime() - now);
  const finalMinute = !room.ended && remaining > 0 && remaining <= 60_000;

  // Movement against the previous bid — the terminal's delta line.
  const previousPrice = room.bids[1]?.amount ?? auction.startingPrice;
  const delta = room.currentPrice - previousPrice;

  function handleConfirm() {
    if (amount === null) return;

    startTransition(async () => {
      const response = await placeBid(auction.id, amount);
      setConfirmOpen(false);

      if (!response.ok) {
        toast.error(rejectionMessage(response));
        return;
      }

      setTouched(false);
      setSucceeded(true);
      room.dismissOutbid();
      toast.success(t("bidAccepted"), {
        description: formatCurrency(amount),
      });
    });
  }

  // The success flash is a moment, not a state.
  useEffect(() => {
    if (!succeeded) return;
    const timer = window.setTimeout(() => setSucceeded(false), 2200);
    return () => window.clearTimeout(timer);
  }, [succeeded]);

  const canBid = validation.ok && !room.ended;
  const isOpen = auction.status === "ACTIVE" && !room.ended;

  return (
    <section
      className={cn(
        "border-line border-t transition-colors duration-700",
        finalMinute && "border-danger-text/40",
        className,
      )}
      aria-label={t("liveBids")}
    >
      {/* Identity band */}
      <div className="border-line flex items-center justify-between gap-4 border-b py-4">
        <span className="mono-figure text-dim text-xs">
          {t("lot")} {formatLot(auction.lotNumber)}
        </span>
        {isOpen ? (
          <LiveTag
            label={ts("live")}
            tone={finalMinute ? "danger" : "accent"}
            critical={finalMinute}
          />
        ) : (
          <span className="label-sm text-dim">
            {room.ended ? t("soldFor") : "—"}
          </span>
        )}
      </div>

      <div className="border-line border-b py-5">
        <h2 className="display text-2xl leading-tight">{auction.product.name}</h2>
        <p className="mono-figure text-dim mt-2 text-xs">
          {t("reference")} {auction.id.replace("auction-", "").toUpperCase()}
        </p>
      </div>

      {/* The state band — leading, outbid, or accepted — replaces itself. */}
      <AnimatePresence mode="wait" initial={false}>
        {succeeded ? (
          <StateBand key="accepted" tone="accent">
            <p className="label">{t("bidAccepted")}</p>
            <p className="display mt-2 text-3xl">
              {formatCurrency(room.currentPrice)}
            </p>
            <p className="label mt-2 opacity-80">{t("youreLeading")}</p>
          </StateBand>
        ) : room.outbid && !room.ended ? (
          <StateBand key="outbid" tone="danger">
            <p className="display text-2xl">{t("outbidTitle")}</p>
            <div className="mt-3 flex gap-8">
              <span>
                <span className="label-sm block opacity-70">
                  {t("outbidCurrent")}
                </span>
                <span className="figure text-lg">
                  {formatCurrency(room.currentPrice)}
                </span>
              </span>
              <span>
                <span className="label-sm block opacity-70">{t("outbidNext")}</span>
                <span className="figure text-lg">
                  {formatCurrency(minimumNextBid)}
                </span>
              </span>
            </div>
          </StateBand>
        ) : room.isHighestBidder && !room.ended ? (
          <StateBand key="leading" tone="accent">
            <p className="label">{t("highestBidder")}</p>
          </StateBand>
        ) : null}
      </AnimatePresence>

      {/* Price */}
      <div className="border-line border-b py-6">
        <p className="text-muted-foreground text-sm">
          {room.ended && room.bidCount > 0 ? t("soldFor") : t("currentBid")}
        </p>
        <AuctionPriceTicker
          amount={room.currentPrice}
          className="display mt-2 text-[clamp(2.5rem,4vw,3.5rem)]"
        />
        {delta > 0 && !room.ended ? (
          <PriceDelta
            delta={delta}
            percent={delta / Math.max(1, previousPrice)}
            className="mt-3"
          />
        ) : null}
        <p className="text-muted-foreground mt-3 text-sm">
          {t("bids", { count: room.bidCount })}
        </p>
      </div>

      {/* Clock */}
      {isOpen ? (
        <div
          className={cn(
            "border-line border-b py-6 transition-colors duration-700",
            finalMinute && "bg-danger/5",
          )}
        >
          <p className="label text-muted-foreground mb-3">{t("timeRemaining")}</p>
          <AuctionClock
            endTime={room.endTime}
            serverTime={serverTime}
            variant="terminal"
            onComplete={room.markEnded}
          />
        </div>
      ) : null}

      {/* Bid input and action */}
      {isOpen ? (
        <>
          <div className="border-line border-b py-6">
            <div className="mb-3 flex items-baseline justify-between gap-4">
              <label htmlFor="terminal-bid" className="label text-muted-foreground">
                {t("yourBid")}
              </label>
              <span className="text-dim text-xs">
                {t("minimum")}{" "}
                <span className="text-foreground figure font-medium">
                  {formatCurrency(minimumNextBid)}
                </span>
              </span>
            </div>

            <div
              className={cn(
                "flex items-center border transition-colors",
                showInputError
                  ? "border-danger-text"
                  : "border-line focus-within:border-signal-text",
              )}
            >
              <span className="text-dim px-4 text-lg" aria-hidden="true">
                {CURRENCY.symbol}
              </span>
              <input
                id="terminal-bid"
                inputMode="numeric"
                value={amountInput}
                disabled={isSeller || !user}
                onChange={(event) => {
                  setTouched(true);
                  setAmountInput(event.target.value.replace(/[^\d]/g, ""));
                }}
                aria-invalid={showInputError}
                aria-describedby={showInputError ? "terminal-bid-error" : undefined}
                className="figure h-14 w-full bg-transparent pr-4 text-2xl outline-none disabled:opacity-50"
              />
            </div>

            {showInputError && blocking ? (
              <p
                id="terminal-bid-error"
                role="alert"
                className="text-danger-text mt-2 text-sm"
              >
                {rejectionMessage(blocking)}
              </p>
            ) : null}
          </div>

          {contextBlock ? (
            <p
              className={cn(
                "border-line border-b py-4 text-sm",
                contextBlock.code === "NOT_AUTHENTICATED"
                  ? "text-muted-foreground"
                  : "text-danger-text",
              )}
            >
              {contextBlock.code === "SELLER_CANNOT_BID"
                ? t("sellerNotice")
                : rejectionMessage(contextBlock)}
            </p>
          ) : null}

          {contextBlock?.code === "NOT_AUTHENTICATED" ? (
            <Link
              href="/login"
              className="label bg-signal text-signal-ink hover:bg-foreground block w-full py-6 text-center transition-colors"
            >
              {t("signInToBid")}
            </Link>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmOpen(true)}
              disabled={!canBid || isPending}
              className={cn(
                "label inline-flex w-full items-center justify-center gap-2 py-6 transition-colors",
                room.outbid
                  ? "bg-danger text-danger-ink hover:bg-foreground hover:text-background"
                  : "bg-signal text-signal-ink hover:bg-foreground hover:text-background",
                succeeded && "bg-signal",
                "disabled:bg-surface-2 disabled:text-dim disabled:cursor-not-allowed",
              )}
            >
              {isPending ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  {t("placing")}
                </>
              ) : succeeded ? (
                <>
                  <Check className="size-4" />
                  {t("bidAccepted")}
                </>
              ) : (
                <>
                  <Gavel className="size-4" />
                  {room.outbid ? t("bidAgain") : t("placeBid")}
                </>
              )}
            </button>
          )}

          <p className="text-dim border-line border-b py-4 text-center text-xs">
            {t("binding")}
          </p>

          <div className="border-line grid grid-cols-2 border-b">
            <button
              type="button"
              onClick={() => toggleWatch(auction.id)}
              aria-pressed={watched}
              className={cn(
                "label border-line hover:text-foreground border-r py-4 transition-colors",
                watched ? "text-signal-text" : "text-muted-foreground",
              )}
            >
              {watched ? t("watching_") : t("watch")}
            </button>

            <AutoBidDialog
              auctionId={auction.id}
              minimumNextBid={minimumNextBid}
              autoBid={auction.viewerState?.autoBid ?? null}
              disabled={isSeller || !user}
            />
          </div>
        </>
      ) : null}

      {/* Counters */}
      <div className="border-line flex items-center justify-between gap-6 border-b py-5">
        <span>
          <span className="label-sm text-dim mb-1.5 block">{t("liveBids")}</span>
          <span className="mono-figure text-lg">
            {String(room.bidCount).padStart(2, "0")}
          </span>
        </span>
        <span className="text-right">
          <span className="label-sm text-dim mb-1.5 block">{t("activity")}</span>
          <span className="mono-figure inline-flex items-center gap-1.5 text-lg">
            <Eye className="size-3.5" aria-hidden="true" />
            {formatCompactNumber(room.viewerCount)}
          </span>
        </span>
      </div>

      {auction.antiSniping.enabled && isOpen ? (
        <p className="text-dim py-4 text-xs leading-relaxed">
          {t("antiSnipe", {
            seconds: auction.antiSniping.windowSeconds,
            minutes: Math.round(auction.antiSniping.extensionSeconds / 60),
          })}
        </p>
      ) : null}

      <BidConfirmLayer
        open={confirmOpen}
        auction={auction}
        amount={amount ?? 0}
        pending={isPending}
        onConfirm={handleConfirm}
        onCancel={() => setConfirmOpen(false)}
      />
    </section>
  );
}

/** A full-bleed band that reports the viewer's position in the auction. */
function StateBand({
  tone,
  children,
}: {
  tone: "accent" | "danger";
  children: React.ReactNode;
}) {
  const TONE_STYLES = {
    accent: "bg-signal text-signal-ink",
    danger: "bg-danger text-danger-text-ink",
  } as const;

  return (
    <motion.div
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: "auto" }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
      className="shrink-0 overflow-hidden"
      role="status"
    >
      <div className={cn("border-line border-b px-4 py-5", TONE_STYLES[tone])}>
        {children}
      </div>
    </motion.div>
  );
}
