"use client";

import { Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

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
import { Textarea } from "@/components/ui/textarea";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import {
  retractBids,
  retractionEligibility,
  type RetractionEligibility,
  type RetractionReason,
} from "@/services/market-service";

interface RetractBidDialogProps {
  auctionId: string;
  /** EN: Re-reads the reader's standing afterwards. / VI: Đọc lại vị thế của người đọc sau đó. */
  onDone: () => void;
}

/**
 * EN: Taking one's bids on a lot back: in the first minutes for any reason, later only for an obvious typo, never in the
 *     last minutes, a few times a month. The rules come from the server when the dialog opens; this only explains them.
 * VI: Rút lại các lượt trả giá của mình trên một lô: trong vài phút đầu vì bất kỳ lý do gì, sau đó chỉ khi gõ nhầm rõ
 *     ràng, không bao giờ trong những phút cuối, vài lần mỗi tháng. Luật lấy từ server khi mở hộp thoại; ở đây chỉ giải
 *     thích lại.
 */
export function RetractBidDialog({ auctionId, onDone }: RetractBidDialogProps) {
  const t = useTranslations("retract");
  const apiError = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [eligibility, setEligibility] = useState<RetractionEligibility | null>(null);
  const [reason, setReason] = useState<RetractionReason | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function openDialog() {
    setOpen(true);
    setError(null);
    setEligibility(null);
    setReason(null);
    retractionEligibility(auctionId)
      .then((answer) => {
        setEligibility(answer);
        setReason(answer.quick ? "QUICK" : answer.typo ? "TYPO" : null);
      })
      .catch((failure: unknown) => setError(apiError(failure)));
  }

  function confirm() {
    if (!reason) return;
    startTransition(async () => {
      try {
        const done = await retractBids(auctionId, reason, note);
        toast.success(t("done", { count: done.bidsRemoved }));
        setOpen(false);
        onDone();
      } catch (failure) {
        setError(apiError(failure));
      }
    });
  }

  const blocked = eligibility?.blockedBy ?? null;

  return (
    <>
      <button
        type="button"
        onClick={openDialog}
        className="label-sm text-muted-foreground hover:text-foreground inline-flex items-center gap-2 py-3 transition-colors"
      >
        <Undo2 className="size-3.5" aria-hidden="true" />
        {t("open")}
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>{t("rules")}</DialogDescription>
          </DialogHeader>

          {eligibility ? (
            <div className="space-y-4">
              {blocked ? (
                <p className="text-warning text-sm" role="status">
                  {t.has(`blocked.${blocked}`) ? t(`blocked.${blocked}`) : t("blocked.RETRACTION_NOT_ALLOWED")}
                </p>
              ) : (
                <fieldset className="space-y-2">
                  <legend className="label-sm text-dim mb-2">{t("reason")}</legend>
                  {(["QUICK", "TYPO"] as const).map((option) => {
                    const allowed = option === "QUICK" ? eligibility.quick : eligibility.typo;
                    return (
                      <label
                        key={option}
                        className={cn(
                          "border-line flex cursor-pointer items-start gap-3 border p-3 text-sm",
                          !allowed && "cursor-not-allowed opacity-50",
                          reason === option && "border-foreground",
                        )}
                      >
                        <input
                          type="radio"
                          name="retract-reason"
                          value={option}
                          checked={reason === option}
                          disabled={!allowed}
                          onChange={() => setReason(option)}
                          className="mt-1"
                        />
                        <span>
                          <span className="font-medium">{t(`reasons.${option}`)}</span>
                          <span className="text-muted-foreground mt-0.5 block text-xs leading-relaxed">
                            {t(`reasonHints.${option}`)}
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </fieldset>
              )}

              {!blocked ? (
                <div className="space-y-2">
                  <Label htmlFor="retract-note">{t("note")}</Label>
                  <Textarea
                    id="retract-note"
                    value={note}
                    maxLength={300}
                    rows={2}
                    onChange={(event) => setNote(event.target.value)}
                  />
                </div>
              ) : null}

              <p className="text-muted-foreground text-xs" data-testid="retract-quota">
                {t("quota", { used: eligibility.usedThisMonth, limit: eligibility.monthlyLimit })}
              </p>
              <p className="text-muted-foreground text-xs">{t("effect")}</p>
            </div>
          ) : error ? null : (
            <p className="text-muted-foreground text-sm">{t("checking")}</p>
          )}

          {error ? (
            <p className="text-destructive text-sm" role="alert">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={confirm} disabled={!reason || !!blocked || isPending} variant="destructive">
              {t("confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
