"use client";

import { Loader2, Receipt, RotateCcw, Truck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
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
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { formatCurrency } from "@/lib/format";
import type { OrderEntry } from "@/services/order-service";
import {
  listSecondChances,
  listSoldOrders,
  offerToRunnerUp,
  shipOrder,
  type SecondChance,
} from "@/services/seller-service";
import type { SecondChanceOffer } from "@/types";

async function loadSales() {
  const [orders, chances] = await Promise.all([listSoldOrders(), listSecondChances()]);
  return { orders, chances: new Map(chances.map((chance) => [chance.auctionId, chance])) };
}

/**
 * EN: The first sale of each lot that fell through unpaid: its second chance is shown there, not on a later sale.
 * VI: Giao dịch đầu tiên của mỗi lô bị đổ vì không trả: cơ hội thứ hai hiện ở đó, không ở giao dịch sau.
 */
function firstUnpaidSales(orders: OrderEntry[]): Set<string> {
  const lots = new Set<string>();
  const sales = new Set<string>();
  for (const entry of [...orders].reverse()) {
    if (entry.order.status !== "CANCELLED" || entry.payment?.status !== "EXPIRED") continue;
    if (lots.has(entry.order.auctionId)) continue;
    lots.add(entry.order.auctionId);
    sales.add(entry.order.id);
  }
  return sales;
}

/**
 * EN: What this seller has sold (spec §5.3). A paid order waits for the seller to ship it; the buyer then
 *     confirms receipt on their side.
 * VI: Những gì người bán đã bán (spec §5.3). Đơn đã trả chờ người bán gửi hàng; sau đó người mua xác nhận đã
 *     nhận ở phía họ.
 */
export default function SellerOrdersPage() {
  const t = useTranslations("seller");

  const { state, refresh } = useAsyncData("seller-orders", loadSales);

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
          <ApiErrorState icon={Receipt} error={state.error} />
        ) : state.data.orders.length === 0 ? (
          <EmptyState icon={Receipt} title={t("ordersEmptyTitle")} description={t("ordersEmptyBody")} />
        ) : (
          <SalesList orders={state.data.orders} chances={state.data.chances} onChanged={refresh} />
        )}
      </div>
    </>
  );
}

function SalesList({
  orders,
  chances,
  onChanged,
}: {
  orders: OrderEntry[];
  chances: Map<string, SecondChance>;
  onChanged: () => void;
}) {
  const unpaid = firstUnpaidSales(orders);

  return (
    <ul>
      {orders.map((entry) => {
        const chance = unpaid.has(entry.order.id) ? chances.get(entry.order.auctionId) : undefined;
        return (
          <OrderRow
            key={entry.order.id}
            entry={entry}
            action={
              entry.order.status === "PAID" ? (
                <ShipButton entry={entry} onShipped={onChanged} />
              ) : chance ? (
                <SecondChanceAction entry={entry} chance={chance} onOffered={onChanged} />
              ) : null
            }
          />
        );
      })}
    </ul>
  );
}

/**
 * EN: What can still happen to a sale the winner left unpaid (spec §17): offer it to the next bidder once, or
 *     see how that went.
 * VI: Điều còn có thể xảy ra với giao dịch người thắng bỏ không trả (spec §17): đề nghị cho người trả giá kế tiếp
 *     một lần, hoặc xem kết quả của lần đó.
 */
function SecondChanceAction({
  entry,
  chance,
  onOffered,
}: {
  entry: OrderEntry;
  chance: SecondChance;
  onOffered: () => void;
}) {
  const t = useTranslations("seller");

  if (chance.offer) return <OfferOutcome offer={chance.offer} />;
  if (chance.runnerUpBid === null) {
    return <p className="label-sm text-dim shrink-0">{t("secondChanceNoRunnerUp")}</p>;
  }
  if (!chance.canOffer) return null;
  return <OfferButton name={entry.auction?.product.name ?? ""} chance={chance} onOffered={onOffered} />;
}

function OfferOutcome({ offer }: { offer: SecondChanceOffer }) {
  const t = useTranslations("seller");
  const format = useFormatter();
  const amount = formatCurrency(offer.amount);

  return (
    <div className="shrink-0 text-sm">
      {offer.status === "PENDING" ? (
        <>
          <p className="label text-signal-text">{t("secondChancePending", { amount })}</p>
          <p className="mono-figure text-dim mt-1 text-[11px]">
            {t("secondChanceAnswerBy", {
              date: format.dateTime(new Date(offer.expiresAt), { dateStyle: "medium", timeStyle: "short" }),
            })}
          </p>
        </>
      ) : offer.status === "ACCEPTED" ? (
        <p className="label text-success">{t("secondChanceAccepted", { amount })}</p>
      ) : (
        <p className="label text-dim">
          {offer.status === "DECLINED" ? t("secondChanceDeclined") : t("secondChanceExpired")}
        </p>
      )}
    </div>
  );
}

function OfferButton({ name, chance, onOffered }: { name: string; chance: SecondChance; onOffered: () => void }) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const amount = formatCurrency(chance.runnerUpBid ?? 0);

  function send() {
    startTransition(async () => {
      try {
        await offerToRunnerUp(chance.auctionId);
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      setOpen(false);
      toast.success(t("secondChanceSent"), { description: t("secondChanceSentBody") });
      onOffered();
    });
  }

  return (
    <div className="shrink-0">
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        <RotateCcw className="size-4" />
        {t("secondChanceOffer")}
      </Button>
      <p className="mono-figure text-dim mt-1.5 text-[11px]">{t("secondChanceNextBid", { amount })}</p>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("secondChanceTitle")}</DialogTitle>
            <DialogDescription>{t("secondChanceBody", { name, amount })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={isPending}>
              {tc("cancel")}
            </Button>
            <Button onClick={send} disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("secondChanceConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
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
