"use client";

import { Gavel } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAsyncData } from "@/hooks/use-async-data";
import { formatCurrency } from "@/lib/format";
import { listMyBids, type BidStanding, type MyBidEntry } from "@/services/account-service";

type BidTab = "ACTIVE" | "WINNING" | "OUTBID" | "ENDED";

const TAB_KEYS: Record<BidTab, string> = {
  ACTIVE: "tabActive",
  WINNING: "tabWinning",
  OUTBID: "tabOutbid",
  ENDED: "tabEnded",
};

function matchesTab(standing: BidStanding, tab: BidTab): boolean {
  switch (tab) {
    case "ACTIVE":
      return standing === "WINNING" || standing === "OUTBID";
    case "WINNING":
      return standing === "WINNING";
    case "OUTBID":
      return standing === "OUTBID";
    case "ENDED":
      return standing === "WON" || standing === "LOST";
  }
}

export default function MyBidsPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const [tab, setTab] = useState<BidTab>("ACTIVE");

  const { state } = useAsyncData("my-bids", listMyBids);

  const entries =
    state.status === "success"
      ? state.data.filter((entry) => matchesTab(entry.standing, tab))
      : [];

  return (
    <>
      <PageHeader title={t("bidsTitle")} description={t("bidsSubtitle")} />

      <Tabs
        value={tab}
        onValueChange={(value) => setTab(value as BidTab)}
        className="mt-6"
      >
        <TabsList>
          {(Object.keys(TAB_KEYS) as BidTab[]).map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(TAB_KEYS[key])}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

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
            icon={Gavel}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : entries.length === 0 ? (
          <EmptyState
            icon={Gavel}
            title={t("bidsEmptyTitle")}
            description={t("bidsEmptyBody")}
            action={{ label: t("bidsEmptyAction"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {entries.map((entry) => (
              <BidRow key={entry.auction.id} entry={entry} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function BidRow({ entry }: { entry: MyBidEntry }) {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const STANDING_LABEL: Record<BidStanding, string> = {
    WINNING: t("statusWinning"),
    OUTBID: t("statusOutbid"),
    WON: t("statusWon"),
    LOST: t("statusLost"),
  };

  const tone =
    entry.standing === "WINNING" || entry.standing === "WON"
      ? "success"
      : entry.standing === "OUTBID"
        ? "urgent"
        : "default";

  return (
    <AuctionListItem
      auction={entry.auction}
      meta={{
        label: t("yourLatestBid"),
        value: (
          <span className="flex flex-col">
            {formatCurrency(entry.yourBid)}
            <span className="text-[11px] font-medium">
              {STANDING_LABEL[entry.standing]}
            </span>
          </span>
        ),
        tone,
      }}
      actions={
        entry.standing === "OUTBID" ? (
          <Button asChild size="sm">
            <Link href={`/auctions/${entry.auction.id}`}>{tc("bidAgain")}</Link>
          </Button>
        ) : (
          <Button asChild size="sm" variant="outline">
            <Link href={`/auctions/${entry.auction.id}`}>{tc("viewDetails")}</Link>
          </Button>
        )
      }
    />
  );
}
