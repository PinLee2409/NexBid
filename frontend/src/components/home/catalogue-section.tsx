import { useTranslations } from "next-intl";

import { AuctionCatalogueItem } from "@/components/nexbid/catalogue-item";
import { LiveTag } from "@/components/nexbid/live-pulse";
import { SectionHead } from "@/components/nexbid/section-head";
import type { AuctionSummary } from "@/types";

interface CatalogueSectionProps {
  auctions: AuctionSummary[];
  serverTime: string;
}

/**
 * The catalogue, set at three scales.
 *
 * A uniform grid would read as a shop; the rhythm here — one wide lot, a pair,
 * then a row of three — reads as a printed sale catalogue.
 */
export function CatalogueSection({ auctions, serverTime }: CatalogueSectionProps) {
  const t = useTranslations("catalogue");

  if (auctions.length === 0) {
    return (
      <section className="mx-auto max-w-[1680px] px-5 py-20 sm:px-8">
        <SectionHead title={t("title")} description={t("empty")} />
      </section>
    );
  }

  const [lead, ...rest] = auctions;
  const pair = rest.slice(0, 2);
  const trio = rest.slice(2, 5);
  const tail = rest.slice(5, 8);

  return (
    <section className="mx-auto max-w-[1680px] px-5 py-16 sm:px-8 lg:py-24">
      <SectionHead
        title={t("title")}
        description={t("lead")}
        action={{ label: t("viewAll"), href: "/auctions?status=ACTIVE" }}
        badge={<LiveTag label={String(auctions.length).padStart(2, "0")} />}
      />

      {/* Opening spread: one lot at full width beside a tall pair. */}
      <div className="mt-14 grid gap-x-8 gap-y-14 lg:grid-cols-12">
        <AuctionCatalogueItem
          auction={lead}
          serverTime={serverTime}
          scale="hero"
          priority
          className="lg:col-span-7"
        />

        <div className="grid gap-y-14 lg:col-span-5 lg:content-start">
          {pair.map((auction) => (
            <AuctionCatalogueItem
              key={auction.id}
              auction={auction}
              serverTime={serverTime}
              scale="standard"
            />
          ))}
        </div>
      </div>

      {trio.length > 0 ? (
        <div className="mt-14 grid gap-x-8 gap-y-14 sm:grid-cols-2 lg:mt-20 lg:grid-cols-3">
          {trio.map((auction) => (
            <AuctionCatalogueItem
              key={auction.id}
              auction={auction}
              serverTime={serverTime}
              scale="standard"
            />
          ))}
        </div>
      ) : null}

      {tail.length > 0 ? (
        <div className="mt-14 grid gap-x-8 gap-y-14 lg:mt-20 lg:grid-cols-12">
          <AuctionCatalogueItem
            auction={tail[0]}
            serverTime={serverTime}
            scale="wide"
            className="lg:col-span-8"
          />
          <div className="grid gap-y-14 lg:col-span-4 lg:content-start">
            {tail.slice(1).map((auction) => (
              <AuctionCatalogueItem
                key={auction.id}
                auction={auction}
                serverTime={serverTime}
                scale="standard"
              />
            ))}
          </div>
        </div>
      ) : null}
    </section>
  );
}
