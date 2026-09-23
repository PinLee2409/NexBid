"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useTranslations } from "next-intl";

import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { Bid } from "@/types";

interface LiveBidTapeProps {
  bids: Bid[];
  /** Ids that arrived over the live channel, so only those animate in. */
  freshBidIds?: string[];
  viewerId: string | null;
  limit?: number;
  className?: string;
}

/** `19:42:18` — tape time, to the second. */
function tapeTime(iso: string): string {
  const value = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}

/**
 * The bid tape.
 *
 * Not a table — a transaction feed, timestamped to the second, with new bids
 * printing at the top. The most recent line carries the accent so the eye can
 * find the current price without reading.
 */
export function LiveBidTape({
  bids,
  freshBidIds = [],
  viewerId,
  limit = 14,
  className,
}: LiveBidTapeProps) {
  const t = useTranslations("terminal");
  const visible = bids.slice(0, limit);

  if (visible.length === 0) {
    return (
      <p className={cn("text-dim py-6 text-sm", className)}>{t("noBidsYet")}</p>
    );
  }

  return (
    <ul className={cn("mono-figure text-[13px]", className)}>
      <AnimatePresence initial={false}>
        {visible.map((bid, index) => {
          const isViewer = bid.bidderId === viewerId;
          const isFresh = freshBidIds.includes(bid.id);
          const isTop = index === 0;

          return (
            <motion.li
              key={bid.id}
              layout
              initial={isFresh ? { opacity: 0, y: -14 } : false}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.26, ease: [0.22, 1, 0.36, 1] }}
              className={cn(
                "border-line flex items-center gap-3 border-b py-2.5 last:border-b-0",
                isFresh && "flash-signal",
              )}
            >
              <span className="text-dim w-[62px] shrink-0 tabular-nums">
                {tapeTime(bid.createdAt)}
              </span>

              <span
                className={cn(
                  "min-w-0 flex-1 truncate",
                  isViewer ? "text-signal-text" : "text-muted-foreground",
                )}
              >
                {isViewer ? t("you") : bid.bidderDisplayName}
                {bid.automatic ? (
                  <span className="text-dim ml-2 text-[10px] tracking-wider uppercase">
                    {t("auto")}
                  </span>
                ) : null}
              </span>

              <span
                className={cn(
                  "shrink-0 tabular-nums",
                  isTop ? "text-foreground" : "text-muted-foreground",
                )}
              >
                {formatCurrency(bid.amount)}
              </span>

              <span
                className={cn(
                  "w-3 shrink-0 text-[10px]",
                  isTop ? "text-signal-text" : "text-transparent",
                )}
                aria-hidden="true"
              >
                ▲
              </span>
            </motion.li>
          );
        })}
      </AnimatePresence>
    </ul>
  );
}
