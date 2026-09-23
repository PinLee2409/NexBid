"use client";

import { Heart } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { WatchButton } from "@/components/auction/watch-button";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { listWatchlist } from "@/services/watchlist-service";
import { useWatchedAuctionIds } from "@/services/watchlist-service";

export default function WatchlistPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  // Re-reading the ids keys the loader, so un-watching a lot removes the row.
  const watchedIds = useWatchedAuctionIds();
  const { state } = useAsyncData(
    `watchlist-${[...watchedIds].sort().join("|")}`,
    listWatchlist,
  );

  return (
    <>
      <PageHeader title={t("watchlistTitle")} description={t("watchlistSubtitle")} />

      <div className="mt-6">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 3 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState
            tone="error"
            icon={Heart}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Heart}
            title={t("watchlistEmptyTitle")}
            description={t("watchlistEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {state.data.map((item) => (
              <AuctionListItem
                key={item.id}
                auction={item.auction}
                actions={
                  <>
                    <WatchButton
                      auctionId={item.auction.id}
                      productName={item.auction.product.name}
                    />
                    <Button asChild size="sm">
                      <Link href={`/auctions/${item.auction.id}`}>
                        {item.auction.status === "ACTIVE"
                          ? tc("placeBid")
                          : tc("viewLot")}
                      </Link>
                    </Button>
                  </>
                }
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
