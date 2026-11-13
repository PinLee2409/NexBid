"use client";

import { CircleAlert, Loader2, PackageCheck, Receipt, Star } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import {
  BankAccountFields,
  EMPTY_BANK_ACCOUNT,
  useBankAccountErrors,
} from "@/components/account/bank-account-form";
import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
import { StarInput, StarRating } from "@/components/common/star-rating";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { getMyBankAccount, saveMyBankAccount, type BankAccount } from "@/services/bank-account-service";
import { confirmReceipt, listOrders, openDispute, reviewOrder, type OrderEntry } from "@/services/order-service";
import type { DisputeReason } from "@/types";
import { PayWithVnpay } from "@/components/payment/pay-with-vnpay";

export default function OrdersPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("orders", listOrders);
  // EN: Kept after closing, so the dialog keeps its words while it fades. / VI: Giữ sau khi đóng, để hộp thoại giữ chữ khi mờ dần.
  const [reviewing, setReviewing] = useState<OrderEntry | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [disputing, setDisputing] = useState<OrderEntry | null>(null);
  const [disputeOpen, setDisputeOpen] = useState(false);

  return (
    <>
      <PageHeader title={t("ordersTitle")} description={t("ordersSubtitle")} />

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <ApiErrorState icon={Receipt} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title={t("ordersEmptyTitle")}
            description={t("ordersEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <OrderRow
                key={entry.order.id}
                entry={entry}
                action={
                  <OrderActions
                    entry={entry}
                    onReceived={refresh}
                    onReview={() => {
                      setReviewing(entry);
                      setReviewOpen(true);
                    }}
                    onDispute={() => {
                      setDisputing(entry);
                      setDisputeOpen(true);
                    }}
                  />
                }
              />
            ))}
          </ul>
        )}
      </div>

      <ReviewDialog
        key={reviewing?.order.id}
        entry={reviewing}
        open={reviewOpen}
        onClose={() => setReviewOpen(false)}
        onPosted={refresh}
      />

      <DisputeDialog
        key={`dispute-${disputing?.order.id}`}
        entry={disputing}
        open={disputeOpen}
        onClose={() => setDisputeOpen(false)}
        onSent={refresh}
      />
    </>
  );
}

function OrderActions({
  entry,
  onReceived,
  onReview,
  onDispute,
}: {
  entry: OrderEntry;
  onReceived: () => void;
  onReview: () => void;
  onDispute: () => void;
}) {
  const t = useTranslations("account");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  const needsPayment = entry.payment?.status === "PENDING" || entry.payment?.status === "FAILED";

  if (needsPayment) {
    return (
      <PayWithVnpay paymentId={entry.payment!.id} className="h-9 px-4" />
    );
  }

  if (entry.order.status === "COMPLETED") {
    // EN: The sale is over; what is left is the buyer's verdict. / VI: Giao dịch đã xong; còn lại là nhận định của người mua.
    return entry.review ? (
      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{t("yourReview")}</p>
        <StarRating rating={entry.review.rating} />
      </div>
    ) : (
      <Button size="sm" variant="outline" onClick={onReview}>
        <Star className="size-4" />
        {t("rateSeller")}
      </Button>
    );
  }
  // EN: Paid but not yet shipped: the only thing the buyer can do is report a problem. / VI: Đã trả nhưng chưa gửi: người mua chỉ có thể báo sự cố.
  const report = (
    <Button size="sm" variant="ghost" onClick={onDispute}>
      <CircleAlert className="size-4" />
      {t("reportProblem")}
    </Button>
  );
  if (entry.order.status === "PAID") return report;
  if (entry.order.status !== "PROCESSING") return null;

  // EN: The seller has shipped; only the buyer can say it arrived. / VI: Người bán đã gửi; chỉ người mua mới xác nhận được là đã nhận.
  function confirm() {
    startTransition(async () => {
      try {
        await confirmReceipt(entry.order.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(t("receiptConfirmed"), { description: t("receiptConfirmedBody") });
      onReceived();
    });
  }

  return (
    <div className="flex shrink-0 flex-wrap items-center gap-2">
      <Button size="sm" variant="outline" onClick={confirm} disabled={isPending}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : <PackageCheck className="size-4" />}
        {t("confirmReceipt")}
      </Button>
      {report}
    </div>
  );
}

const DISPUTE_REASONS: DisputeReason[] = ["NOT_RECEIVED", "NOT_AS_DESCRIBED", "DAMAGED", "OTHER"];

/**
 * EN: The buyer tells NexBid what went wrong. The order stops — it cannot complete on its own — until an admin decides.
 * VI: Người mua cho NexBid biết chuyện gì không ổn. Đơn tạm dừng — không tự hoàn tất được — cho tới khi admin quyết định.
 */
function DisputeDialog({
  entry,
  open,
  onClose,
  onSent,
}: {
  entry: OrderEntry | null;
  open: boolean;
  onClose: () => void;
  onSent: () => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [reason, setReason] = useState<DisputeReason | "">(
    entry?.order.status === "PAID" ? "NOT_RECEIVED" : "",
  );
  const [details, setDetails] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  // EN: A refund is a bank transfer, so the buyer says where to, once, if they have not already.
  // VI: Hoàn tiền là chuyển khoản, nên người mua cho biết chuyển về đâu, một lần, nếu chưa từng cho.
  const saved = useAsyncData(`my-bank-account-${open}`, getMyBankAccount);
  const needsAccount = saved.state.status === "success" && saved.state.data === null;
  const [bank, setBank] = useState<BankAccount>(EMPTY_BANK_ACCOUNT);
  const [bankErrors, setBankErrors] = useState<Partial<Record<keyof BankAccount, string>>>({});
  const checkBank = useBankAccountErrors();

  function send() {
    if (!entry) return;
    if (!reason) {
      setError(t("disputeReasonRequired"));
      return;
    }
    if (details.trim().length < 10) {
      setError(t("disputeDetailsTooShort"));
      return;
    }
    if (needsAccount) {
      const found = checkBank(bank);
      setBankErrors(found);
      if (Object.keys(found).length > 0) return;
    }
    startTransition(async () => {
      try {
        if (needsAccount) await saveMyBankAccount(bank);
        await openDispute(entry.order.id, reason, details);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onSent();
      toast.success(t("disputeSent"), { description: t("disputeSentBody") });
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("disputeTitle")}</DialogTitle>
          <DialogDescription>{t("disputeBody", { name: entry?.auction?.product.name ?? "" })}</DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          <FormField label={t("disputeReasonLabel")} required>
            {(field) => (
              <Select
                value={reason}
                onValueChange={(value) => {
                  setReason(value as DisputeReason);
                  setError(null);
                }}
              >
                <SelectTrigger id={field.id} aria-describedby={field["aria-describedby"]} className="w-full">
                  <SelectValue placeholder={t("disputeReasonPlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {DISPUTE_REASONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`disputeReason.${option}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>
          <FormField label={t("disputeDetailsLabel")} hint={t("disputeDetailsHint")} required>
            {(field) => (
              <Textarea
                {...field}
                rows={4}
                maxLength={1000}
                value={details}
                onChange={(event) => {
                  setDetails(event.target.value);
                  setError(null);
                }}
              />
            )}
          </FormField>
          {needsAccount ? (
            <fieldset className="border-line space-y-4 border-t pt-5">
              <legend className="label text-foreground">{t("refundAccountTitle")}</legend>
              <p className="text-muted-foreground text-sm">{t("disputeAccountHint")}</p>
              <BankAccountFields
                values={bank}
                errors={bankErrors}
                disabled={isPending}
                onChange={(key, value) => {
                  setBank((current) => ({ ...current, [key]: value }));
                  setBankErrors((current) => ({ ...current, [key]: undefined }));
                }}
              />
            </fieldset>
          ) : null}
          {error ? (
            <p role="alert" className="label-sm text-danger-text">
              {error}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button onClick={send} disabled={isPending || saved.state.status === "loading"}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("disputeSubmit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * EN: One to five stars and an optional comment, posted once.
 * VI: Một tới năm sao và một nhận xét tuỳ chọn, gửi một lần.
 */
function ReviewDialog({
  entry,
  open,
  onClose,
  onPosted,
}: {
  entry: OrderEntry | null;
  open: boolean;
  onClose: () => void;
  onPosted: () => void;
}) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function post() {
    if (!entry) return;
    if (rating === 0) {
      setError(t("reviewStarsRequired"));
      return;
    }
    startTransition(async () => {
      try {
        await reviewOrder(entry.order.id, rating, comment);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      onClose();
      onPosted();
      toast.success(t("reviewPosted"), { description: t("reviewPostedBody") });
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("reviewTitle")}</DialogTitle>
          <DialogDescription>{t("reviewBody", { name: entry?.auction?.product.name ?? "" })}</DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          <div className="space-y-2">
            <p id="review-stars-label" className="label-sm text-dim">
              {t("reviewStars")}
            </p>
            <StarInput
              name="review-stars"
              value={rating}
              labelledBy="review-stars-label"
              onChange={(value) => {
                setRating(value);
                setError(null);
              }}
            />
          </div>
          <FormField label={t("reviewComment")} hint={t("reviewCommentHint")}>
            {(field) => (
              <Textarea {...field} rows={3} maxLength={500} value={comment} onChange={(event) => setComment(event.target.value)} />
            )}
          </FormField>
          {error ? (
            <p role="alert" className="label-sm text-danger-text">
              {error}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button onClick={post} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("reviewSubmit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

