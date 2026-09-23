"use client";

import { Gavel, Plus, Send } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { isAuctionEditable } from "@/lib/auction-rules";
import {
  listMyAuctions,
  submitAuctionForApproval,
} from "@/services/seller-service";
import type { AuctionSummary } from "@/types";

export default function SellerAuctionsPage() {
  const t = useTranslations("seller");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("seller-auctions", listMyAuctions);

  return (
    <>
      <PageHeader
        title={t("auctionsTitle")}
        description={t("auctionsSubtitle")}
        actions={
          <Button asChild>
            <Link href="/seller/auctions/create">
              <Plus className="size-4" />
              {t("newAuction")}
            </Link>
          </Button>
        }
      />

      <div className="mt-10">
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
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Gavel}
            title={t("auctionsEmptyTitle")}
            description={t("auctionsEmptyBody")}
            action={{ label: t("newAuction"), href: "/seller/auctions/create" }}
          />
        ) : (
          <ul>
            {state.data.map((auction) => (
              <SellerAuctionRow
                key={auction.id}
                auction={auction}
                onChanged={refresh}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function SellerAuctionRow({
  auction,
  onChanged,
}: {
  auction: AuctionSummary;
  onChanged: () => void;
}) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const [isPending, startTransition] = useTransition();

  const canSubmit = isAuctionEditable(auction.status);

  function handleSubmit() {
    startTransition(async () => {
      await submitAuctionForApproval(auction.id);
      onChanged();
      toast.success(t("submittedTitle"), {
        description: t("submittedBody", { name: auction.product.name }),
      });
    });
  }

  return (
    <>
      <AuctionListItem
        auction={auction}
        actions={
          <>
            {canSubmit ? (
              <Button size="sm" onClick={handleSubmit} disabled={isPending}>
                <Send className="size-3.5" />
                {t("submitForApproval")}
              </Button>
            ) : null}
            <Button asChild size="sm" variant="outline">
              <Link href={`/auctions/${auction.id}`}>{tc("viewDetails")}</Link>
            </Button>
          </>
        }
      />

      {auction.status === "REJECTED" && auction.rejectionReason ? (
        <li className="bg-danger-dim text-danger-text px-4 py-3 text-sm">
          <span className="label-sm">{t("rejectionReason")}:</span>{" "}
          {auction.rejectionReason}
        </li>
      ) : null}
    </>
  );
}
