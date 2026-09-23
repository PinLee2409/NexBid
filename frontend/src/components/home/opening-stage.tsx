import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { AuctionClock } from "@/components/nexbid/auction-clock";
import { LiveTag } from "@/components/nexbid/live-pulse";
import { LotNumber } from "@/components/nexbid/lot-number";
import { AuctionPriceTicker } from "@/components/nexbid/price-ticker";
import { formatCompactNumber } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import type { AuctionSummary } from "@/types";

interface OpeningStageProps {
  auction: AuctionSummary;
  serverTime: string;
  liveCount: number;
}

/**
 * The first screen is not a hero — it is the room.
 *
 * One lot, full bleed, with the auction data floating around the object rather
 * than stacked beside it. The header sits transparently on top, so the page
 * opens on the product.
 */
export function OpeningStage({
  auction,
  serverTime,
  liveCount,
}: OpeningStageProps) {
  const t = useTranslations("stage");
  const cover = auction.product.images[0];

  return (
    // The stage is a cinema, not a page: it stays dark in both themes so the
    // object is lit and the type on top is always legible.
    <section className="on-media bg-background relative -mt-16 flex min-h-[100svh] flex-col overflow-hidden">
      {/* The object fills the stage; the vignette lets type sit on top of it. */}
      <div className="absolute inset-0">
        {cover ? (
          <Image
            unoptimized={isLocalImage(cover.url)}
            src={cover.url}
            alt={cover.alt}
            fill
            priority
            sizes="100vw"
            className="object-cover object-center"
          />
        ) : null}
        <div className="stage-fade absolute inset-0" aria-hidden="true" />
        <div
          className="scrim-bottom absolute inset-x-0 bottom-0 h-[70%]"
          aria-hidden="true"
        />
        <div className="scrim-top absolute inset-x-0 top-0 h-40" aria-hidden="true" />
      </div>

      <div className="relative mx-auto flex w-full max-w-[1680px] flex-1 flex-col px-5 pt-24 pb-10 sm:px-8">
        {/* Top rail: the session status, opposite the lot index. */}
        <div className="flex items-start justify-between gap-6">
          <LiveTag label={t("auctionsLive", { count: liveCount })} />
          <LotNumber
            lot={auction.lotNumber}
            size="xl"
            bare
            className="text-foreground/12 -mt-6 leading-[0.8] select-none sm:-mt-10"
          />
        </div>

        <div className="mt-auto">
          <p className="label text-muted-foreground mb-3">
            {t("openingLot")} · {auction.category.name}
          </p>

          {/* The name is allowed to be the largest thing on the page. */}
          <h1 className="display over-image max-w-[16ch] text-[clamp(2.75rem,9vw,8.5rem)]">
            {auction.product.name}
          </h1>

          <div className="border-line mt-10 flex flex-wrap items-end gap-x-14 gap-y-8 border-t pt-7">
            <div>
              <p className="label text-muted-foreground mb-2">{t("current")}</p>
              <AuctionPriceTicker
                amount={auction.currentPrice}
                flashOnChange={false}
                className="display text-[clamp(2.25rem,5.5vw,4.5rem)]"
              />
            </div>

            <div>
              <p className="label text-muted-foreground mb-2">{t("endsIn")}</p>
              <AuctionClock
                endTime={auction.endTime}
                serverTime={serverTime}
                variant="hero"
                className="text-[clamp(2.25rem,5.5vw,4.5rem)]"
              />
            </div>

            <div className="flex gap-10">
              <div>
                <p className="label text-muted-foreground mb-2">
                  {t("bids", { count: auction.bidCount })}
                </p>
                <p className="mono-figure text-lg">
                  {String(auction.bidCount).padStart(2, "0")}
                </p>
              </div>
              <div>
                <p className="label text-muted-foreground mb-2">
                  {t("watching", { count: "" }).trim()}
                </p>
                <p className="mono-figure text-lg">
                  {formatCompactNumber(auction.viewerCount)}
                </p>
              </div>
            </div>

            <Link
              href={`/auctions/${auction.id}`}
              className="group label bg-signal text-signal-ink hover:bg-foreground ml-auto inline-flex items-center gap-3 px-8 py-5 transition-colors"
            >
              {t("bidNow")}
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

