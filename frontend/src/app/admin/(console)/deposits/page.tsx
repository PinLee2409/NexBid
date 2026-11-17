"use client";

import { ShieldCheck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import {
  depositsDue,
  markDepositRefunded,
  type DepositDue,
} from "@/services/market-service";

/**
 * EN: Deposits owed back: the bidder, their bank account and the amount. Transfer it, then record the bank reference.
 * VI: Các khoản cọc cần trả lại: người trả giá, tài khoản ngân hàng và số tiền. Chuyển khoản, rồi ghi lại mã giao dịch.
 */
export default function AdminDepositsPage() {
  const t = useTranslations("depositsAdmin");
  const { state, refresh } = useAsyncData("admin-deposits", depositsDue);

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      <div className="mt-8">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={ShieldCheck} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={ShieldCheck}
            title={t("emptyTitle")}
            description={t("emptyBody")}
          />
        ) : (
          <ul data-testid="deposits-due">
            {state.data.map((row) => (
              <DueRow key={row.deposit.id} row={row} onDone={refresh} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function DueRow({ row, onDone }: { row: DepositDue; onDone: () => void }) {
  const t = useTranslations("depositsAdmin");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [reference, setReference] = useState("");
  const [isPending, startTransition] = useTransition();

  return (
    <li className="border-line flex flex-col gap-3 border-b py-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0 text-sm">
        <p className="font-medium">
          {row.userName} ·{" "}
          <span className="mono-figure">
            {money.currency(row.deposit.amount)}
          </span>
        </p>
        <Link
          href={`/auctions/${row.deposit.auctionId}`}
          className="text-muted-foreground hover:text-signal-text"
        >
          {row.deposit.lotTitle}
        </Link>
        <p className="text-dim mt-1 text-xs">
          {row.account
            ? `${row.account.bankName} · ${row.account.accountNumber} · ${row.account.accountHolder}`
            : t("noAccount")}
          {row.deposit.paidAt
            ? ` · ${t("paidOn", { date: format.dateTime(new Date(row.deposit.paidAt), { dateStyle: "medium" }) })}`
            : ""}
        </p>
      </div>
      <form
        className="flex shrink-0 gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          startTransition(async () => {
            try {
              await markDepositRefunded(row.deposit.id, reference.trim());
              toast.success(t("done"));
              onDone();
            } catch (error) {
              toast.error(errorMessage(error));
            }
          });
        }}
      >
        <Input
          aria-label={t("reference")}
          placeholder={t("reference")}
          value={reference}
          onChange={(event) => setReference(event.target.value)}
          className="h-9 w-44"
          maxLength={80}
        />
        <Button
          size="sm"
          type="submit"
          disabled={isPending || !reference.trim() || !row.account}
        >
          {t("markReturned")}
        </Button>
      </form>
    </li>
  );
}
