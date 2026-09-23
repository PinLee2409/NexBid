"use client";

import { useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";

import { AuctionStatusBadge } from "@/components/auction/auction-status-badge";
import { CategoryName } from "@/components/common/category-name";
import { AuctionClock } from "@/components/nexbid/auction-clock";
import { formatLot } from "@/components/nexbid/lot-number";
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/types";

interface AuctionListItemProps {
  auction: AuctionSummary;
  serverTime?: string;
  /** Extra key/value shown beside the current price, e.g. the user's bid. */
  meta?: { label: string; value: ReactNode; tone?: "default" | "success" | "urgent" };
  /** Right-hand controls: pay, rebid, remove. */
  actions?: ReactNode;
  className?: string;
}

/**
 * The catalogue row, shared by My Bids, My Wins, Watchlist and the seller and
 * admin lists — the same object read the same way in every context, indexed
 * by lot number and separated by a rule rather than boxed in a card.
 */
export function AuctionListItem({
  auction,
  serverTime,
  meta,
  actions,
  className,
}: AuctionListItemProps) {
  const tc = useTranslations("common");
  const ts = useTranslations("stage");
  const cover = auction.product.images[0];
  const href = `/auctions/${auction.id}`;

  const isUpcoming = auction.status === "SCHEDULED";
  const isFinished =
    auction.status === "ENDED" ||
    auction.status === "COMPLETED" ||
    auction.status === "CANCELLED";

  return (
    <li
      className={cn(
        "border-line group relative flex flex-wrap items-center gap-x-6 gap-y-4 border-t py-5 last:border-b",
        className,
      )}
    >
      <span className="mono-figure text-dim group-hover:text-signal-text hidden w-10 shrink-0 text-xs transition-colors sm:block">
        {formatLot(auction.lotNumber)}
      </span>

      <span className="on-media bg-surface relative size-16 shrink-0 overflow-hidden">
        {cover ? (
          <Image
            unoptimized={isLocalImage(cover.url)}
            src={cover.url}
            alt=""
            fill
            sizes="64px"
            className="object-cover transition-transform duration-700 group-hover:scale-105"
          />
        ) : null}
      </span>

      <span className="min-w-[10rem] flex-1">
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <AuctionStatusBadge status={auction.status} size="sm" />
          <span className="label-sm text-dim">
            <CategoryName category={auction.category} />
          </span>
        </span>

        <h3 className="display group-hover:text-signal-text mt-1.5 text-lg transition-colors sm:text-xl">
          <Link href={href} className="after:absolute after:inset-0 after:content-['']">
            <span className="line-clamp-1">{auction.product.name}</span>
          </Link>
        </h3>

        <span className="mono-figure text-dim mt-1 block text-[11px]">
          {ts("bids", { count: auction.bidCount })}
        </span>
      </span>

      <span className="shrink-0">
        <span className="label-sm text-dim mb-1 block">
          {isUpcoming
            ? tc("startingPrice")
            : isFinished
              ? tc("winningBid")
              : tc("currentBid")}
        </span>
        <span className="figure block text-lg">
          {formatCurrency(
            isUpcoming ? auction.startingPrice : auction.currentPrice,
          )}
        </span>
      </span>

      {meta ? (
        <span className="shrink-0">
          <span className="label-sm text-dim mb-1 block">{meta.label}</span>
          <span
            className={cn(
              "figure block text-sm",
              meta.tone === "success" && "text-success",
              meta.tone === "urgent" && "text-danger-text",
            )}
          >
            {meta.value}
          </span>
        </span>
      ) : null}

      {/* A clock only means something while bidding is actually open. */}
      {auction.status === "ACTIVE" ? (
        <span className="shrink-0 text-right">
          <span className="label-sm text-dim mb-1 block">{tc("endsIn")}</span>
          <AuctionClock
            endTime={auction.endTime}
            serverTime={serverTime}
            variant="inline"
          />
        </span>
      ) : null}

      {actions ? (
        <span className="relative z-20 flex shrink-0 flex-wrap items-center gap-2">
          {actions}
        </span>
      ) : null}
    </li>
  );
}
