"use client";

import { CalendarClock, Gavel, Loader2, Package, Shield } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition, type FormEvent, type ReactNode } from "react";
import { toast } from "sonner";

import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { AUCTION_CONFIG } from "@/constants/auction";
import { CURRENCY, formatCurrency, fromDateAndTimeParts } from "@/lib/format";
import { cn } from "@/lib/utils";
import { createAuction } from "@/services/seller-service";
import type { ProductWithMeta } from "@/services/seller-service";

interface AuctionFormProps {
  products: ProductWithMeta[];
  /** Preselected from `?productId=` when coming from the catalogue. */
  initialProductId?: string;
}

interface FieldErrors {
  productId?: string;
  startingPrice?: string;
  minimumIncrement?: string;
  start?: string;
  end?: string;
}

/** Tomorrow at 20:00 — a sensible default slot rather than an empty form. */
function defaultSchedule() {
  const start = new Date();
  start.setDate(start.getDate() + 1);
  start.setHours(20, 0, 0, 0);

  const end = new Date(start);
  end.setHours(end.getHours() + 2);

  const pad = (n: number) => String(n).padStart(2, "0");
  const dateOf = (value: Date) =>
    `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`;
  const timeOf = (value: Date) => `${pad(value.getHours())}:${pad(value.getMinutes())}`;

  return {
    startDate: dateOf(start),
    startTime: timeOf(start),
    endDate: dateOf(end),
    endTime: timeOf(end),
  };
}

export function AuctionForm({ products, initialProductId }: AuctionFormProps) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const format = useFormatter();
  const router = useRouter();

  const schedule = defaultSchedule();

  const [productId, setProductId] = useState(initialProductId ?? "");
  const [startingPrice, setStartingPrice] = useState("500");
  const [minimumIncrement, setMinimumIncrement] = useState("25");
  const [startDate, setStartDate] = useState(schedule.startDate);
  const [startTime, setStartTime] = useState(schedule.startTime);
  const [endDate, setEndDate] = useState(schedule.endDate);
  const [endTime, setEndTime] = useState(schedule.endTime);
  const [antiSniping, setAntiSniping] = useState(true);
  const [snipeWindow, setSnipeWindow] = useState(
    String(AUCTION_CONFIG.defaultAntiSnipingWindowSeconds),
  );
  const [extension, setExtension] = useState(
    String(AUCTION_CONFIG.defaultExtensionSeconds),
  );

  const [errors, setErrors] = useState<FieldErrors>({});
  const [isPending, startTransition] = useTransition();

  const startIso = fromDateAndTimeParts(startDate, startTime);
  const endIso = fromDateAndTimeParts(endDate, endTime);
  const selectedProduct = products.find((entry) => entry.product.id === productId);

  /** Mirrors the server rules in spec §7.4. */
  function validate(): FieldErrors {
    const next: FieldErrors = {};

    if (!productId) next.productId = t("errors.productRequired");

    if (!(Number(startingPrice) > 0))
      next.startingPrice = t("errors.startingPricePositive");

    if (!(Number(minimumIncrement) > 0))
      next.minimumIncrement = t("errors.incrementPositive");

    if (!startIso) next.start = t("errors.startRequired");
    if (!endIso) next.end = t("errors.endRequired");

    if (startIso && endIso && new Date(endIso) <= new Date(startIso))
      next.end = t("errors.endAfterStart");

    return next;
  }

  function submit(submitForApproval: boolean) {
    const nextErrors = validate();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0 || !startIso || !endIso) return;

    startTransition(async () => {
      await createAuction(
        {
          productId,
          startingPrice: Number(startingPrice),
          minimumIncrement: Number(minimumIncrement),
          startTime: startIso,
          endTime: endIso,
          antiSnipingEnabled: antiSniping,
          antiSnipingWindowSeconds: Number(snipeWindow),
          extensionSeconds: Number(extension),
        },
        submitForApproval,
      );

      toast.success(t("auctionCreated"), {
        description: t("auctionCreatedBody", {
          name: selectedProduct?.product.name ?? "",
        }),
      });
      router.push("/seller/auctions");
    });
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    submit(true);
  }

  const durationLabel =
    startIso && endIso && new Date(endIso) > new Date(startIso)
      ? formatDuration(new Date(endIso).getTime() - new Date(startIso).getTime())
      : null;

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-10"
    >
      <div className="min-w-0 space-y-6">
        <FormSection
          icon={Package}
          title={t("sectionProduct")}
          hint={t("sectionProductHint")}
        >
          <div className="space-y-1.5">
            <Label htmlFor="auction-product" className="label-sm text-dim">
              {t("selectProduct")}
              <span className="text-danger-text" aria-hidden="true">
                *
              </span>
            </Label>

            {products.length === 0 ? (
              <p className="text-muted-foreground bg-surface border-line border px-3.5 py-3 text-sm">
                {t("noProductsAvailable")}
              </p>
            ) : (
              <Select
                value={productId}
                onValueChange={(value) => {
                  setProductId(value);
                  setErrors((current) => ({ ...current, productId: undefined }));
                }}
              >
                <SelectTrigger
                  id="auction-product"
                  className="h-11 w-full"
                  aria-invalid={Boolean(errors.productId)}
                >
                  <SelectValue placeholder={t("selectProduct")} />
                </SelectTrigger>
                <SelectContent>
                  {products.map((entry) => (
                    <SelectItem key={entry.product.id} value={entry.product.id}>
                      {entry.product.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {errors.productId ? (
              <p role="alert" className="text-danger-text text-xs">
                {errors.productId}
              </p>
            ) : null}
          </div>
        </FormSection>

        <FormSection
          icon={Gavel}
          title={t("sectionPricing")}
          hint={t("sectionPricingHint")}
        >
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField
              label={t("startingPriceLabel")}
              error={errors.startingPrice}
              required
            >
              {(field) => (
                <MoneyInput
                  {...field}
                  value={startingPrice}
                  onValueChange={(value) => {
                    setStartingPrice(value);
                    setErrors((c) => ({ ...c, startingPrice: undefined }));
                  }}
                />
              )}
            </FormField>

            <FormField
              label={t("incrementLabel")}
              error={errors.minimumIncrement}
              required
            >
              {(field) => (
                <MoneyInput
                  {...field}
                  value={minimumIncrement}
                  onValueChange={(value) => {
                    setMinimumIncrement(value);
                    setErrors((c) => ({ ...c, minimumIncrement: undefined }));
                  }}
                />
              )}
            </FormField>
          </div>
        </FormSection>

        <FormSection
          icon={CalendarClock}
          title={t("sectionSchedule")}
          hint={t("sectionScheduleHint")}
        >
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField label={t("startDate")} error={errors.start} required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={startDate}
                  onChange={(event) => {
                    setStartDate(event.target.value);
                    setErrors((c) => ({ ...c, start: undefined }));
                  }}
                  className="h-11"
                />
              )}
            </FormField>

            <FormField label={t("startTime")} required>
              {(field) => (
                <Input
                  {...field}
                  type="time"
                  value={startTime}
                  onChange={(event) => setStartTime(event.target.value)}
                  className="h-11"
                />
              )}
            </FormField>

            <FormField label={t("endDate")} error={errors.end} required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={endDate}
                  onChange={(event) => {
                    setEndDate(event.target.value);
                    setErrors((c) => ({ ...c, end: undefined }));
                  }}
                  className="h-11"
                />
              )}
            </FormField>

            <FormField label={t("endTime")} required>
              {(field) => (
                <Input
                  {...field}
                  type="time"
                  value={endTime}
                  onChange={(event) => setEndTime(event.target.value)}
                  className="h-11"
                />
              )}
            </FormField>
          </div>
        </FormSection>

        <FormSection
          icon={Shield}
          title={t("sectionAdvanced")}
          hint={t("sectionAdvancedHint")}
        >
          <div className="flex items-start justify-between gap-6">
            <div>
              <Label htmlFor="anti-sniping" className="label">
                {t("antiSnipingEnabled")}
              </Label>
              <p className="text-muted-foreground mt-1 text-xs leading-relaxed">
                {t("antiSnipingHint")}
              </p>
            </div>
            <Switch
              id="anti-sniping"
              checked={antiSniping}
              onCheckedChange={setAntiSniping}
            />
          </div>

          {antiSniping ? (
            <div className="border-line mt-6 grid gap-6 border-t pt-6 sm:grid-cols-2">
              <FormField
                label={t("antiSnipingWindow")}
                hint={t("antiSnipingWindowHint")}
              >
                {(field) => (
                  <UnitInput
                    {...field}
                    value={snipeWindow}
                    unit={t("seconds")}
                    onValueChange={setSnipeWindow}
                  />
                )}
              </FormField>

              <FormField
                label={t("extensionDuration")}
                hint={t("extensionHint")}
              >
                {(field) => (
                  <UnitInput
                    {...field}
                    value={extension}
                    unit={t("seconds")}
                    onValueChange={setExtension}
                  />
                )}
              </FormField>
            </div>
          ) : null}
        </FormSection>
      </div>

      {/* Summary — sticky on desktop so the seller can see what they are creating. */}
      <aside className="lg:sticky lg:top-10 lg:self-start">
        <div className="border-line border-t pt-5">
          <h2 className="label-sm text-dim">{t("summaryTitle")}</h2>

          {!selectedProduct ? (
            <p className="text-muted-foreground mt-3 text-sm leading-relaxed">
              {t("summaryEmpty")}
            </p>
          ) : (
            <dl className="mt-4 space-y-3 text-sm">
              <SummaryRow label={tc("product")} value={selectedProduct.product.name} />
              <SummaryRow
                label={t("startingPriceLabel")}
                value={formatCurrency(Number(startingPrice) || 0)}
              />
              <SummaryRow
                label={t("incrementLabel")}
                value={formatCurrency(Number(minimumIncrement) || 0)}
              />
              {startIso ? (
                <SummaryRow
                  label={t("summaryOpens")}
                  value={format.dateTime(new Date(startIso), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}
                />
              ) : null}
              {endIso ? (
                <SummaryRow
                  label={t("summaryCloses")}
                  value={format.dateTime(new Date(endIso), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}
                />
              ) : null}
              {durationLabel ? (
                <SummaryRow label={t("summaryDuration")} value={durationLabel} />
              ) : null}
              <SummaryRow
                label={t("summaryAntiSniping")}
                value={
                  antiSniping
                    ? t("summaryOn", {
                        window: snipeWindow,
                        extension: Math.round(Number(extension) / 60),
                      })
                    : t("summaryOff")
                }
              />
            </dl>
          )}

          <div className="border-line mt-5 space-y-2 border-t pt-5">
            <Button type="submit" className="w-full" disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("createAndSubmit")}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="w-full"
              disabled={isPending}
              onClick={() => submit(false)}
            >
              {t("createDraft")}
            </Button>
          </div>
        </div>
      </aside>
    </form>
  );
}

function FormSection({
  icon: Icon,
  title,
  hint,
  children,
}: {
  icon: typeof Package;
  title: string;
  hint: string;
  children: ReactNode;
}) {
  return (
    <section className="border-line border-t pt-6">
      <div className="flex items-start gap-3">
        <span className="text-dim flex size-8 shrink-0 items-center justify-center">
          <Icon className="size-4" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h2 className="display text-xl">{title}</h2>
          <p className="text-muted-foreground mt-0.5 text-sm">{hint}</p>
        </div>
      </div>
      <div className="mt-5">{children}</div>
    </section>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="text-muted-foreground shrink-0">{label}</dt>
      <dd className="min-w-0 text-right font-medium">{value}</dd>
    </div>
  );
}

function MoneyInput({
  value,
  onValueChange,
  ...field
}: {
  value: string;
  onValueChange: (value: string) => void;
} & React.ComponentProps<typeof Input>) {
  return (
    <div className="relative">
      <span
        className="text-dim figure pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-sm"
        aria-hidden="true"
      >
        {CURRENCY.symbol}
      </span>
      <Input
        {...field}
        inputMode="numeric"
        value={value}
        onChange={(event) => onValueChange(event.target.value.replace(/[^\d]/g, ""))}
        className={cn("figure pl-8", field.className)}
      />
    </div>
  );
}

function UnitInput({
  value,
  unit,
  onValueChange,
  ...field
}: {
  value: string;
  unit: string;
  onValueChange: (value: string) => void;
} & React.ComponentProps<typeof Input>) {
  return (
    <div className="relative">
      <Input
        {...field}
        inputMode="numeric"
        value={value}
        onChange={(event) => onValueChange(event.target.value.replace(/[^\d]/g, ""))}
        className={cn("figure pr-16", field.className)}
      />
      <span
        className="text-muted-foreground pointer-events-none absolute top-1/2 right-3.5 -translate-y-1/2 text-xs"
        aria-hidden="true"
      >
        {unit}
      </span>
    </div>
  );
}

function formatDuration(ms: number): string {
  const totalMinutes = Math.round(ms / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;

  return [
    days > 0 ? `${days}d` : null,
    hours > 0 ? `${hours}h` : null,
    minutes > 0 ? `${minutes}m` : null,
  ]
    .filter(Boolean)
    .join(" ");
}
