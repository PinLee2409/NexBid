"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";

import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/types";

interface NexBidLiveTickerProps {
  auctions: AuctionSummary[];
  className?: string;
}

/**
 * The market tape under the header.
 *
 * A slow horizontal crawl of live prices — the detail that makes the platform
 * read as a running market rather than a catalogue. It pauses on hover so a
 * line can actually be clicked.
 */
export function NexBidLiveTicker({ auctions, className }: NexBidLiveTickerProps) {
  const t = useTranslations("ticker");

  if (auctions.length === 0) return null;

  // The track is duplicated so the -50% crawl loops seamlessly.
  const lane = [...auctions, ...auctions];
  const duration = Math.max(40, auctions.length * 9);

  return (
    <div
      className={cn(
        "marquee-host border-line bg-background relative overflow-hidden border-b",
        className,
      )}
    >
      <div className="flex items-stretch">
        <span className="label-sm text-signal-ink bg-signal z-10 flex shrink-0 items-center px-4 py-2.5">
          {t("label")}
        </span>

        <div className="relative min-w-0 flex-1 overflow-hidden">
          <div
            className="marquee-track flex w-max items-center"
            style={{ ["--marquee-duration" as string]: `${duration}s` }}
          >
            {lane.map((auction, index) => (
              <Link
                key={`${auction.id}-${index}`}
                href={`/auctions/${auction.id}`}
                aria-hidden={index >= auctions.length}
                tabIndex={index >= auctions.length ? -1 : undefined}
                className="group border-line flex shrink-0 items-baseline gap-3 border-r px-5 py-2.5"
              >
                <span className="group-hover:text-foreground text-muted-foreground truncate text-xs transition-colors">
                  {auction.product.name}
                </span>
                <span className="mono-figure text-foreground text-xs">
                  {formatCurrency(auction.currentPrice)}
                </span>
                <span className="text-signal-text text-[10px] leading-none">▲</span>
              </Link>
            ))}
          </div>

          {/* Fades so the tape dissolves into the page rather than being cut. */}
          <div className="from-background pointer-events-none absolute inset-y-0 right-0 w-16 bg-gradient-to-l to-transparent" />
        </div>
      </div>
    </div>
  );
}
