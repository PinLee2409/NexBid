"use client";

import { Loader2, Receipt, RotateCcw, Truck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { OrderRow } from "@/components/common/order-row";
import { StarInput, StarRating } from "@/components/common/star-rating";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
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
import type { OrderEntry } from "@/services/order-service";
import {
  listSecondChances,
  listSoldOrders,
  offerToRunnerUp,
  rateBuyer,
  shipOrder,
  type SecondChance,
} from "@/services/seller-service";
import { CARRIERS, GHN_CARRIER, OTHER_CARRIER } from "@/lib/carriers";
import { useGhnEnabled } from "@/services/shipping-service";
import type { SecondChanceOffer } from "@/types";
import { useMoney } from "@/hooks/use-money";

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
            messages
            extra={<BuyerRecordLine entry={entry} />}
            action={
              entry.order.status === "PAID" ? (
                <ShipButton entry={entry} onShipped={onChanged} />
              ) : chance ? (
                <SecondChanceAction entry={entry} chance={chance} onOffered={onChanged} />
              ) : entry.order.status === "COMPLETED" ? (
                <div className="flex shrink-0 flex-col items-start gap-3">
                  {entry.review ? <BuyerReview rating={entry.review.rating} comment={entry.review.comment} /> : null}
                  <RateBuyer entry={entry} onRated={onChanged} />
                </div>
              ) : entry.review ? (
                <BuyerReview rating={entry.review.rating} comment={entry.review.comment} />
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
  const money = useMoney();
  const t = useTranslations("seller");
  const format = useFormatter();
  const amount = money.currency(offer.amount);

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
  const money = useMoney();
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const amount = money.currency(chance.runnerUpBid ?? 0);

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

function BuyerReview({ rating, comment }: { rating: number; comment: string | null }) {
  const t = useTranslations("seller");
  return (
    <div className="max-w-[16rem] shrink-0">
      <p className="label-sm text-dim mb-1">{t("buyerReview")}</p>
      <StarRating rating={rating} />
      {comment ? <p className="text-muted-foreground mt-1 line-clamp-2 text-xs">{comment}</p> : null}
    </div>
  );
}

function ShipButton({ entry, onShipped }: { entry: OrderEntry; onShipped: () => void }) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [open, setOpen] = useState(false);
  const [choice, setChoice] = useState<string>("");
  const [otherCarrier, setOtherCarrier] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const ghnOn = useGhnEnabled();
  const carrier = choice === OTHER_CARRIER ? otherCarrier.trim() : choice;

  function ship(event: FormEvent) {
    event.preventDefault();
    startTransition(async () => {
      try {
        await shipOrder(entry.order.id, { carrier, trackingNumber });
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      setOpen(false);
      toast.success(t("shipped"), { description: t("shippedBody") });
      onShipped();
    });
  }

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)} disabled={isPending}>
        <Truck className="size-4" />
        {t("markShipped")}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={ship} noValidate>
            <DialogHeader>
              <DialogTitle>{t("shipTitle")}</DialogTitle>
              <DialogDescription>{t("shipBody")}</DialogDescription>
            </DialogHeader>

            {entry.order.shipping ? (
              <p className="border-line mt-5 border-y py-3 text-sm leading-relaxed">
                <span className="label-sm text-dim block">{t("shipTo")}</span>
                {entry.order.shipping.recipient} · {entry.order.shipping.phone}
                <br />
                {entry.order.shipping.address}
              </p>
            ) : null}

            <div className="mt-5 space-y-4">
              <FormField label={t("shipCarrier")}>
                {(field) => (
                  <Select value={choice} onValueChange={setChoice}>
                    <SelectTrigger id={field.id} aria-describedby={field["aria-describedby"]} className="w-full">
                      <SelectValue placeholder={t("shipCarrierPlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      {CARRIERS.map((name) => (
                        <SelectItem key={name} value={name}>
                          {name}
                        </SelectItem>
                      ))}
                      <SelectItem value={OTHER_CARRIER}>{t("shipCarrierOther")}</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              </FormField>
              {choice === OTHER_CARRIER ? (
                <FormField label={t("shipCarrierOtherName")}>
                  {(field) => (
                    <Input
                      {...field}
                      maxLength={60}
                      value={otherCarrier}
                      onChange={(event) => setOtherCarrier(event.target.value)}
                    />
                  )}
                </FormField>
              ) : null}
              <FormField
                label={t("shipTracking")}
                hint={choice === GHN_CARRIER && ghnOn ? t("shipTrackingGhnHint") : undefined}
              >
                {(field) => (
                  <Input
                    {...field}
                    maxLength={60}
                    value={trackingNumber}
                    onChange={(event) => setTrackingNumber(event.target.value)}
                  />
                )}
              </FormField>
            </div>

            <DialogFooter className="mt-6">
              <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={isPending}>
                {tc("cancel")}
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? <Loader2 className="size-4 animate-spin" /> : <Truck className="size-4" />}
                {t("markShipped")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

/**
 * EN: The buyer's record across NexBid, so the seller knows who they are dealing with: purchases completed, wins left
 *     unpaid, and what sellers rated them.
 * VI: Hồ sơ của người mua trên NexBid, để người bán biết mình đang giao dịch với ai: số đơn đã hoàn tất, số lần thắng bỏ
 *     không trả, và điểm người bán đã cho.
 */
function BuyerRecordLine({ entry }: { entry: OrderEntry }) {
  const t = useTranslations("seller");
  const format = useFormatter();
  const record = entry.buyer?.record;
  if (!record) return null;
  return (
    <p className="text-muted-foreground mt-2 text-xs">
      <span className="label-sm text-dim">{t("buyerRecord")}</span>{" "}
      {t("buyerCompleted", { count: record.completed })}
      {" · "}
      <span className={record.unpaidWins > 0 ? "text-danger-text" : undefined}>
        {t("buyerUnpaid", { count: record.unpaidWins })}
      </span>
      {record.averageRating !== null ? (
        <>
          {" · "}
          {t("buyerRating", {
            rating: format.number(record.averageRating, { minimumFractionDigits: 1 }),
            count: record.ratings,
          })}
        </>
      ) : null}
    </p>
  );
}

/** EN: The seller's own 1–5 rating of the buyer, once per completed order. / VI: Người bán chấm người mua 1–5, một lần mỗi đơn hoàn tất. */
function RateBuyer({ entry, onRated }: { entry: OrderEntry; onRated: () => void }) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const errorMessage = useApiErrorMessage();
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const mine = entry.buyer?.review;
  if (mine) {
    return (
      <div className="max-w-[16rem]">
        <p className="label-sm text-dim mb-1">{t("yourBuyerRating")}</p>
        <StarRating rating={mine.rating} />
        {mine.comment ? <p className="text-muted-foreground mt-1 line-clamp-2 text-xs">{mine.comment}</p> : null}
      </div>
    );
  }

  function send() {
    if (rating === 0) {
      setError(t("rateBuyerStarsRequired"));
      return;
    }
    startTransition(async () => {
      try {
        await rateBuyer(entry.order.id, rating, comment);
      } catch (failure) {
        setError(errorMessage(failure));
        return;
      }
      setOpen(false);
      toast.success(t("rateBuyerSaved"));
      onRated();
    });
  }

  const labelId = `rate-buyer-${entry.order.id}`;
  return (
    <>
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        {t("rateBuyer")}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("rateBuyerTitle")}</DialogTitle>
            <DialogDescription>{t("rateBuyerBody", { name: entry.auction?.product.name ?? "—" })}</DialogDescription>
          </DialogHeader>
          <p id={labelId} className="label-sm text-dim">
            {t("rateBuyerStars")}
          </p>
          <StarInput
            name={labelId}
            value={rating}
            labelledBy={labelId}
            onChange={(value) => {
              setRating(value);
              setError(null);
            }}
          />
          <FormField label={t("rateBuyerComment")} error={error ?? undefined}>
            {(field) => (
              <Textarea
                {...field}
                value={comment}
                maxLength={500}
                rows={3}
                disabled={isPending}
                onChange={(event) => setComment(event.target.value)}
              />
            )}
          </FormField>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={isPending}>
              {tc("cancel")}
            </Button>
            <Button onClick={send} disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("rateBuyerSend")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
