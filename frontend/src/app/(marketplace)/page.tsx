import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { CatalogueSection } from "@/components/home/catalogue-section";
import { ClosingSection } from "@/components/home/closing-section";
import { LotInFocus } from "@/components/home/lot-in-focus";
import { OpeningStage } from "@/components/home/opening-stage";
import { RoomsSection } from "@/components/home/rooms-section";
import { ScheduleSection } from "@/components/home/schedule-section";
import { NexBidLiveTicker } from "@/components/nexbid/live-ticker";
import { getHomeFeed } from "@/services/auction-service";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("common");
  return {
    title: { absolute: `${t("appName")} — ${t("tagline")}` },
    description: t("description"),
  };
}

/** The floor is live; nothing here may be cached between requests. */
export const dynamic = "force-dynamic";

export default async function HomePage() {
  const feed = await getHomeFeed();

  // The editorial break must not repeat the lot already on the opening stage.
  const editorialLot =
    feed.live.find((auction) => auction.id !== feed.featured?.id) ?? null;

  /**
   * Each section is a different spatial composition — stage, tape, catalogue,
   * editorial spread, closing board, rooms, preview — held together by one
   * type scale and one accent.
   */
  return (
    <>
      {feed.featured ? (
        <OpeningStage
          auction={feed.featured}
          serverTime={feed.serverTime}
          liveCount={feed.stats.liveCount}
        />
      ) : null}

      <NexBidLiveTicker auctions={feed.live} />

      <CatalogueSection auctions={feed.live} serverTime={feed.serverTime} />

      {editorialLot ? <LotInFocus auction={editorialLot} serverTime={feed.serverTime} /> : null}

      <ClosingSection auctions={feed.endingSoon} serverTime={feed.serverTime} />

      <RoomsSection categories={feed.categories} />

      <ScheduleSection auctions={feed.upcoming} />
    </>
  );
}
