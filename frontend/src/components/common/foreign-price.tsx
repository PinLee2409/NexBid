"use client";

import { useTranslations } from "next-intl";

import { useMoney } from "@/hooks/use-money";
import { LOCALE_INTL } from "@/i18n/config";
import { convert, useFx } from "@/lib/fx-store";
import { cn } from "@/lib/utils";

interface ForeignPriceProps {
  /** EN: The amount in đồng. / VI: Số tiền bằng đồng. */
  amount: number;
  className?: string;
}

/**
 * EN: "≈ $1,234" under a price, in the reader's chosen reference currency. Renders nothing when they turned it off,
 *     before the rates arrive, and on the server — so the page never waits on it and hydration never disagrees.
 * VI: "≈ $1,234" dưới một mức giá, theo loại tiền tham khảo người đọc chọn. Không hiện gì khi họ đã tắt, trước khi có tỷ
 *     giá, và trên server — nên trang không bao giờ phải chờ nó và hydration không bao giờ lệch.
 */
export function ForeignPrice({ amount, className }: ForeignPriceProps) {
  const fx = useFx();
  const { locale } = useMoney();
  const t = useTranslations("fx");
  const converted = convert(fx, amount);
  if (!converted) return null;

  const text = new Intl.NumberFormat(LOCALE_INTL[locale], {
    style: "currency",
    currency: converted.currency,
    maximumFractionDigits: converted.value >= 100 ? 0 : 2,
  }).format(converted.value);

  return (
    <p className={cn("text-muted-foreground text-xs tabular-nums", className)} data-testid="fx-hint">
      ≈ {text}
      <span className="sr-only"> {t("reference")}</span>
    </p>
  );
}
