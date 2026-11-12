"use client";

import { Loader2, PackageCheck, Receipt, Star } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

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
import { Textarea } from "@/components/ui/textarea";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { confirmReceipt, listOrders, reviewOrder, type OrderEntry } from "@/services/order-service";
import { PayWithVnpay } from "@/components/payment/pay-with-vnpay";

export default function OrdersPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("orders", listOrders);
  // EN: Kept after closing, so the dialog keeps its words while it fades. / VI: Giữ sau khi đóng, để hộp thoại giữ chữ khi mờ dần.
  const [reviewing, setReviewing] = useState<OrderEntry | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);

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
    </>
  );
}

function OrderActions({
  entry,
  onReceived,
  onReview,
}: {
  entry: OrderEntry;
  onReceived: () => void;
  onReview: () => void;
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
    <Button size="sm" variant="outline" onClick={confirm} disabled={isPending}>
      {isPending ? <Loader2 className="size-4 animate-spin" /> : <PackageCheck className="size-4" />}
      {t("confirmReceipt")}
    </Button>
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

