"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

import { formatLot } from "@/components/nexbid/lot-number";
import { formatCurrency } from "@/lib/format";
import type { AuctionDetail } from "@/types";

interface BidConfirmLayerProps {
  open: boolean;
  auction: AuctionDetail;
  amount: number;
  pending: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Confirmation as a moment, not a dialog.
 *
 * The page dims, the amount is stated at display scale, and the two choices
 * sit as full-width bars. Committing money should feel like a decision.
 */
export function BidConfirmLayer({
  open,
  auction,
  amount,
  pending,
  onConfirm,
  onCancel,
}: BidConfirmLayerProps) {
  const t = useTranslations("terminal");
  const tc = useTranslations("common");

  useEffect(() => {
    if (!open) return;

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !pending) onCancel();
    }

    document.addEventListener("keydown", onKeyDown);
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previous;
    };
  }, [open, pending, onCancel]);

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.24 }}
          className="bg-background/96 fixed inset-0 z-[80] flex items-center justify-center backdrop-blur-2xl"
          role="dialog"
          aria-modal="true"
          aria-label={t("confirmTitle")}
        >
          <motion.div
            initial={{ y: 28, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 14, opacity: 0 }}
            transition={{ duration: 0.38, ease: [0.22, 1, 0.36, 1] }}
            className="mx-auto w-full max-w-3xl px-5 sm:px-8"
          >
            <p className="label text-muted-foreground">{t("confirmTitle")}</p>

            <p className="display text-signal-text mt-6 text-[clamp(3.5rem,13vw,10rem)] leading-[0.85]">
              {formatCurrency(amount)}
            </p>

            <div className="border-line mt-8 flex flex-wrap items-baseline gap-x-8 gap-y-2 border-t pt-5">
              <span className="mono-figure text-dim text-sm">
                {t("lot")} {formatLot(auction.lotNumber)}
              </span>
              <span className="display text-xl sm:text-2xl">
                {auction.product.name}
              </span>
            </div>

            <p className="text-muted-foreground mt-5 text-sm">{t("binding")}</p>

            <div className="mt-10 grid gap-3 sm:grid-cols-[1fr_auto]">
              <button
                type="button"
                onClick={onConfirm}
                disabled={pending}
                className="label bg-signal text-signal-ink hover:bg-foreground w-full py-6 transition-colors disabled:opacity-60"
              >
                {pending ? `${t("placing")}…` : t("confirmBid")}
              </button>
              <button
                type="button"
                onClick={onCancel}
                disabled={pending}
                className="label border-line text-muted-foreground hover:border-foreground hover:text-foreground border px-10 py-6 transition-colors disabled:opacity-60"
              >
                {tc("cancel")}
              </button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
