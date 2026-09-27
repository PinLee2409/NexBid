"use client";

import { Loader2, Receipt, Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage, useEnumLabels } from "@/hooks/use-labels";
import { formatCurrency } from "@/lib/format";
import { listOrders, refundOrder } from "@/services/admin-service";
import type { OrderEntry } from "@/services/order-service";
import type { OrderStatus } from "@/types";

type OrderTab = "ALL" | OrderStatus;

const TABS: OrderTab[] = ["ALL", "PAID", "PROCESSING", "COMPLETED", "CANCELLED"];

/**
 * EN: Every order, by status. A paid order whose goods are not yet confirmed received can be refunded:
 *     the order is cancelled and its payment marked REFUNDED.
 * VI: Mọi đơn hàng, theo trạng thái. Đơn đã trả mà hàng chưa được xác nhận đã nhận thì hoàn tiền được: đơn bị
 *     huỷ và khoản thanh toán thành REFUNDED.
 */
export default function AdminOrdersPage() {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const labels = useEnumLabels();

  const [tab, setTab] = useState<OrderTab>("ALL");
  const { state, refresh } = useAsyncData(`admin-orders-${tab}`, () => listOrders(tab === "ALL" ? [] : [tab]));
  const [refunding, setRefunding] = useState<OrderEntry | null>(null);

  return (
    <>
      <PageHeader title={t("ordersTitle")} description={t("ordersSubtitle")} />

      <Tabs value={tab} onValueChange={(value) => setTab(value as OrderTab)} className="mt-6 block">
        <TabsList>
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {key === "ALL" ? t("tabAllOrders") : labels.orderStatus(key)}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-10">
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
                  action={
                    entry.order.status === "PAID" || entry.order.status === "PROCESSING" ? (
                      <Button size="sm" variant="outline" onClick={() => setRefunding(entry)}>
                        <Undo2 className="size-4" />
                        {t("refund")}
                      </Button>
                    ) : null
                  }
                />
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>

      <RefundDialog entry={refunding} onClose={() => setRefunding(null)} onRefunded={refresh} />
    </>
  );
}

function RefundDialog({
  entry,
  onClose,
  onRefunded,
}: {
  entry: OrderEntry | null;
  onClose: () => void;
  onRefunded: () => void;
}) {
  const t = useTranslations("admin");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  function refund() {
    if (!entry) return;
    startTransition(async () => {
      try {
        await refundOrder(entry.order.id);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      onClose();
      onRefunded();
      toast.success(t("refunded"), { description: t("refundedBody") });
    });
  }

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("refundTitle")}</DialogTitle>
          <DialogDescription>{t("refundBody")}</DialogDescription>
        </DialogHeader>

        {entry ? (
          <div className="border-line flex items-center justify-between gap-4 border-y px-4 py-4">
            <span className="text-muted-foreground min-w-0 truncate text-sm">
              {entry.auction?.product.name ?? "—"}
            </span>
            <span className="figure text-xl">{formatCurrency(entry.order.amount)}</span>
          </div>
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {tc("cancel")}
          </Button>
          <Button variant="destructive" onClick={refund} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("confirmRefund")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
