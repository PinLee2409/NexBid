"use client";

import { Loader2, Plus, TicketPercent } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition, type FormEvent } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { FormField } from "@/components/common/form-field";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { createPromoCode, listPromoCodes, setPromoActive, type PromoCode } from "@/services/promo-service";

/**
 * EN: Shipping vouchers. Each takes a fixed amount off a buyer's shipping, once per buyer; NexBid pays the difference,
 *     so the seller still gets the full shipping. New sellers' first sales carry no platform fee on their own.
 * VI: Voucher phí ship. Mỗi mã giảm một số tiền cố định vào phí ship của người mua, mỗi người một lần; NexBid trả phần
 *     chênh, nên người bán vẫn nhận đủ phí ship. Vài giao dịch đầu của người bán mới tự động không tính phí sàn.
 */
export default function AdminPromosPage() {
  const t = useTranslations("admin");
  const { state, refresh } = useAsyncData("admin-promos", listPromoCodes);
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title={t("promosTitle")}
        description={t("promosSubtitle")}
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus className="size-3.5" />
            {t("promosNew")}
          </Button>
        }
      />
      <div className="mt-10">
        {state.status === "loading" ? (
          <Skeleton className="h-40 w-full" />
        ) : state.status === "error" ? (
          <ApiErrorState icon={TicketPercent} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={TicketPercent} title={t("promosEmptyTitle")} description={t("promosEmptyBody")} />
        ) : (
          <ul data-testid="promo-list">
            {state.data.map((promo) => (
              <PromoRow key={promo.code} promo={promo} onChanged={refresh} />
            ))}
          </ul>
        )}
      </div>
      <CreatePromoDialog open={creating} onClose={() => setCreating(false)} onCreated={refresh} />
    </>
  );
}

function PromoRow({ promo, onChanged }: { promo: PromoCode; onChanged: () => void }) {
  const t = useTranslations("admin");
  const money = useMoney();
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();
  const [now] = useState(() => Date.now());
  const ended = promo.expiresAt !== null && new Date(promo.expiresAt).getTime() <= now;

  function toggle() {
    startTransition(async () => {
      try {
        await setPromoActive(promo.code, !promo.active);
        onChanged();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <li className="border-line flex flex-wrap items-center justify-between gap-4 border-b py-4">
      <div>
        <p className="font-mono text-sm font-medium tracking-wider">{promo.code}</p>
        <p className="text-dim mt-1 text-xs">
          {t("promoLine", {
            amount: money.currency(promo.amount),
            used: promo.used,
            max: promo.maxUses,
          })}
          {promo.expiresAt
            ? ` · ${t("promoUntil", { date: format.dateTime(new Date(promo.expiresAt), { dateStyle: "medium" }) })}`
            : ""}
          {promo.note ? ` · ${promo.note}` : ""}
        </p>
      </div>
      <div className="flex items-center gap-3">
        <span
          className={
            promo.active && !ended && promo.used < promo.maxUses ? "text-success text-xs font-medium" : "text-dim text-xs"
          }
        >
          {!promo.active
            ? t("promoOff")
            : ended
              ? t("promoEnded")
              : promo.used >= promo.maxUses
                ? t("promoUsedUp")
                : t("promoLive")}
        </span>
        <Button size="sm" variant="outline" onClick={toggle} disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {promo.active ? t("promoTurnOff") : t("promoTurnOn")}
        </Button>
      </div>
    </li>
  );
}

function CreatePromoDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const t = useTranslations("admin");
  const errorMessage = useApiErrorMessage();
  const [code, setCode] = useState("");
  const [amount, setAmount] = useState("20000");
  const [maxUses, setMaxUses] = useState("100");
  const [until, setUntil] = useState("");
  const [note, setNote] = useState("");
  const [isPending, startTransition] = useTransition();

  function submit(event: FormEvent) {
    event.preventDefault();
    startTransition(async () => {
      try {
        await createPromoCode({
          code: code.trim(),
          amount: Number(amount),
          maxUses: Number(maxUses),
          // EN: The end of that day, Vietnam time. / VI: Cuối ngày đó, giờ Việt Nam.
          expiresAt: until ? new Date(`${until}T23:59:59+07:00`).toISOString() : null,
          note,
        });
      } catch (error) {
        toast.error(errorMessage(error));
        return;
      }
      toast.success(t("promoCreated"));
      setCode("");
      setNote("");
      onClose();
      onCreated();
    });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? null : onClose())}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("promosNew")}</DialogTitle>
          <DialogDescription>{t("promosNewBody")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="space-y-4">
          <FormField label={t("promoCodeLabel")} required>
            {(field) => (
              <Input
                {...field}
                value={code}
                maxLength={32}
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                placeholder="FREESHIP-50K"
              />
            )}
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label={t("promoAmountLabel")} required>
              {(field) => (
                <Input {...field} inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} />
              )}
            </FormField>
            <FormField label={t("promoMaxUsesLabel")} required>
              {(field) => (
                <Input {...field} inputMode="numeric" value={maxUses} onChange={(event) => setMaxUses(event.target.value)} />
              )}
            </FormField>
          </div>
          <FormField label={t("promoUntilLabel")}>
            {(field) => <Input {...field} type="date" value={until} onChange={(event) => setUntil(event.target.value)} />}
          </FormField>
          <FormField label={t("promoNoteLabel")}>
            {(field) => <Input {...field} value={note} maxLength={200} onChange={(event) => setNote(event.target.value)} />}
          </FormField>
          <div className="flex justify-end">
            <Button type="submit" disabled={isPending || code.trim().length < 4}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
              {t("promoCreate")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
