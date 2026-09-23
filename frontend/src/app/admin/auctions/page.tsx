"use client";

import { Check, Gavel, Loader2, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { AuctionStatusBadge } from "@/components/auction/auction-status-badge";
import { CategoryName } from "@/components/common/category-name";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { formatCurrency } from "@/lib/format";
import { isLocalImage } from "@/lib/images";
import {
  approveAuction,
  listAuctionsForReview,
  rejectAuction,
} from "@/services/admin-service";
import type { AuctionSummary } from "@/types";

type ReviewTab = "PENDING_APPROVAL" | "SCHEDULED" | "REJECTED";

const TAB_KEYS: Record<ReviewTab, string> = {
  PENDING_APPROVAL: "tabPending",
  SCHEDULED: "tabApproved",
  REJECTED: "tabRejected",
};

export default function AdminAuctionsPage() {
  const t = useTranslations("admin");
  const tc = useTranslations("common");

  const [tab, setTab] = useState<ReviewTab>("PENDING_APPROVAL");
  const { state, refresh } = useAsyncData(`admin-auctions-${tab}`, () =>
    listAuctionsForReview(tab),
  );

  const [rejecting, setRejecting] = useState<AuctionSummary | null>(null);

  return (
    <>
      <PageHeader title={t("auctionsTitle")} description={t("auctionsSubtitle")} />

      <Tabs
        value={tab}
        onValueChange={(value) => setTab(value as ReviewTab)}
        className="mt-6"
      >
        <TabsList>
          {(Object.keys(TAB_KEYS) as ReviewTab[]).map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(TAB_KEYS[key])}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
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
            title={t("pendingEmptyTitle")}
            description={t("pendingEmptyBody")}
          />
        ) : (
          <ul>
            {state.data.map((auction) => (
              <ReviewCard
                key={auction.id}
                auction={auction}
                onApproved={refresh}
                onRejectRequested={() => setRejecting(auction)}
              />
            ))}
          </ul>
        )}
      </div>

      <RejectDialog
        auction={rejecting}
        onClose={() => setRejecting(null)}
        onRejected={refresh}
      />
    </>
  );
}

function ReviewCard({
  auction,
  onApproved,
  onRejectRequested,
}: {
  auction: AuctionSummary;
  onApproved: () => void;
  onRejectRequested: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const format = useFormatter();
  const [isPending, startTransition] = useTransition();

  const cover = auction.product.images[0];
  const isPendingReview = auction.status === "PENDING_APPROVAL";

  function handleApprove() {
    startTransition(async () => {
      await approveAuction(auction.id);
      onApproved();
      toast.success(t("approved"), {
        description: t("approvedBody", { name: auction.product.name }),
      });
    });
  }

  return (
    <li className="border-line border-t last:border-b">
      <div className="flex flex-wrap items-start gap-x-6 gap-y-4 py-6">
        <div className="on-media bg-surface relative size-20 shrink-0 overflow-hidden">
          {cover ? (
            <Image
              unoptimized={isLocalImage(cover.url)}
              src={cover.url}
              alt={cover.alt}
              fill
              sizes="80px"
              className="object-cover"
            />
          ) : null}
        </div>

        <div className="min-w-[14rem] flex-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <AuctionStatusBadge status={auction.status} size="sm" />
            <span className="label-sm text-dim">
              <CategoryName category={auction.category} />
            </span>
          </div>

          <h3 className="display mt-1.5 text-xl">
            <Link
              href={`/auctions/${auction.id}`}
              className="hover:text-signal-text transition-colors"
            >
              {auction.product.name}
            </Link>
          </h3>

          <dl className="mt-4 flex flex-wrap gap-x-10 gap-y-3">
            <div>
              <dt className="label-sm text-dim">{t("submittedBy")}</dt>
              <dd className="mt-1 text-sm">{auction.seller.displayName}</dd>
            </div>
            <div>
              <dt className="label-sm text-dim">{tc("startingPrice")}</dt>
              <dd className="figure mt-1 text-sm">
                {formatCurrency(auction.startingPrice)}
              </dd>
            </div>
            <div>
              <dt className="label-sm text-dim">{tc("bidIncrement")}</dt>
              <dd className="figure mt-1 text-sm">
                {formatCurrency(auction.minimumIncrement)}
              </dd>
            </div>
            <div>
              <dt className="label-sm text-dim">{t("schedule")}</dt>
              <dd className="mono-figure mt-1 text-xs">
                {format.dateTime(new Date(auction.startTime), {
                  dateStyle: "medium",
                  timeStyle: "short",
                })}
                {" → "}
                {format.dateTime(new Date(auction.endTime), {
                  dateStyle: "medium",
                  timeStyle: "short",
                })}
              </dd>
            </div>
          </dl>
        </div>

        {isPendingReview ? (
          <div className="flex shrink-0 items-center gap-2">
            <Button
              variant="destructive"
              onClick={onRejectRequested}
              disabled={isPending}
            >
              <X className="size-3.5" />
              {t("reject")}
            </Button>
            <Button onClick={handleApprove} disabled={isPending}>
              {isPending ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Check className="size-3.5" />
              )}
              {t("approve")}
            </Button>
          </div>
        ) : (
          <Button asChild variant="outline">
            <Link href={`/auctions/${auction.id}`}>{t("reviewLot")}</Link>
          </Button>
        )}
      </div>

      {auction.status === "REJECTED" && auction.rejectionReason ? (
        <p className="bg-danger-dim text-danger-text mb-6 px-4 py-3 text-sm">
          <span className="label-sm">{t("rejectReasonLabel")}:</span>{" "}
          {auction.rejectionReason}
        </p>
      ) : null}
    </li>
  );
}

function RejectDialog({
  auction,
  onClose,
  onRejected,
}: {
  auction: AuctionSummary | null;
  onClose: () => void;
  onRejected: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");

  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleReject() {
    if (!auction) return;

    if (!reason.trim()) {
      setError(t("rejectReasonRequired"));
      return;
    }

    startTransition(async () => {
      await rejectAuction(auction.id, reason);
      setReason("");
      setError(null);
      onClose();
      onRejected();
      toast.success(t("rejected"), { description: t("rejectedBody") });
    });
  }

  return (
    <Dialog open={auction !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("rejectTitle")}</DialogTitle>
          <DialogDescription>{t("rejectBody")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="reject-reason" className="label-sm text-dim">
            {t("rejectReasonLabel")}
          </Label>
          <Textarea
            id="reject-reason"
            rows={4}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              setError(null);
            }}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? "reject-error" : undefined}
            placeholder={t("rejectReasonPlaceholder")}
          />
          {error ? (
            <p id="reject-error" role="alert" className="label-sm text-danger-text">
              {error}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant="destructive" onClick={handleReject} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("reject")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
