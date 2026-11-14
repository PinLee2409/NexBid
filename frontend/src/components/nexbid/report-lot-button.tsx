"use client";

import { Flag, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
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
import { useApiErrorMessage } from "@/hooks/use-labels";
import { REPORT_REASONS, reportLot, type ReportReason } from "@/services/report-service";
import { useSession } from "@/services/session-service";
import type { AuctionStatus } from "@/types";

const OPEN: AuctionStatus[] = ["SCHEDULED", "ACTIVE"];

/**
 * EN: A quiet "Report this lot" link for counterfeit, misdescribed or prohibited goods. Hidden from the seller and on
 *     closed lots; a visitor is sent to sign in first.
 * VI: Link "Báo cáo lô này" kín đáo cho hàng giả, sai mô tả hoặc hàng cấm. Ẩn với người bán và với lô đã đóng; khách
 *     được đưa đi đăng nhập trước.
 */
export function ReportLotButton({ auctionId, sellerId, status }: { auctionId: string; sellerId: string; status: AuctionStatus }) {
  const t = useTranslations("auction");
  const tc = useTranslations("common");
  const { user, hydrated } = useSession();
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason | "">("");
  const [details, setDetails] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  if (!hydrated || !OPEN.includes(status) || user?.id === sellerId) return null;

  if (!user) {
    return (
      <Link
        href={`/login?redirect=${encodeURIComponent(`/auctions/${auctionId}`)}`}
        className="text-dim hover:text-foreground inline-flex items-center gap-1.5 text-xs transition-colors"
      >
        <Flag className="size-3.5" aria-hidden="true" />
        {t("reportSignIn")}
      </Link>
    );
  }

  function send() {
    if (!reason) {
      setError(t("reportReasonRequired"));
      return;
    }
    if (details.trim().length < 10) {
      setError(t("reportDetailsShort"));
      return;
    }
    startTransition(async () => {
      try {
        await reportLot(auctionId, reason, details);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      setOpen(false);
      setReason("");
      setDetails("");
      toast.success(t("reportSent"), { description: t("reportSentBody") });
    });
  }

  return (
    <>
      <button
        type="button"
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
        className="text-dim hover:text-foreground inline-flex items-center gap-1.5 text-xs transition-colors"
      >
        <Flag className="size-3.5" aria-hidden="true" />
        {t("reportLot")}
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("reportTitle")}</DialogTitle>
            <DialogDescription>{t("reportBody")}</DialogDescription>
          </DialogHeader>

          <FormField label={t("reportReasonLabel")} required>
            {(field) => (
              <Select
                value={reason}
                onValueChange={(value) => {
                  setReason(value as ReportReason);
                  setError(null);
                }}
              >
                <SelectTrigger id={field.id} className="w-full">
                  <SelectValue placeholder={t("reportReasonPlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {REPORT_REASONS.map((value) => (
                    <SelectItem key={value} value={value}>
                      {t(`reportReason.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>
          <FormField label={t("reportDetailsLabel")} hint={t("reportDetailsHint")} error={error ?? undefined} required>
            {(field) => (
              <Textarea
                {...field}
                value={details}
                maxLength={1000}
                rows={4}
                disabled={isPending}
                onChange={(event) => {
                  setDetails(event.target.value);
                  setError(null);
                }}
              />
            )}
          </FormField>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={isPending}>
              {tc("cancel")}
            </Button>
            <Button onClick={send} disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("reportSend")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
