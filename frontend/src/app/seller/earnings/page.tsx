"use client";

import { Banknote, Percent, Wallet } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";

import { BankAccountForm } from "@/components/account/bank-account-form";
import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton, StatTileSkeleton } from "@/components/common/loading-skeleton";
import { StatTile } from "@/components/common/stat-tile";
import { PageHeader } from "@/components/layout/page-header";
import { useAsyncData } from "@/hooks/use-async-data";
import { useMoney } from "@/hooks/use-money";
import { cn } from "@/lib/utils";
import { getEarnings, type SellerEarnings } from "@/services/payout-service";

/**
 * EN: What NexBid owes the seller. A sale counts once its order completes; NexBid keeps its fee and transfers the rest
 *     to the seller's bank account.
 * VI: NexBid đang nợ người bán bao nhiêu. Một giao dịch được tính khi đơn hoàn tất; NexBid giữ phí sàn và chuyển phần
 *     còn lại vào tài khoản ngân hàng của người bán.
 */
export default function SellerEarningsPage() {
  const t = useTranslations("seller");
  const money = useMoney();
  const { state, refresh } = useAsyncData("seller-earnings", getEarnings);

  return (
    <>
      <PageHeader title={t("earningsTitle")} description={t("earningsSubtitle")} />

      {state.status === "error" ? (
        <div className="mt-10">
          <ApiErrorState icon={Wallet} error={state.error} />
        </div>
      ) : (
        <>
          <div className="mt-10 grid gap-8 sm:grid-cols-3">
            {state.status === "loading" ? (
              Array.from({ length: 3 }).map((_, index) => <StatTileSkeleton key={index} />)
            ) : (
              <>
                <StatTile icon={Wallet} label={t("earningsBalance")} value={money.currency(state.data.balance)} />
                <StatTile icon={Banknote} label={t("earningsPaidOut")} value={money.currency(state.data.paidOut)} />
                <StatTile icon={Percent} label={t("earningsFee")} value={`${state.data.feePercent}%`} />
              </>
            )}
          </div>

          <div className="mt-16 grid gap-12 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-16">
            <section aria-labelledby="earnings-heading">
              <h2 id="earnings-heading" className="display border-line border-b pb-4 text-2xl">
                {t("earningsSales")}
              </h2>
              {state.status === "loading" ? (
                <div>
                  {Array.from({ length: 3 }).map((_, index) => (
                    <AuctionListRowSkeleton key={index} />
                  ))}
                </div>
              ) : state.data.earnings.length === 0 ? (
                <div className="mt-8">
                  <EmptyState icon={Wallet} title={t("earningsEmptyTitle")} description={t("earningsEmptyBody")} />
                </div>
              ) : (
                <EarningList data={state.data} />
              )}
            </section>

            <aside className="space-y-12">
              <section aria-labelledby="bank-heading">
                <h2 id="bank-heading" className="display border-line border-b pb-4 text-2xl">
                  {t("bankTitle")}
                </h2>
                {state.status === "loading" ? (
                  <StatTileSkeleton />
                ) : (
                  <BankAccountForm
                    key={state.data.account?.accountNumber ?? "none"}
                    initial={state.data.account}
                    hint={t("bankHint")}
                    onSaved={refresh}
                  />
                )}
              </section>

              {state.status === "success" && state.data.payouts.length > 0 ? (
                <section aria-labelledby="payouts-heading">
                  <h2 id="payouts-heading" className="display border-line border-b pb-4 text-2xl">
                    {t("payoutHistory")}
                  </h2>
                  <PayoutList data={state.data} />
                </section>
              ) : null}
            </aside>
          </div>
        </>
      )}
    </>
  );
}

function EarningList({ data }: { data: SellerEarnings }) {
  const t = useTranslations("seller");
  const money = useMoney();
  const format = useFormatter();

  return (
    <ul>
      {data.earnings.map((earning) => (
        <li key={earning.id} className="border-line flex flex-wrap items-center gap-x-6 gap-y-2 border-b py-4">
          <div className="min-w-[10rem] flex-1">
            <p className="font-medium">
              {earning.auction ? (
                <Link href={`/auctions/${earning.auction.id}`} className="hover:text-signal-text transition-colors">
                  {earning.auction.product.name}
                </Link>
              ) : (
                "—"
              )}
            </p>
            <p className="mono-figure text-dim mt-1 text-[11px]">
              {format.dateTime(new Date(earning.createdAt), { dateStyle: "medium" })}
              {" · "}
              {t("earningsBreakdown", { gross: money.currency(earning.gross), fee: money.currency(earning.fee) })}
            </p>
          </div>
          <div className="text-right">
            <p className="figure text-lg">{money.currency(earning.net)}</p>
            <p className={cn("label-sm mt-1", earning.payoutId ? "text-success" : "text-warning")}>
              {earning.payoutId ? t("earningPaid") : t("earningWaiting")}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}

function PayoutList({ data }: { data: SellerEarnings }) {
  const money = useMoney();
  const format = useFormatter();

  return (
    <ul>
      {data.payouts.map((payout) => (
        <li key={payout.id} className="border-line border-b py-3">
          <div className="flex items-baseline justify-between gap-4">
            <span className="figure">{money.currency(payout.amount)}</span>
            <span className="mono-figure text-dim text-[11px]">
              {format.dateTime(new Date(payout.createdAt), { dateStyle: "medium" })}
            </span>
          </div>
          <p className="mono-figure text-dim mt-1 text-[11px]">
            {payout.account.bankName} ·••• {payout.account.accountNumber.slice(-4)} · {payout.reference}
          </p>
        </li>
      ))}
    </ul>
  );
}
