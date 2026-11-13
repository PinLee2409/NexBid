"use client";

import { AlertTriangle } from "lucide-react";
import { useTranslations } from "next-intl";

import { FormField } from "@/components/common/form-field";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { getRefundAccount } from "@/services/admin-service";

import { TransferDetails } from "./transfer-details";

/**
 * EN: The buyer's refund account for an order, loaded when a refund dialog opens. Null: the buyer has not said.
 * VI: Tài khoản nhận hoàn tiền của người mua cho một đơn, tải khi hộp hoàn tiền mở. Null: người mua chưa cho biết.
 */
export function useRefundAccount(orderId: string | null) {
  return useAsyncData(`refund-account-${orderId ?? "none"}`, () =>
    orderId ? getRefundAccount(orderId) : Promise.resolve(null),
  ).state;
}

/**
 * EN: The refund as a bank transfer: where to send the money, then the bank's reference once it is sent. Without an
 *     account there is nothing to transfer to, and the dialog says so.
 * VI: Hoàn tiền dưới dạng chuyển khoản: chuyển tiền về đâu, rồi mã giao dịch ngân hàng sau khi đã chuyển. Không có tài
 *     khoản thì không có chỗ để chuyển, và hộp thoại nói rõ điều đó.
 */
export function RefundTransfer({
  account,
  amount,
  reference,
  error,
  disabled,
  onReference,
}: {
  account: ReturnType<typeof useRefundAccount>;
  amount: number;
  reference: string;
  error: string | null;
  disabled?: boolean;
  onReference: (value: string) => void;
}) {
  const t = useTranslations("admin");
  const errorMessage = useApiErrorMessage();

  if (account.status === "loading") {
    return <Skeleton className="h-40 w-full" />;
  }
  if (account.status === "error") {
    return (
      <p role="alert" className="label-sm text-danger-text">
        {errorMessage(account.error)}
      </p>
    );
  }
  if (account.data === null) {
    return (
      <p role="alert" className="border-warning text-warning flex gap-2 border-l-2 py-1 pl-3 text-sm">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" />
        {t("refundNoAccount")}
      </p>
    );
  }

  return (
    <>
      <TransferDetails amount={amount} account={account.data} />
      <FormField label={t("payoutsReference")} hint={t("payoutsReferenceHint")} error={error ?? undefined} required>
        {(field) => (
          <Input
            {...field}
            value={reference}
            maxLength={80}
            disabled={disabled}
            onChange={(event) => onReference(event.target.value)}
          />
        )}
      </FormField>
    </>
  );
}
