"use client";

import { TicketPercent } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { createCoupon, listCoupons, setCouponActive } from "@/services/market-service";

/**
 * EN: The seller's own discount codes, off the goods on their lots. The seller bears the discount; it comes off what
 *     they are paid for that sale.
 * VI: Mã giảm giá riêng của người bán, trừ vào tiền hàng trên lô của họ. Người bán chịu khoản giảm; nó bị trừ vào số
 *     tiền họ nhận cho giao dịch đó.
 */
export default function SellerCouponsPage() {
  const t = useTranslations("coupons");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const { state, refresh } = useAsyncData("seller-coupons", listCoupons);
  const [isPending, startTransition] = useTransition();
  const [code, setCode] = useState("");
  const [kind, setKind] = useState<"percent" | "amount">("percent");
  const [value, setValue] = useState("10");
  const [maxUses, setMaxUses] = useState("20");
  const [minPrice, setMinPrice] = useState("");
  const [until, setUntil] = useState("");
  const [followersOnly, setFollowersOnly] = useState(false);

  function create(event: React.FormEvent) {
    event.preventDefault();
    const number = Number(value.replace(/\D/g, ""));
    startTransition(async () => {
      try {
        await createCoupon({
          code,
          percentOff: kind === "percent" ? number : null,
          amountOff: kind === "amount" ? number : null,
          minPrice: minPrice ? Number(minPrice.replace(/\D/g, "")) : null,
          followersOnly,
          maxUses: Number(maxUses),
          expiresAt: until ? new Date(`${until}T23:59:00`).toISOString() : null,
        });
        toast.success(t("created", { code: code.toUpperCase() }));
        setCode("");
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  function toggle(target: string, active: boolean) {
    startTransition(async () => {
      try {
        await setCouponActive(target, active);
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />

      <form onSubmit={create} className="border-line mt-8 grid gap-4 border p-5 sm:grid-cols-2 lg:grid-cols-3" data-testid="coupon-form">
        <FormField label={t("code")}>
          {(field) => (
            <Input
              {...field}
              value={code}
              maxLength={32}
              placeholder="SUMMER-10"
              onChange={(event) => setCode(event.target.value.toUpperCase())}
              className="h-10"
            />
          )}
        </FormField>
        <div className="space-y-2">
          <span className="label-sm text-dim">{t("kind")}</span>
          <div className="flex gap-2" role="group" aria-label={t("kind")}>
            {(["percent", "amount"] as const).map((each) => (
              <Button
                key={each}
                type="button"
                size="sm"
                variant={kind === each ? "default" : "outline"}
                aria-pressed={kind === each}
                onClick={() => {
                  setKind(each);
                  setValue(each === "percent" ? "10" : "100000");
                }}
              >
                {t(each)}
              </Button>
            ))}
          </div>
        </div>
        <FormField label={kind === "percent" ? t("percentValue") : t("amountValue")}>
          {(field) => (
            <Input {...field} inputMode="numeric" value={value} onChange={(event) => setValue(event.target.value)} className="h-10" />
          )}
        </FormField>
        <FormField label={t("maxUses")}>
          {(field) => (
            <Input {...field} type="number" min={1} value={maxUses} onChange={(event) => setMaxUses(event.target.value)} className="h-10" />
          )}
        </FormField>
        <FormField label={t("minPrice")}>
          {(field) => (
            <Input {...field} inputMode="numeric" value={minPrice} onChange={(event) => setMinPrice(event.target.value)} className="h-10" />
          )}
        </FormField>
        <FormField label={t("until")}>
          {(field) => <Input {...field} type="date" value={until} onChange={(event) => setUntil(event.target.value)} className="h-10" />}
        </FormField>
        <label className="flex items-center gap-3 text-sm sm:col-span-2">
          <Switch checked={followersOnly} onCheckedChange={setFollowersOnly} />
          {t("followersOnly")}
        </label>
        <div className="flex items-end justify-end">
          <Button type="submit" disabled={isPending || code.length < 4}>
            {t("create")}
          </Button>
        </div>
      </form>

      <div className="mt-8">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={TicketPercent} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={TicketPercent} title={t("emptyTitle")} description={t("emptyBody")} />
        ) : (
          <ul data-testid="coupons">
            {state.data.map((coupon) => (
              <li key={coupon.id} className="border-line flex flex-wrap items-center justify-between gap-3 border-b py-4">
                <div className="min-w-0">
                  <p className="mono-figure font-medium">{coupon.code}</p>
                  <p className="text-muted-foreground text-sm">
                    {coupon.percentOff !== null
                      ? t("percentOff", { percent: coupon.percentOff })
                      : t("amountOff", { amount: money.currency(coupon.amountOff ?? 0) })}
                    {coupon.minPrice ? ` · ${t("from", { amount: money.currency(coupon.minPrice) })}` : ""}
                    {coupon.followersOnly ? ` · ${t("followersOnlyShort")}` : ""}
                  </p>
                  <p className="text-dim text-xs">
                    {t("uses", { used: coupon.used, max: coupon.maxUses })}
                    {coupon.expiresAt
                      ? ` · ${t("ends", { date: format.dateTime(new Date(coupon.expiresAt), { dateStyle: "medium" }) })}`
                      : ""}
                  </p>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <Switch
                    checked={coupon.active}
                    disabled={isPending}
                    onCheckedChange={(on) => toggle(coupon.code, on)}
                    aria-label={t("activeFor", { code: coupon.code })}
                  />
                  {coupon.active ? t("active") : t("paused")}
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
