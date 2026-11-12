"use client";

import { CreditCard, Loader2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { AddressForm } from "@/components/account/address-form";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { ApiError } from "@/services/api/http";
import { startVnpayCheckout } from "@/services/payment-service";

/**
 * EN: Sends the winner to VNPay to pay. The browser leaves the site here; VNPay brings it back to
 *     /payments/vnpay-return with the result. A buyer with no delivery address is asked for one first, then goes
 *     straight on to VNPay.
 * VI: Đưa người thắng sang VNPay để thanh toán. Trình duyệt rời site tại đây; VNPay đưa nó quay lại
 *     /payments/vnpay-return kèm kết quả. Người mua chưa có địa chỉ nhận hàng được hỏi trước, rồi đi tiếp sang VNPay.
 */
export function PayWithVnpay({ paymentId, className }: { paymentId: string; className?: string }) {
  const t = useTranslations("account");
  const locale = useLocale();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [askAddress, setAskAddress] = useState(false);

  function handlePay() {
    startTransition(async () => {
      try {
        const url = await startVnpayCheckout(paymentId, locale);
        window.location.assign(url);
      } catch (error) {
        if (error instanceof ApiError && error.code === "ADDRESS_REQUIRED") {
          setAskAddress(true);
          return;
        }
        toast.error(t("paymentFailed"), { description: errorMessage(error) });
      }
    });
  }

  return (
    <>
      <Button onClick={handlePay} disabled={isPending} className={className}>
        {isPending ? <Loader2 className="size-4 animate-spin" /> : <CreditCard className="size-4" />}
        {t("payWithVnpay")}
      </Button>

      <Dialog open={askAddress} onOpenChange={setAskAddress}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("addressTitle")}</DialogTitle>
            <DialogDescription>{t("addressBeforePaying")}</DialogDescription>
          </DialogHeader>
          <AddressForm
            initial={null}
            submitLabel={t("addressSaveAndPay")}
            onSaved={() => {
              setAskAddress(false);
              handlePay();
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}
