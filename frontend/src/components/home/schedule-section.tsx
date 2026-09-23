import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { formatLot } from "@/components/nexbid/lot-number";
import { SectionHead } from "@/components/nexbid/section-head";
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import type { AuctionSummary } from "@/types";

interface ScheduleSectionProps {
  auctions: AuctionSummary[];
}

/**
 * Lots not yet open, presented as an exhibition preview: tall portraits in a
 * row, with the opening time rather than a countdown — nothing is at stake
 * yet, so nothing is racing.
 */
export function ScheduleSection({ auctions }: ScheduleSectionProps) {
  const t = useTranslations("schedule");
  const format = useFormatter();

  if (auctions.length === 0) return null;

  return (
    <section className="mx-auto max-w-[1680px] px-5 py-16 sm:px-8 lg:py-24">
      <SectionHead
        title={t("title")}
        description={t("lead")}
        action={{ label: t("viewAll"), href: "/auctions?status=SCHEDULED" }}
      />

      <div className="mt-12 grid gap-x-8 gap-y-12 sm:grid-cols-2 lg:grid-cols-4">
        {auctions.map((auction) => {
          const cover = auction.product.images[0];

          return (
            <Link
              key={auction.id}
              href={`/auctions/${auction.id}`}
              className="group block"
            >
              <div className="on-media bg-surface relative aspect-[3/4] overflow-hidden">
                {cover ? (
                  <Image
                    unoptimized={isLocalImage(cover.url)}
                    src={cover.url}
                    alt={cover.alt}
                    fill
                    sizes="(max-width: 640px) 100vw, 25vw"
                    className="object-cover opacity-80 transition-all duration-[900ms] group-hover:scale-105 group-hover:opacity-100"
                  />
                ) : null}
                <span
                  className="display over-image text-foreground/70 group-hover:text-signal-text absolute top-4 left-4 text-2xl transition-colors"
                  aria-hidden="true"
                >
                  {formatLot(auction.lotNumber)}
                </span>
              </div>

              <div className="border-line mt-4 border-t pt-3">
                <p className="label-sm text-dim mb-2">{t("opening")}</p>
                <h3 className="display group-hover:text-signal-text text-lg transition-colors">
                  {auction.product.name}
                </h3>
                <div className="mt-3 flex items-baseline justify-between gap-3">
                  <span className="figure text-sm">
                    {formatCurrency(auction.startingPrice)}
                  </span>
                  <span className="mono-figure text-dim text-[11px]">
                    {format.dateTime(new Date(auction.startTime), {
                      month: "short",
                      day: "numeric",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </span>
                </div>
              </div>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
