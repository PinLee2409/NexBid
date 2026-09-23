"use client";

import { Trophy } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";

import { AuctionListItem } from "@/components/auction/auction-list-item";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";

import { cn } from "@/lib/utils";
import { listMyWins, type MyWinEntry } from "@/services/account-service";

export default function MyWinsPage() {
  const t = useTranslations("account");
  const tc = useTranslations("common");

  const { state } = useAsyncData("my-wins", listMyWins);

  return (
    <>
      <PageHeader title={t("winsTitle")} description={t("winsSubtitle")} />

      <div className="mt-6">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 2 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState
            tone="error"
            icon={Trophy}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Trophy}
            title={t("winsEmptyTitle")}
            description={t("winsEmptyBody")}
            action={{ label: tc("viewAll"), href: "/auctions" }}
          />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <WinRow key={entry.auction.id} entry={entry} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function WinRow({ entry }: { entry: MyWinEntry }) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const status = entry.payment?.status ?? "PENDING";
  const needsPayment = status === "PENDING" || status === "FAILED";

  return (
    <AuctionListItem
      auction={entry.auction}
      meta={{
        label: t("paymentStatus"),
        value: (
          <span className="flex flex-col">
            <span
              className={cn(
                "text-[13px]",
                status === "SUCCESS" && "text-success",
                needsPayment && "text-danger-text",
              )}
            >
              {labels.paymentStatus(status)}
            </span>
            {entry.payment ? (
              <span className="text-muted-foreground text-[11px] font-normal">
                {status === "SUCCESS"
                  ? t("paidOn", {
                      date: format.dateTime(new Date(entry.payment.updatedAt), {
                        dateStyle: "medium",
                      }),
                    })
                  : t("paymentDue", {
                      date: format.dateTime(new Date(entry.payment.expiredAt), {
                        dateStyle: "medium",
                      }),
                    })}
              </span>
            ) : null}
          </span>
        ),
      }}
      actions={
        needsPayment && entry.order ? (
          <Button asChild size="sm">
            <Link href={`/orders?highlight=${entry.order.id}`}>
              {t("payNow")}
            </Link>
          </Button>
        ) : (
          <Button asChild size="sm" variant="outline">
            <Link href="/orders">{tc("viewDetails")}</Link>
          </Button>
        )
      }
      className={needsPayment ? "border-danger-text/30" : undefined}
    />
  );
}
