"use client";

import { CircleCheck, CircleX, Loader2, Wallet } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { useMoney } from "@/hooks/use-money";
import { confirmVnpayReturn } from "@/services/payment-service";
import type { Payment } from "@/types";

/** EN: VNPay's code when the buyer cancels on its page. / VI: Mã của VNPay khi người mua huỷ trên trang của nó. */
const CANCELLED = "24";

type Result =
  | { status: "checking" }
  | { status: "done"; payment: Payment; responseCode: string | null }
  | { status: "error"; error: unknown };

/**
 * EN: Where VNPay sends the buyer back. The address bar carries VNPay's signed reply; the server checks it and
 *     settles the attempt — the IPN may already have done so, which is fine — and this page says how it went.
 * VI: Nơi VNPay đưa người mua quay lại. Thanh địa chỉ mang phản hồi có chữ ký của VNPay; server kiểm và chốt lần
 *     thử — IPN có thể đã làm trước, không sao — và trang này cho biết kết quả.
 */
export default function VnpayReturnPage() {
  const t = useTranslations("account");
  const money = useMoney();
  const [result, setResult] = useState<Result>({ status: "checking" });
  // EN: Once, even when React runs effects twice in development. / VI: Một lần, kể cả khi React chạy effect hai lần ở môi trường dev.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const query = window.location.search;
    const responseCode = new URLSearchParams(query).get("vnp_ResponseCode");
    confirmVnpayReturn(query)
      .then((payment) => setResult({ status: "done", payment, responseCode }))
      .catch((error: unknown) => setResult({ status: "error", error }));
  }, []);

  if (result.status === "checking") {
    return (
      <div className="flex items-center gap-3 py-20" role="status">
        <Loader2 className="text-signal-text size-5 animate-spin" aria-hidden="true" />
        <p className="text-muted-foreground">{t("vnpayChecking")}</p>
      </div>
    );
  }

  if (result.status === "error") {
    return <ApiErrorState icon={Wallet} error={result.error} />;
  }

  const { payment, responseCode } = result;
  if (payment.status === "SUCCESS") {
    return (
      <EmptyState
        icon={CircleCheck}
        title={t("paymentSucceeded")}
        description={`${money.currency(payment.amount)} · ${t("paymentSucceededBody")}`}
        action={{ label: t("viewOrder"), href: "/orders" }}
        secondaryAction={{ label: t("paymentsTitle"), href: "/payments" }}
      />
    );
  }

  const cancelled = responseCode === CANCELLED;
  return (
    <EmptyState
      tone="error"
      icon={CircleX}
      title={cancelled ? t("vnpayCancelled") : t("paymentFailed")}
      description={cancelled ? t("vnpayCancelledBody") : t("paymentFailedBody")}
      action={{ label: t("paymentsTitle"), href: "/payments" }}
    />
  );
}
