"use client";

import { CreditCard, Loader2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { startVnpayCheckout } from "@/services/payment-service";

/**
 * EN: Sends the winner to VNPay to pay. The browser leaves the site here; VNPay brings it back to
 *     /payments/vnpay-return with the result.
 * VI: Đưa người thắng sang VNPay để thanh toán. Trình duyệt rời site tại đây; VNPay đưa nó quay lại
 *     /payments/vnpay-return kèm kết quả.
 */
export function PayWithVnpay({ paymentId, className }: { paymentId: string; className?: string }) {
  const t = useTranslations("account");
  const locale = useLocale();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  function handlePay() {
    startTransition(async () => {
      try {
        const url = await startVnpayCheckout(paymentId, locale);
        window.location.assign(url);
      } catch (error) {
        toast.error(t("paymentFailed"), { description: errorMessage(error) });
      }
    });
  }

  return (
    <Button onClick={handlePay} disabled={isPending} className={className}>
      {isPending ? <Loader2 className="size-4 animate-spin" /> : <CreditCard className="size-4" />}
      {t("payWithVnpay")}
    </Button>
  );
}
