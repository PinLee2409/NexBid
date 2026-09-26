"use client";

import { Loader2, Receipt, Truck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import type { OrderEntry } from "@/services/order-service";
import { listSoldOrders, shipOrder } from "@/services/seller-service";

/**
 * EN: What this seller has sold (spec §5.3). A paid order waits for the seller to ship it; the buyer then
 *     confirms receipt on their side.
 * VI: Những gì người bán đã bán (spec §5.3). Đơn đã trả chờ người bán gửi hàng; sau đó người mua xác nhận đã
 *     nhận ở phía họ.
 */
export default function SellerOrdersPage() {
  const t = useTranslations("seller");
  const tc = useTranslations("common");

  const { state, refresh } = useAsyncData("seller-orders", listSoldOrders);

  return (
    <>
      <PageHeader title={t("ordersTitle")} description={t("ordersSubtitle")} />

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 3 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState tone="error" icon={Receipt} title={tc("tryAgain")} description={state.error.message} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={Receipt} title={t("ordersEmptyTitle")} description={t("ordersEmptyBody")} />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <OrderRow
                key={entry.order.id}
                entry={entry}
                action={entry.order.status === "PAID" ? <ShipButton entry={entry} onShipped={refresh} /> : null}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function ShipButton({ entry, onShipped }: { entry: OrderEntry; onShipped: () => void }) {
  const t = useTranslations("seller");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  function ship() {
    startTransition(async () => {
      try {
        await shipOrder(entry.order.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(t("shipped"), { description: t("shippedBody") });
      onShipped();
    });
  }

  return (
    <Button size="sm" onClick={ship} disabled={isPending}>
      {isPending ? <Loader2 className="size-4 animate-spin" /> : <Truck className="size-4" />}
      {t("markShipped")}
    </Button>
  );
}
