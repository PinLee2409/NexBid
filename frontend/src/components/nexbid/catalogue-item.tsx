"use client";

import { useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { AuctionClock } from "@/components/nexbid/auction-clock";
import { LivePulse } from "@/components/nexbid/live-pulse";
import { formatLot } from "@/components/nexbid/lot-number";
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import { cn } from "@/lib/utils";
import type { AuctionSummary } from "@/types";

type Scale = "hero" | "large" | "standard" | "wide";

interface AuctionCatalogueItemProps {
  auction: AuctionSummary;
  serverTime?: string;
  /** Controls how much of the grid the lot claims. */
  scale?: Scale;
  priority?: boolean;
  className?: string;
}

const RATIOS: Record<Scale, string> = {
  hero: "aspect-[4/5]",
  large: "aspect-[4/5]",
  standard: "aspect-square",
  wide: "aspect-[16/9]",
};

const TITLES: Record<Scale, string> = {
  hero: "text-[clamp(1.75rem,3.4vw,3.25rem)]",
  large: "text-[clamp(1.4rem,2.4vw,2.25rem)]",
  standard: "text-xl sm:text-2xl",
  wide: "text-[clamp(1.75rem,3.4vw,3rem)]",
};

/**
 * A catalogue entry, not a product card.
 *
 * No container, no border, no shadow — the lot number, the image and the type
 * do the work. Scale is what creates hierarchy between lots.
 */
export function AuctionCatalogueItem({
  auction,
  serverTime,
  scale = "standard",
  priority = false,
  className,
}: AuctionCatalogueItemProps) {
  const t = useTranslations("stage");
  const cover = auction.product.images[0];
  const isLive = auction.status === "ACTIVE";
  const isUpcoming = auction.status === "SCHEDULED";

  return (
    <article className={cn("group relative", className)}>
      <Link href={`/auctions/${auction.id}`} className="block">
        <div className={cn("on-media bg-surface relative overflow-hidden", RATIOS[scale])}>
          {cover ? (
            <Image
              unoptimized={isLocalImage(cover.url)}
              src={cover.url}
              alt={cover.alt}
              fill
              priority={priority}
              sizes={
                scale === "hero" || scale === "wide"
                  ? "(max-width: 1024px) 100vw, 60vw"
                  : "(max-width: 640px) 100vw, 33vw"
              }
              className="object-cover transition-transform duration-[900ms] ease-out group-hover:scale-[1.04]"
            />
          ) : null}

          {/* Lot index sits on the image, growing slightly on approach. */}
          <span
            className={cn(
              "display figure over-image absolute top-4 left-4 leading-none transition-all duration-500",
              "text-foreground/70 group-hover:text-signal-text",
              scale === "standard"
                ? "text-2xl group-hover:text-[28px]"
                : "text-4xl group-hover:text-5xl",
            )}
            aria-hidden="true"
          >
            {formatLot(auction.lotNumber)}
          </span>

          {isLive ? (
            <span className="absolute top-5 right-4">
              <LivePulse />
            </span>
          ) : null}

          {/* Revealed on hover in place of a persistent button. */}
          <span className="scrim-bottom pointer-events-none absolute inset-x-0 bottom-0 flex h-24 items-end p-4 opacity-0 transition-opacity duration-300 group-hover:opacity-100">
            <span className="label text-signal-text">{t("viewLot")} →</span>
          </span>
        </div>

        <div className="border-line mt-4 border-t pt-3">
          <p className="label-sm text-dim mb-2">{auction.category.name}</p>

          <h3
            className={cn(
              "display group-hover:text-signal-text transition-colors duration-300",
              TITLES[scale],
            )}
          >
            {auction.product.name}
          </h3>

          <div className="mt-4 flex items-end justify-between gap-4">
            <div>
              <p className="label-sm text-dim mb-1.5">
                {isUpcoming ? t("openingLot") : t("current")}
              </p>
              {/* Price rises into place on hover — the lot reacting to you. */}
              <p className="figure text-xl transition-transform duration-300 group-hover:-translate-y-0.5 sm:text-2xl">
                {formatCurrency(
                  isUpcoming ? auction.startingPrice : auction.currentPrice,
                )}
              </p>
            </div>

            <div className="text-right">
              {isLive ? (
                <AuctionClock
                  endTime={auction.endTime}
                  serverTime={serverTime}
                  variant="inline"
                />
              ) : (
                <span className="label-sm text-dim">
                  {isUpcoming ? t("openingLot") : "—"}
                </span>
              )}
              <p className="mono-figure text-dim mt-1.5 text-[11px]">
                {String(auction.bidCount).padStart(2, "0")} ·{" "}
                {String(auction.viewerCount).padStart(2, "0")}
              </p>
            </div>
          </div>
        </div>
      </Link>
    </article>
  );
}
