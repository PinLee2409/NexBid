"use client";

import { Copy } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { useMoney } from "@/hooks/use-money";
import type { BankAccount } from "@/services/bank-account-service";

/**
 * EN: What an admin types into the bank app: the amount and the account, each with a copy button. Used for payouts to
 *     sellers and refunds to buyers.
 * VI: Những gì admin nhập vào ứng dụng ngân hàng: số tiền và tài khoản, mỗi dòng có nút sao chép. Dùng cho chi trả người
 *     bán và hoàn tiền người mua.
 */
export function TransferDetails({ amount, account }: { amount: number; account: BankAccount }) {
  const t = useTranslations("admin");
  const money = useMoney();

  function copy(text: string) {
    void navigator.clipboard?.writeText(text).then(() => toast.success(t("payoutsCopied")));
  }

  return (
    <dl className="border-line divide-line divide-y border-y text-sm">
      {[
        [t("payoutsAmount"), money.currency(amount), String(amount)],
        [t("payoutsBank"), account.bankName, account.bankName],
        [t("payoutsNumber"), account.accountNumber, account.accountNumber],
        [t("payoutsHolder"), account.accountHolder, account.accountHolder],
      ].map(([label, shown, raw]) => (
        <div key={label} className="flex items-center justify-between gap-4 py-2.5">
          <dt className="label-sm text-dim">{label}</dt>
          <dd className="flex items-center gap-2">
            <span className="mono-figure">{shown}</span>
            <button
              type="button"
              onClick={() => copy(raw)}
              className="text-dim hover:text-foreground"
              aria-label={t("payoutsCopy", { what: label })}
            >
              <Copy className="size-3.5" />
            </button>
          </dd>
        </div>
      ))}
    </dl>
  );
}
