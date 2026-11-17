"use client";

import { Lightbulb } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

import { useMoney } from "@/hooks/use-money";
import { getPriceHint, type PriceHint as Hint } from "@/services/seller-service";

/**
 * EN: What lots like this one sold for lately, and a starting price to use. Shows nothing until a product is chosen,
 *     and nothing when there were too few sales to go on.
 * VI: Các lô tương tự gần đây bán được bao nhiêu, và một giá khởi điểm gợi ý. Không hiện gì khi chưa chọn sản phẩm, hoặc
 *     khi có quá ít giao dịch để dựa vào.
 */
export function PriceHint({ productId, onUse }: { productId: string; onUse: (price: number) => void }) {
  const t = useTranslations("seller");
  const money = useMoney();
  const [hint, setHint] = useState<{ productId: string; hint: Hint } | null>(null);

  useEffect(() => {
    if (!productId) return;
    let live = true;
    getPriceHint(productId).then(
      (found) => live && setHint({ productId, hint: found }),
      () => live && setHint(null),
    );
    return () => {
      live = false;
    };
  }, [productId]);

  const shown = hint && hint.productId === productId ? hint.hint : null;
  if (!shown || shown.suggestedStart === null || shown.median === null) return null;

  return (
    <div className="bg-surface flex flex-wrap items-start gap-3 px-4 py-3 text-sm sm:col-span-2" data-testid="price-hint">
      <Lightbulb className="text-signal-text mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p>
          {t(shown.basis === "SIMILAR" ? "priceHintSimilar" : "priceHintCategory", {
            count: shown.sales,
            median: money.currency(shown.median),
            low: money.currency(shown.low ?? shown.median),
            high: money.currency(shown.high ?? shown.median),
          })}
        </p>
        <p className="text-dim mt-1 text-xs">{t("priceHintSuggest", { price: money.currency(shown.suggestedStart) })}</p>
      </div>
      <button
        type="button"
        className="text-signal-text text-sm underline-offset-4 hover:underline"
        onClick={() => onUse(shown.suggestedStart!)}
      >
        {t("priceHintUse")}
      </button>
    </div>
  );
}
