"use client";

import { Columns3 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import {
  COMPARE_LIMIT,
  toggleCompare,
  useCompareList,
} from "@/lib/compare-store";
import { cn } from "@/lib/utils";

/**
 * EN: Adds the lot to the comparison tray (up to three lots).
 * VI: Thêm lô vào khay so sánh (tối đa ba lô).
 */
export function CompareButton({
  auctionId,
  className,
}: {
  auctionId: string;
  className?: string;
}) {
  const t = useTranslations("compare");
  const list = useCompareList();
  const picked = list.includes(auctionId);

  return (
    <button
      type="button"
      aria-pressed={picked}
      onClick={() => {
        if (!toggleCompare(auctionId))
          toast.error(t("full", { count: COMPARE_LIMIT }));
      }}
      className={cn(
        "label inline-flex items-center gap-2 transition-colors",
        picked
          ? "text-signal-text"
          : "text-muted-foreground hover:text-foreground",
        className,
      )}
    >
      <Columns3 className="size-4" aria-hidden="true" />
      {picked ? t("added") : t("add")}
    </button>
  );
}
