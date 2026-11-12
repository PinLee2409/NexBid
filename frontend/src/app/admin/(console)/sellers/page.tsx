"use client";

import { Check, Loader2, Store, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import {
  approveSellerApplication,
  listSellerApplications,
  rejectSellerApplication,
} from "@/services/admin-service";
import type { SellerApplication, SellerApplicationStatus } from "@/types";

const TAB_KEYS: Record<SellerApplicationStatus, string> = {
  PENDING: "tabPending",
  APPROVED: "tabApproved",
  REJECTED: "tabRejected",
};

/**
 * EN: The queue of buyers asking to sell (spec §7.1). Approving grants the seller tools at once.
 * VI: Hàng chờ người mua xin bán hàng (spec §7.1). Duyệt là cấp ngay công cụ người bán.
 */
export default function AdminSellersPage() {
  const t = useTranslations("admin");

  const [tab, setTab] = useState<SellerApplicationStatus>("PENDING");
  const { state, refresh } = useAsyncData(`admin-sellers-${tab}`, () => listSellerApplications(tab));
  const [rejecting, setRejecting] = useState<SellerApplication | null>(null);

  return (
    <>
      <PageHeader title={t("sellersTitle")} description={t("sellersSubtitle")} />

      <Tabs
        value={tab}
        onValueChange={(value) => setTab(value as SellerApplicationStatus)}
        className="mt-6 block"
      >
        <TabsList>
          {(Object.keys(TAB_KEYS) as SellerApplicationStatus[]).map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(TAB_KEYS[key])}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-10">
          {state.status === "loading" ? (
            <div>
              {Array.from({ length: 2 }).map((_, index) => (
                <AuctionListRowSkeleton key={index} />
              ))}
            </div>
          ) : state.status === "error" ? (
            <ApiErrorState icon={Store} error={state.error} />
          ) : state.data.length === 0 ? (
            <EmptyState icon={Store} title={t("sellersEmptyTitle")} description={t("sellersEmptyBody")} />
          ) : (
            <ul>
              {state.data.map((application) => (
                <ApplicationRow
                  key={application.id}
                  application={application}
                  onApproved={refresh}
                  onRejectRequested={() => setRejecting(application)}
                />
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>

      <RejectDialog application={rejecting} onClose={() => setRejecting(null)} onRejected={refresh} />
    </>
  );
}

function ApplicationRow({
  application,
  onApproved,
  onRejectRequested,
}: {
  application: SellerApplication;
  onApproved: () => void;
  onRejectRequested: () => void;
}) {
  const t = useTranslations("admin");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  const when = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium", timeStyle: "short" });

  function handleApprove() {
    startTransition(async () => {
      try {
        await approveSellerApplication(application.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      onApproved();
      toast.success(t("sellerApproved"), {
        description: t("sellerApprovedBody", { name: application.fullName }),
      });
    });
  }

  return (
    <li className="border-line border-t last:border-b">
      <div className="flex flex-wrap items-start gap-x-6 gap-y-4 py-6">
        <div className="min-w-[14rem] flex-1">
          <h3 className="display text-xl">{application.fullName}</h3>
          <p className="text-muted-foreground mt-1 text-sm break-all">{application.email}</p>

          <p className="label-sm text-dim mt-4">{t("applicantNote")}</p>
          <p className="mt-1.5 max-w-2xl text-sm leading-relaxed whitespace-pre-line">{application.note}</p>

          <dl className="mt-4 flex flex-wrap gap-x-10 gap-y-3">
            <div>
              <dt className="label-sm text-dim">{t("requestedOn")}</dt>
              <dd className="mono-figure mt-1 text-xs">{when(application.createdAt)}</dd>
            </div>
            {application.decidedAt ? (
              <div>
                <dt className="label-sm text-dim">{t("decidedOn")}</dt>
                <dd className="mono-figure mt-1 text-xs">{when(application.decidedAt)}</dd>
              </div>
            ) : null}
          </dl>
        </div>

        {application.status === "PENDING" ? (
          <div className="flex shrink-0 items-center gap-2">
            <Button variant="destructive" onClick={onRejectRequested} disabled={isPending}>
              <X className="size-3.5" />
              {t("reject")}
            </Button>
            <Button onClick={handleApprove} disabled={isPending}>
              {isPending ? <Loader2 className="size-3.5 animate-spin" /> : <Check className="size-3.5" />}
              {t("approve")}
            </Button>
          </div>
        ) : null}
      </div>

      {application.status === "REJECTED" && application.rejectionReason ? (
        <p className="bg-danger-dim text-danger-text mb-6 px-4 py-3 text-sm">
          <span className="label-sm">{t("rejectReasonLabel")}:</span> {application.rejectionReason}
        </p>
      ) : null}
    </li>
  );
}

function RejectDialog({
  application,
  onClose,
  onRejected,
}: {
  application: SellerApplication | null;
  onClose: () => void;
  onRejected: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();

  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleReject() {
    if (!application) return;
    if (!reason.trim()) {
      setError(t("rejectReasonRequired"));
      return;
    }

    startTransition(async () => {
      try {
        await rejectSellerApplication(application.id, reason);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      setReason("");
      setError(null);
      onClose();
      onRejected();
      toast.success(t("sellerRejected"), { description: t("sellerRejectedBody") });
    });
  }

  return (
    <Dialog open={application !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("sellerRejectTitle")}</DialogTitle>
          <DialogDescription>{t("sellerRejectBody")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="seller-reject-reason" className="label-sm text-dim">
            {t("rejectReasonLabel")}
          </Label>
          <Textarea
            id="seller-reject-reason"
            rows={4}
            maxLength={500}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              setError(null);
            }}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? "seller-reject-error" : undefined}
            placeholder={t("sellerRejectPlaceholder")}
          />
          {error ? (
            <p id="seller-reject-error" role="alert" className="label-sm text-danger-text">
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
