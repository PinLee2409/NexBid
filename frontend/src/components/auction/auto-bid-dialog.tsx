"use client";

import { Bot, Info } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useBidRejectionMessage } from "@/hooks/use-labels";
import { CURRENCY, formatCurrency, parseCurrencyInput } from "@/lib/format";
import { cn } from "@/lib/utils";
import { removeAutoBid, saveAutoBid } from "@/services/bid-service";
import type { AutoBid } from "@/types";

interface AutoBidDialogProps {
  auctionId: string;
  minimumNextBid: number;
  autoBid: AutoBid | null;
  disabled?: boolean;
  /** Lets the terminal drop the trigger straight into its own grid. */
  triggerClassName?: string;
}

/**
 * Auto bid (spec §14). The maximum is private: it is never rendered anywhere
 * except this dialog, which only the owner can open.
 */
export function AutoBidDialog({
  auctionId,
  minimumNextBid,
  autoBid,
  disabled = false,
  triggerClassName,
}: AutoBidDialogProps) {
  const t = useTranslations("auction");
  const tc = useTranslations("common");
  const rejectionMessage = useBidRejectionMessage();

  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(
    autoBid ? String(autoBid.maxAmount) : String(minimumNextBid),
  );
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleSave() {
    const amount = parseCurrencyInput(value);

    startTransition(async () => {
      const response = await saveAutoBid(auctionId, amount ?? Number.NaN);

      if (!response.ok) {
        setError(rejectionMessage(response));
        return;
      }

      setError(null);
      setOpen(false);
      toast.success(t("autoBidSaved"), {
        description: t("autoBidActive", {
          amount: formatCurrency(response.autoBid.maxAmount),
        }),
      });
    });
  }

  function handleRemove() {
    startTransition(async () => {
      await removeAutoBid(auctionId);
      setOpen(false);
      toast.success(t("autoBidRemoved"));
    });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen(true)}
        className={cn(
          "label text-muted-foreground hover:text-foreground inline-flex w-full items-center justify-center gap-2 py-4 transition-colors disabled:opacity-40",
          triggerClassName,
        )}
      >
        <Bot className="size-3.5" />
        {autoBid ? t("autoBidActive", { amount: formatCurrency(autoBid.maxAmount) }) : t("setAutoBid")}
      </button>

      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("autoBidTitle")}</DialogTitle>
          <DialogDescription>{t("autoBidBody")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="auto-bid-max">{t("autoBidMax")}</Label>
          <div className="relative">
            <span
              className="text-dim figure pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-sm"
              aria-hidden="true"
            >
              {CURRENCY.symbol}
            </span>
            <Input
              id="auto-bid-max"
              inputMode="numeric"
              value={value}
              onChange={(event) => {
                setValue(event.target.value.replace(/[^\d]/g, ""));
                setError(null);
              }}
              aria-invalid={error !== null}
              aria-describedby={error ? "auto-bid-error" : "auto-bid-hint"}
              className="figure h-12 pl-8 text-lg"
            />
          </div>

          {error ? (
            <p id="auto-bid-error" role="alert" className="text-danger-text text-sm">
              {error}
            </p>
          ) : (
            <p
              id="auto-bid-hint"
              className="text-muted-foreground flex items-start gap-1.5 text-xs"
            >
              <Info className="mt-0.5 size-3 shrink-0" aria-hidden="true" />
              {tc("minimumNextBid")}: {formatCurrency(minimumNextBid)}
            </p>
          )}
        </div>

        <DialogFooter className="sm:justify-between">
          {autoBid ? (
            <Button
              variant="ghost"
              onClick={handleRemove}
              disabled={isPending}
              className="text-danger-text hover:text-danger-text"
            >
              {t("removeAutoBid")}
            </Button>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={isPending}
            >
              {tc("cancel")}
            </Button>
            <Button onClick={handleSave} disabled={isPending}>
              {isPending ? tc("saving") : tc("save")}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
