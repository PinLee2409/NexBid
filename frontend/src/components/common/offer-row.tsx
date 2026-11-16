"use client";

import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";

import { useMoney } from "@/hooks/use-money";
import { cn } from "@/lib/utils";
import type { LotOffer } from "@/services/market-service";

/**
 * EN: One offer as a row: the lot, the amounts, its state and when it lapses; the caller adds the buttons.
 * VI: Một đề nghị dạng một dòng: lô, các mức giá, trạng thái và lúc hết hạn; bên gọi thêm các nút.
 */
export function OfferRow({
  offer,
  showBuyer = false,
  children,
}: {
  offer: LotOffer;
  showBuyer?: boolean;
  children?: ReactNode;
}) {
  const t = useTranslations("deals");
  const money = useMoney();
  const format = useFormatter();
  const open = offer.status === "PENDING" || offer.status === "COUNTERED";

  return (
    <li
      className="border-line flex flex-col gap-3 border-b py-4 sm:flex-row sm:items-center sm:justify-between"
      data-testid="offer-row"
    >
      <div className="min-w-0">
        <Link
          href={`/auctions/${offer.auctionId}`}
          className="hover:text-signal-text font-medium [overflow-wrap:anywhere]"
        >
          {offer.lotTitle}
        </Link>
        <p className="text-muted-foreground mt-1 text-sm">
          {showBuyer ? `${offer.buyer} · ` : ""}
          {t("offerOf", { amount: money.currency(offer.amount) })}
          {offer.counterAmount !== null
            ? ` · ${t("counterOf", { amount: money.currency(offer.counterAmount) })}`
            : ""}
          {offer.buyNowPrice !== null
            ? ` · ${t("buyNowOf", { amount: money.currency(offer.buyNowPrice) })}`
            : ""}
        </p>
        {offer.message ? (
          <p className="text-dim mt-1 text-sm">“{offer.message}”</p>
        ) : null}
        <p className="text-dim mt-1 text-xs">
          <span className={cn("label-sm", open ? "text-signal-text" : "")}>
            {t(`status.${offer.status}`)}
          </span>
          {open
            ? ` · ${t("offerExpires", {
                when: format.dateTime(new Date(offer.expiresAt), {
                  dateStyle: "medium",
                  timeStyle: "short",
                }),
              })}`
            : ""}
        </p>
      </div>
      {children ? (
        <div className="flex shrink-0 flex-wrap gap-2">{children}</div>
      ) : null}
    </li>
  );
}
