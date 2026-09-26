"use client";

import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";

import { ProductPhoto } from "@/components/common/product-photo";
import { useEnumLabels } from "@/hooks/use-labels";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { OrderEntry } from "@/services/order-service";
import type { OrderStatus } from "@/types";

// EN: Red while money is owed, amber while goods are on the way, green once settled. / VI: Đỏ khi còn nợ tiền, vàng khi hàng đang đi, xanh khi đã xong.
const STATUS_TONE: Record<OrderStatus, string | undefined> = {
  PENDING_PAYMENT: "text-danger-text",
  PAID: "text-success",
  PROCESSING: "text-warning",
  COMPLETED: "text-success",
  CANCELLED: "text-dim",
};

/**
 * EN: One order as a row — the lot, when it was placed, the total and where it stands — with room on the
 *     right for whatever the person looking can do next. Shared by the buyer's, seller's and admin's lists.
 * VI: Một đơn hàng dạng một dòng — lô, ngày đặt, tổng tiền và trạng thái — chừa chỗ bên phải cho việc người
 *     đang xem có thể làm tiếp. Dùng chung cho danh sách của người mua, người bán và admin.
 */
export function OrderRow({ entry, action }: { entry: OrderEntry; action?: ReactNode }) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const cover = entry.auction?.product.images[0];
  const refunded = entry.payment?.status === "REFUNDED";

  return (
    <li className="border-line flex flex-wrap items-center gap-x-6 gap-y-4 border-t py-5 last:border-b">
      <div className="on-media bg-surface relative size-16 shrink-0 overflow-hidden">
        <ProductPhoto src={cover?.url} alt={cover?.alt} sizes="64px" className="object-cover" fallback="icon" />
      </div>

      <div className="min-w-[10rem] flex-1">
        <p className="mono-figure text-dim text-[11px]">
          {t("orderNumber", { id: entry.order.id.slice(-8).toUpperCase() })}
        </p>
        <h3 className="display mt-1 text-lg sm:text-xl">
          {entry.auction ? (
            <Link href={`/auctions/${entry.auction.id}`} className="hover:text-signal-text transition-colors">
              {entry.auction.product.name}
            </Link>
          ) : (
            "—"
          )}
        </h3>
        <p className="mono-figure text-dim mt-1.5 text-[11px]">
          {t("orderPlaced", {
            date: format.dateTime(new Date(entry.order.createdAt), { dateStyle: "medium" }),
          })}
        </p>
      </div>

      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{t("orderTotal")}</p>
        <p className="figure text-lg">{formatCurrency(entry.order.amount)}</p>
      </div>

      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{tc("status")}</p>
        <p className={cn("label", STATUS_TONE[entry.order.status])}>{labels.orderStatus(entry.order.status)}</p>
        {refunded ? <p className="label-sm text-dim mt-1">{labels.paymentStatus("REFUNDED")}</p> : null}
      </div>

      {action}
    </li>
  );
}
