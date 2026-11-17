"use client";

import { Palmtree } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";

import { useAsyncData } from "@/hooks/use-async-data";
import { api } from "@/services/api/http";

/**
 * EN: "The seller is away until…" on a lot or seller page, while their vacation lasts.
 * VI: "Người bán vắng tới ngày…" trên trang lô hoặc trang người bán, trong lúc kỳ nghỉ còn hiệu lực.
 */
export function SellerAway({
  sellerId,
  until,
  note,
}: {
  sellerId?: string;
  until?: string | null;
  note?: string | null;
}) {
  const t = useTranslations("deals");
  const format = useFormatter();
  const { state } = useAsyncData(`seller-away-${sellerId ?? "given"}`, () =>
    sellerId
      ? api<{ awayUntil: string | null; awayNote: string | null }>(
          `/api/sellers/${sellerId}/profile`,
        ).catch(() => null)
      : Promise.resolve({ awayUntil: until ?? null, awayNote: note ?? null }),
  );
  const away = state.status === "success" ? state.data : null;
  if (!away?.awayUntil) return null;

  return (
    <div
      className="border-line bg-surface mt-6 flex items-start gap-3 border p-4 text-sm"
      role="note"
      data-testid="seller-away"
    >
      <Palmtree
        className="text-signal-text mt-0.5 size-4 shrink-0"
        aria-hidden="true"
      />
      <div>
        <p>
          {t("awayUntil", {
            date: format.dateTime(new Date(away.awayUntil), {
              dateStyle: "long",
            }),
          })}
        </p>
        {away.awayNote ? (
          <p className="text-muted-foreground mt-1">“{away.awayNote}”</p>
        ) : null}
      </div>
    </div>
  );
}
