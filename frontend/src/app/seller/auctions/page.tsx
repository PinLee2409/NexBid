"use client";

import { Gavel, Plus, Send } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { isAuctionEditable } from "@/lib/auction-rules";
import {
  decideReserve,
  listMyAuctions,
  submitAuctionForApproval,
} from "@/services/seller-service";
import type { AuctionSummary } from "@/types";
import { useMoney } from "@/hooks/use-money";

export default function SellerAuctionsPage() {
  const t = useTranslations("seller");

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
          <ApiErrorState icon={Gavel} error={state.error} />
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
  const errorMessage = useApiErrorMessage();

  const canSubmit = isAuctionEditable(auction.status);

  function handleSubmit() {
    startTransition(async () => {
      try {
        await submitAuctionForApproval(auction.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
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

      {auction.reserveDecision === "PENDING" ? (
        <ReserveDecisionRow auction={auction} onDecided={onChanged} />
      ) : auction.reserveDecision === "DECLINED" || auction.reserveDecision === "LAPSED" ? (
        <li className="text-muted-foreground px-4 pb-3 text-sm">{t("reserveNotSold")}</li>
      ) : null}

      {auction.status === "REJECTED" && auction.rejectionReason ? (
        <li className="bg-danger-dim text-danger-text px-4 py-3 text-sm">
          <span className="label-sm">{t("rejectionReason")}:</span>{" "}
          {auction.rejectionReason}
        </li>
      ) : null}
    </>
  );
}

/**
 * EN: A lot that closed below the reserve waits for the seller: sell at the highest bid, or keep it.
 * VI: Lô đóng dưới giá sàn chờ người bán: bán với giá cao nhất, hoặc giữ lại.
 */
function ReserveDecisionRow({ auction, onDecided }: { auction: AuctionSummary; onDecided: () => void }) {
  const money = useMoney();
  const t = useTranslations("seller");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const bid = money.currency(auction.currentPrice);

  function decide(accept: boolean) {
    startTransition(async () => {
      try {
        await decideReserve(auction.id, accept);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      onDecided();
      if (accept) toast.success(t("reserveAccepted"), { description: t("reserveAcceptedBody") });
      else toast.success(t("reserveRefused"), { description: t("reserveRefusedBody") });
    });
  }

  return (
    <li className="border-warning bg-surface mb-2 flex flex-wrap items-center justify-between gap-4 border-l-2 px-4 py-4">
      <div className="min-w-[14rem] flex-1">
        <p className="label">{t("reserveDecisionTitle")}</p>
        <p className="mt-1 text-sm">
          {t("reserveDecisionBody", {
            bid,
            reserve: money.currency(auction.reservePrice ?? 0),
            date: auction.reserveDecisionUntil
              ? format.dateTime(new Date(auction.reserveDecisionUntil), { dateStyle: "medium", timeStyle: "short" })
              : "",
          })}
        </p>
      </div>
      <div className="flex shrink-0 gap-2">
        <Button size="sm" variant="outline" onClick={() => decide(false)} disabled={isPending}>
          {t("keepLot")}
        </Button>
        <Button size="sm" onClick={() => decide(true)} disabled={isPending}>
          {t("sellAnyway", { bid })}
        </Button>
      </div>
    </li>
  );
}
