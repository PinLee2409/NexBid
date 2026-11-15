"use client";

import { Printer } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState } from "react";

import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { useMoney } from "@/hooks/use-money";
import { getOrder, type OrderEntry } from "@/services/order-service";
import { getEarnings } from "@/services/payout-service";
import { listSoldOrders } from "@/services/seller-service";
import { useSession } from "@/services/session-service";

interface Statement {
  gross: number;
  fee: number;
  shipping: number;
  net: number;
}

type Loaded =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "ready"; entry: OrderEntry; statement: Statement | null };

/**
 * EN: The receipt itself. The buyer's shows goods, shipping, any voucher and what VNPay charged; the seller's shows the
 *     sale less the platform fee plus shipping, as owed. Only paid orders have one.
 * VI: Nội dung biên nhận. Của người mua: tiền hàng, phí ship, voucher nếu có và số VNPay đã thu; của người bán: giá bán
 *     trừ phí sàn cộng phí ship, là số được nhận. Chỉ đơn đã thanh toán mới có.
 */
export function Receipt({ orderId, side }: { orderId: string; side: "buyer" | "seller" }) {
  const t = useTranslations("receipt");
  const money = useMoney();
  const format = useFormatter();
  const { user, hydrated } = useSession();
  const [loaded, setLoaded] = useState<Loaded>({ kind: "loading" });
  const signedIn = Boolean(user);

  useEffect(() => {
    if (!hydrated || !signedIn) return;
    let live = true;
    (async () => {
      if (side === "buyer") {
        return { entry: await getOrder(orderId), statement: null };
      }
      const [sold, earnings] = await Promise.all([listSoldOrders(), getEarnings()]);
      const entry = sold.find((found) => found.order.id === orderId);
      if (!entry) throw new Error("missing");
      const earning = earnings.earnings.find((found) => found.orderId === orderId);
      return {
        entry,
        statement: earning ? { gross: earning.gross, fee: earning.fee, shipping: earning.shipping, net: earning.net } : null,
      };
    })().then(
      (found) => live && setLoaded(found.entry.payment?.status === "SUCCESS" || found.entry.payment?.status === "REFUNDED"
        ? { kind: "ready", ...found }
        : { kind: "missing" }),
      () => live && setLoaded({ kind: "missing" }),
    );
    return () => {
      live = false;
    };
  }, [orderId, side, hydrated, signedIn]);

  if (hydrated && !signedIn) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-16">
        <Link href={`/login?redirect=${encodeURIComponent(`/receipts/${orderId}${side === "seller" ? "?as=seller" : ""}`)}`} className="text-signal-text underline">
          {t("signIn")}
        </Link>
      </main>
    );
  }
  if (loaded.kind === "loading") {
    return <main className="text-dim mx-auto max-w-2xl px-4 py-16 text-sm">{t("loading")}</main>;
  }
  if (loaded.kind === "missing") {
    return <main className="mx-auto max-w-2xl px-4 py-16 text-sm">{t("missing")}</main>;
  }

  const { entry, statement } = loaded;
  const payment = entry.payment!;
  const shipping = payment.shippingFee ?? 0;
  const discount = payment.shippingDiscount ?? 0;
  const paidAt = payment.updatedAt;
  const lot = entry.auction?.product.name ?? "—";

  return (
    <main className="receipt mx-auto max-w-2xl px-4 py-12 print:max-w-none print:p-0" data-testid="receipt">
      <div className="mb-8 flex justify-end gap-2 print:hidden">
        <Button variant="outline" asChild>
          <Link href={side === "seller" ? "/seller/orders" : "/orders"}>{t("back")}</Link>
        </Button>
        <Button onClick={() => window.print()}>
          <Printer className="size-4" />
          {t("print")}
        </Button>
      </div>

      <header className="border-line flex items-start justify-between gap-6 border-b pb-6">
        <Logo size={24} />
        <div className="text-right">
          <h1 className="display text-2xl">{side === "seller" ? t("sellerTitle") : t("title")}</h1>
          <p className="mono-figure text-dim mt-1 text-xs">
            {t("number", { id: entry.order.id.slice(0, 8).toUpperCase() })}
          </p>
        </div>
      </header>

      <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
        <dt className="text-dim">{t("date")}</dt>
        <dd>{format.dateTime(new Date(paidAt), { dateStyle: "long", timeStyle: "short" })}</dd>
        <dt className="text-dim">{t("lot")}</dt>
        <dd>{lot}</dd>
        <dt className="text-dim">{t("seller")}</dt>
        <dd>{entry.auction?.seller.displayName ?? "—"}</dd>
        {entry.order.shipping ? (
          <>
            <dt className="text-dim">{t("deliverTo")}</dt>
            <dd>
              {entry.order.shipping.recipient}
              <br />
              {entry.order.shipping.address}
            </dd>
          </>
        ) : null}
        <dt className="text-dim">{t("reference")}</dt>
        <dd className="mono-figure text-xs">{payment.id}</dd>
      </dl>

      <table className="mt-8 w-full text-sm">
        <tbody className="divide-line border-line divide-y border-y">
          {side === "buyer" ? (
            <>
              <Line label={t("goods")} value={money.currency(payment.amount)} />
              <Line label={t("shipping")} value={money.currency(shipping)} />
              {discount > 0 ? (
                <Line label={t("voucher", { code: payment.promoCode ?? "" })} value={`−${money.currency(discount)}`} />
              ) : null}
              <Line label={t("paid")} value={money.currency(payment.total)} strong />
            </>
          ) : statement ? (
            <>
              <Line label={t("sale")} value={money.currency(statement.gross)} />
              <Line label={t("fee")} value={statement.fee > 0 ? `−${money.currency(statement.fee)}` : money.currency(0)} />
              <Line label={t("shippingToSeller")} value={money.currency(statement.shipping)} />
              <Line label={t("net")} value={money.currency(statement.net)} strong />
            </>
          ) : (
            <>
              <Line label={t("sale")} value={money.currency(payment.amount)} />
              <tr>
                <td colSpan={2} className="text-dim py-3 text-xs">
                  {t("notYetOwed")}
                </td>
              </tr>
            </>
          )}
        </tbody>
      </table>

      <p className="text-dim mt-8 text-xs leading-relaxed">
        {payment.status === "REFUNDED" ? t("refunded") : side === "seller" ? t("sellerNote") : t("buyerNote")}
      </p>
    </main>
  );
}

function Line({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <tr>
      <td className={strong ? "py-3 font-medium" : "text-dim py-3"}>{label}</td>
      <td className={strong ? "mono-figure py-3 text-right text-lg font-medium" : "mono-figure py-3 text-right"}>{value}</td>
    </tr>
  );
}
