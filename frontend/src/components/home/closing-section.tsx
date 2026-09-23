"use client";

import { Flame, Gavel } from "lucide-react";
import { useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { AuctionClock } from "@/components/nexbid/auction-clock";
import { LivePulse } from "@/components/nexbid/live-pulse";
import { formatLot } from "@/components/nexbid/lot-number";
import { SectionHead } from "@/components/nexbid/section-head";
import { AUCTION_CONFIG } from "@/constants/auction";
import { getMsRemaining } from "@/lib/auction-rules";
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/types";

interface ClosingSectionProps {
  auctions: AuctionSummary[];
  serverTime: string;
}

/**
 * The closing board: a race read top to bottom.
 *
 * Each lot carries a progress line showing how much of its final hour is
 * gone, and the closest lot is set largest — hierarchy by time, not by price.
 */
export function ClosingSection({ auctions, serverTime }: ClosingSectionProps) {
  const t = useTranslations("closing");

  if (auctions.length === 0) return null;

  const now = new Date(serverTime).getTime();

  return (
    <section className="border-line bg-surface border-y">
      <div className="mx-auto max-w-[1680px] px-5 py-16 sm:px-8 lg:py-24">
        <SectionHead
          title={t("title")}
          description={t("lead")}
          action={{ label: t("viewAll"), href: "/auctions?sort=ENDING_SOON" }}
          badge={
            <span className="bg-danger/12 text-danger-text inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold">
              <Flame className="size-3" aria-hidden="true" />
              {t("finalMinute")}
            </span>
          }
        />

        <ul className="mt-12">
          {auctions.map((auction, index) => (
            <ClosingRow
              key={auction.id}
              auction={auction}
              serverTime={serverTime}
              now={now}
              prominent={index === 0}
            />
          ))}
        </ul>
      </div>
    </section>
  );
}

function ClosingRow({
  auction,
  serverTime,
  now,
  prominent,
}: {
  auction: AuctionSummary;
  serverTime: string;
  now: number;
  prominent: boolean;
}) {
  const t = useTranslations("closing");
  const ts = useTranslations("stage");

  const remaining = getMsRemaining(auction, now);
  const critical = remaining <= 60_000;
  // How far through the final hour this lot already is.
  const elapsed = Math.min(
    1,
    Math.max(0, 1 - remaining / AUCTION_CONFIG.endingSoonMs),
  );

  const cover = auction.product.images[0];

  return (
    <li className="border-line group relative border-t last:border-b">
      <Link
        href={`/auctions/${auction.id}`}
        className="flex items-center gap-5 py-5 sm:gap-8"
      >
        <span className="mono-figure text-dim group-hover:text-signal-text w-10 shrink-0 text-xs transition-colors">
          {formatLot(auction.lotNumber)}
        </span>

        <span
          className={cn(
            "bg-background relative hidden shrink-0 overflow-hidden sm:block",
            prominent ? "size-24" : "size-16",
          )}
        >
          {cover ? (
            <Image
              unoptimized={isLocalImage(cover.url)}
              src={cover.url}
              alt=""
              fill
              sizes="96px"
              className="object-cover transition-transform duration-700 group-hover:scale-105"
            />
          ) : null}
        </span>

        <span className="min-w-0 flex-1">
          <span
            className={cn(
              "display group-hover:text-signal-text block truncate transition-colors",
              prominent ? "text-2xl sm:text-4xl" : "text-lg sm:text-2xl",
            )}
          >
            {auction.product.name}
          </span>
          <span className="mt-2 flex items-center gap-3">
            <span className="figure text-muted-foreground text-sm">
              {formatCurrency(auction.currentPrice)}
            </span>
            <span className="text-dim text-xs">·</span>
            <span className="mono-figure text-dim inline-flex items-center gap-1.5 text-xs">
              <Gavel className="size-3" aria-hidden="true" />
              {ts("bids", { count: auction.bidCount })}
            </span>
            {critical ? (
              <span className="label-sm text-danger-text inline-flex items-center gap-1.5">
                <LivePulse tone="danger" critical />
                {t("finalMinute")}
              </span>
            ) : null}
          </span>
        </span>

        <span className="shrink-0 text-right">
          <span className="label-sm text-dim mb-1.5 block">{t("endsIn")}</span>
          <AuctionClock
            endTime={auction.endTime}
            serverTime={serverTime}
            variant="inline"
            className={cn(prominent && "text-base")}
          />
        </span>
      </Link>

      {/* Progress through the final hour — the race made visible. */}
      <span
        className="pointer-events-none absolute inset-x-0 bottom-0 h-px"
        aria-hidden="true"
      >
        <span
          className={cn(
            "block h-px transition-[width] duration-1000",
            critical ? "bg-danger" : "bg-signal-text",
          )}
          style={{ width: `${elapsed * 100}%` }}
        />
      </span>
    </li>
  );
}
