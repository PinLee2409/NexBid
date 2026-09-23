"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState } from "react";

import { formatLot } from "@/components/nexbid/lot-number";
import { formatCurrency, padTwo } from "@/lib/format";
import type { AuctionDetail } from "@/types";

/* -------------------------------------------------------------------------- */
/*                             Auction extended                               */
/* -------------------------------------------------------------------------- */

interface ExtendedOverlayProps {
  open: boolean;
  /** Seconds added by the anti-sniping rule. */
  extensionSeconds: number;
  /** Time remaining at the instant the extension fired, in ms. */
  remainingBeforeMs: number;
  onDone: () => void;
}

function clockOf(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  return `${padTwo(Math.floor(total / 60))}:${padTwo(total % 60)}`;
}

/**
 * Anti-sniping, staged as a live event: the clock that was about to expire is
 * shown, the extension is added to it, and the new total lands. Three beats,
 * then the room returns.
 */
export function AuctionExtendedOverlay({
  open,
  extensionSeconds,
  remainingBeforeMs,
  onDone,
}: ExtendedOverlayProps) {
  const t = useTranslations("overlay");
  const [beat, setBeat] = useState(0);

  // Opening resets the sequence; adjusting during render keeps the reset out
  // of an effect, where it would cascade.
  const [wasOpen, setWasOpen] = useState(open);
  if (wasOpen !== open) {
    setWasOpen(open);
    setBeat(0);
  }

  useEffect(() => {
    if (!open) return;

    const timers = [
      window.setTimeout(() => setBeat(1), 700),
      window.setTimeout(() => setBeat(2), 1500),
      window.setTimeout(() => onDone(), 3400),
    ];

    return () => timers.forEach(window.clearTimeout);
  }, [open, onDone]);

  const before = clockOf(remainingBeforeMs);
  const added = `+${clockOf(extensionSeconds * 1000)}`;
  const after = clockOf(remainingBeforeMs + extensionSeconds * 1000);

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.28 }}
          className="bg-background/97 fixed inset-0 z-[85] flex items-center justify-center backdrop-blur-2xl"
          role="status"
          aria-live="assertive"
        >
          <div className="w-full max-w-4xl px-5 text-center sm:px-8">
            <p className="label text-signal-text">{t("extendedTitle")}</p>

            <div className="mt-10 flex flex-col items-center gap-2">
              <motion.p
                animate={{
                  opacity: beat === 0 ? 1 : 0.25,
                  scale: beat === 0 ? 1 : 0.9,
                }}
                transition={{ duration: 0.4 }}
                className="mono-figure text-danger-text text-[clamp(2.5rem,8vw,5rem)] leading-none"
              >
                {before}
              </motion.p>

              <motion.p
                animate={{ opacity: beat >= 1 ? 1 : 0, y: beat >= 1 ? 0 : 12 }}
                transition={{ duration: 0.4 }}
                className="mono-figure text-signal-text text-[clamp(2rem,6vw,3.5rem)] leading-none"
              >
                {added}
              </motion.p>

              <motion.p
                animate={{ opacity: beat >= 2 ? 1 : 0, y: beat >= 2 ? 0 : 16 }}
                transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
                className="display mt-4 text-[clamp(3.5rem,12vw,9rem)] leading-none"
              >
                {after}
              </motion.p>
            </div>

            <p className="text-muted-foreground mt-10 text-sm">
              {t("extendedBody")}
            </p>
          </div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

/* -------------------------------------------------------------------------- */
/*                                  Closing                                   */
/* -------------------------------------------------------------------------- */

interface ClosingOverlayProps {
  open: boolean;
  onDone: () => void;
}

/**
 * The hammer. `GOING. GOING. GONE.` lands one line at a time, then hands over
 * to the result — the conclusion an auction deserves instead of a status chip.
 */
export function AuctionClosingOverlay({ open, onDone }: ClosingOverlayProps) {
  const t = useTranslations("overlay");
  const [beat, setBeat] = useState(0);

  const [wasOpen, setWasOpen] = useState(open);
  if (wasOpen !== open) {
    setWasOpen(open);
    setBeat(0);
  }

  useEffect(() => {
    if (!open) return;

    const timers = [
      window.setTimeout(() => setBeat(1), 750),
      window.setTimeout(() => setBeat(2), 1500),
      window.setTimeout(() => onDone(), 2900),
    ];

    return () => timers.forEach(window.clearTimeout);
  }, [open, onDone]);

  const lines = [t("going"), t("going"), t("gone")];

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
          className="bg-background fixed inset-0 z-[85] flex items-center justify-center"
          role="status"
          aria-live="assertive"
        >
          <div className="w-full max-w-5xl px-5 sm:px-8">
            {lines.map((line, index) => (
              <motion.p
                key={index}
                initial={{ opacity: 0, y: 24 }}
                animate={{
                  opacity: beat >= index ? 1 : 0,
                  y: beat >= index ? 0 : 24,
                }}
                transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
                className={`display text-[clamp(3rem,12vw,9rem)] leading-[0.88] ${
                  index === 2 ? "text-signal-text" : "text-foreground"
                }`}
              >
                {line}
              </motion.p>
            ))}
          </div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

/* -------------------------------------------------------------------------- */
/*                                    Sold                                    */
/* -------------------------------------------------------------------------- */

interface SoldOverlayProps {
  open: boolean;
  auction: AuctionDetail;
  finalPrice: number;
  winnerName: string | null;
  /** True when the signed-in user took the lot. */
  viewerWon: boolean;
  onClose: () => void;
}

/** The result screen: price, lot, winner — and a route out. */
export function SoldOverlay({
  open,
  auction,
  finalPrice,
  winnerName,
  viewerWon,
  onClose,
}: SoldOverlayProps) {
  const t = useTranslations("overlay");

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
          className="bg-background/98 fixed inset-0 z-[84] flex items-center justify-center backdrop-blur-2xl"
          role="dialog"
          aria-modal="true"
        >
          <motion.div
            initial={{ y: 26, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
            className="w-full max-w-4xl px-5 sm:px-8"
          >
            <p className="label text-signal-text">
              {viewerWon ? t("yoursTitle") : t("sold")}
            </p>

            <p className="display mt-6 text-[clamp(3.5rem,14vw,11rem)] leading-[0.85]">
              {winnerName ? formatCurrency(finalPrice) : t("gone")}
            </p>

            <dl className="border-line mt-10 grid gap-x-12 gap-y-5 border-t pt-6 sm:grid-cols-3">
              <div>
                <dt className="label-sm text-dim mb-2">{t("lot")}</dt>
                <dd className="mono-figure text-lg">
                  {formatLot(auction.lotNumber)}
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="label-sm text-dim mb-2">{t("winner")}</dt>
                <dd className="display text-lg">{winnerName ?? "—"}</dd>
              </div>
            </dl>

            <div className="mt-10 flex flex-wrap gap-3">
              {viewerWon ? (
                <Link
                  href="/orders"
                  className="label bg-signal text-signal-ink hover:bg-foreground px-10 py-5 transition-colors"
                >
                  {t("payNow")} →
                </Link>
              ) : null}
              <button
                type="button"
                onClick={onClose}
                className="label border-line text-muted-foreground hover:border-foreground hover:text-foreground border px-10 py-5 transition-colors"
              >
                {t("backToLot")}
              </button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
