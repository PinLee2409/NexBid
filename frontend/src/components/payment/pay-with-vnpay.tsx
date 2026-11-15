"use client";

import { CreditCard, Loader2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { AddressForm } from "@/components/account/address-form";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { getShippingAddress } from "@/services/account-service";
import { ApiError } from "@/services/api/http";
import { quoteShipping, startVnpayCheckout, type ShippingQuote } from "@/services/payment-service";
import type { ShippingAddress } from "@/types";

/**
 * EN: Sends the winner to VNPay to pay. First the shipping to their address is fixed and shown with the goods and the
 *     total; a buyer with no delivery address is asked for one first. Then the browser leaves the site for VNPay,
 *     which brings it back to /payments/vnpay-return with the result.
 * VI: Đưa người thắng sang VNPay để thanh toán. Trước hết phí ship tới địa chỉ của họ được chốt và hiện cùng tiền hàng
 *     và tổng; người mua chưa có địa chỉ nhận hàng được hỏi trước. Sau đó trình duyệt rời site sang VNPay, nơi đưa nó
 *     quay lại /payments/vnpay-return kèm kết quả.
 */
export function PayWithVnpay({ paymentId, className }: { paymentId: string; className?: string }) {
  const t = useTranslations("account");
  const locale = useLocale();
  const money = useMoney();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [address, setAddress] = useState<{ initial: ShippingAddress | null } | null>(null);
  const [quote, setQuote] = useState<ShippingQuote | null>(null);

  function review() {
    startTransition(async () => {
      try {
        setQuote(await quoteShipping(paymentId));
      } catch (error) {
        if (error instanceof ApiError && error.code === "ADDRESS_REQUIRED") {
          setAddress({ initial: null });
          return;
        }
        toast.error(t("paymentFailed"), { description: errorMessage(error) });
      }
    });
  }

  function changeAddress() {
    startTransition(async () => {
      const saved = await getShippingAddress().catch(() => null);
      setQuote(null);
      setAddress({ initial: saved });
    });
  }

  function pay() {
    startTransition(async () => {
      try {
        window.location.assign(await startVnpayCheckout(paymentId, locale));
      } catch (error) {
        toast.error(t("paymentFailed"), { description: errorMessage(error) });
      }
    });
  }

  return (
    <>
      <Button onClick={review} disabled={isPending} className={className}>
        {isPending && !quote ? <Loader2 className="size-4 animate-spin" /> : <CreditCard className="size-4" />}
        {t("payWithVnpay")}
      </Button>

      <Dialog open={quote !== null} onOpenChange={(open) => (open ? null : setQuote(null))}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("paymentReviewTitle")}</DialogTitle>
            <DialogDescription>{t("paymentReviewBody")}</DialogDescription>
          </DialogHeader>
          {quote ? (
            <dl className="divide-line border-line divide-y border-y text-sm">
              <div className="flex items-baseline justify-between gap-4 py-2.5">
                <dt className="text-dim">{t("paymentGoods")}</dt>
                <dd className="figure">{money.currency(quote.amount)}</dd>
              </div>
              <div className="flex items-baseline justify-between gap-4 py-2.5">
                <dt className="text-dim">
                  {t("paymentShipping")}
                  <span className="label-sm text-dim block">
                    {quote.source === "GHN" ? t("paymentShippingGhn") : t("paymentShippingFlat")}
                  </span>
                </dt>
                <dd className="figure">{money.currency(quote.shippingFee)}</dd>
              </div>
              <div className="flex items-baseline justify-between gap-4 py-2.5">
                <dt className="font-medium">{t("paymentTotal")}</dt>
                <dd className="figure text-lg" data-testid="payment-total">
                  {money.currency(quote.total)}
                </dd>
              </div>
            </dl>
          ) : null}
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={changeAddress} disabled={isPending}>
              {t("paymentChangeAddress")}
            </Button>
            <Button onClick={pay} disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : <CreditCard className="size-4" />}
              {t("paymentContinue")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={address !== null} onOpenChange={(open) => (open ? null : setAddress(null))}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("addressTitle")}</DialogTitle>
            <DialogDescription>{t("addressBeforePaying")}</DialogDescription>
          </DialogHeader>
          {address ? (
            <AddressForm
              initial={address.initial}
              submitLabel={t("addressSaveAndPay")}
              onSaved={() => {
                setAddress(null);
                review();
              }}
            />
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
