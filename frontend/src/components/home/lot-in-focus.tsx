import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { AuctionClock } from "@/components/nexbid/auction-clock";
import { LotNumber } from "@/components/nexbid/lot-number";
import { AuctionPriceTicker } from "@/components/nexbid/price-ticker";
import { isLocalImage } from "@/lib/images";
import type { AuctionSummary } from "@/types";

interface LotInFocusProps {
  auction: AuctionSummary;
  serverTime: string;
}

/**
 * The editorial break in the scroll: a single lot told as a story, with the
 * image running off the grid and the specification set as a bare list rather
 * than a row of spec cards.
 */
export function LotInFocus({ auction, serverTime }: LotInFocusProps) {
  const t = useTranslations("focus");
  const ts = useTranslations("stage");
  const tEnum = useTranslations("enums");
  const format = useFormatter();

  const image = auction.product.images[1] ?? auction.product.images[0];

  return (
    <section className="border-line relative border-t">
      <div className="mx-auto max-w-[1680px] px-5 sm:px-8">
        <div className="grid gap-10 py-16 lg:grid-cols-12 lg:gap-0 lg:py-24">
          {/* Type column, deliberately narrow against a very wide image. */}
          <div className="lg:col-span-5 lg:pr-12">
            <p className="label text-signal-text">{t("eyebrow")}</p>

            <LotNumber
              lot={auction.lotNumber}
              size="lg"
              bare
              className="text-foreground/15 mt-6 block leading-none"
            />

            <h2 className="display mt-6 text-[clamp(2.25rem,5vw,4.25rem)]">
              {auction.product.name}
            </h2>

            <p className="text-muted-foreground mt-6 max-w-md leading-relaxed">
              {auction.product.description.split(". ").slice(0, 2).join(". ")}.
            </p>

            <dl className="border-line mt-10 border-t">
              <SpecRow
                label={t("year")}
                value={format.dateTime(new Date(auction.product.createdAt), {
                  year: "numeric",
                })}
              />
              <SpecRow
                label={t("condition")}
                value={tEnum(`condition.${auction.product.condition}`)}
              />
              <SpecRow label={t("provenance")} value={t("originalOwner")} />
              <SpecRow label={t("included")} value={t("boxAndPapers")} />
            </dl>

            <div className="mt-10 flex flex-wrap items-end gap-10">
              <div>
                <p className="label text-muted-foreground mb-2">{ts("current")}</p>
                <AuctionPriceTicker
                  amount={auction.currentPrice}
                  flashOnChange={false}
                  className="display text-4xl"
                />
              </div>
              <div>
                <p className="label text-muted-foreground mb-2">{ts("endsIn")}</p>
                <AuctionClock
                  endTime={auction.endTime}
                  serverTime={serverTime}
                  variant="terminal"
                  className="!text-4xl"
                />
              </div>
            </div>

            <Link
              href={`/auctions/${auction.id}`}
              className="label border-foreground hover:bg-foreground hover:text-background mt-10 inline-flex items-center gap-3 border px-7 py-4 transition-colors"
            >
              {t("enterLot")} →
            </Link>
          </div>

          {/* The image breaks the container and bleeds to the right edge. */}
          <div className="lg:col-span-7">
            <div className="bg-surface relative aspect-[4/3] lg:bleed-right lg:aspect-[5/6] lg:h-full">
              {image ? (
                <Image
                  unoptimized={isLocalImage(image.url)}
                  src={image.url}
                  alt={image.alt}
                  fill
                  sizes="(max-width: 1024px) 100vw, 60vw"
                  className="object-cover"
                />
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function SpecRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-line flex items-baseline justify-between gap-6 border-b py-3.5">
      <dt className="label-sm text-dim">{label}</dt>
      <dd className="text-right text-sm">{value}</dd>
    </div>
  );
}
