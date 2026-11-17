"use client";

import { Columns3, X } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { clearCompare, useCompareList } from "@/lib/compare-store";

/**
 * EN: A small bar at the bottom of the marketplace once two or more lots are picked, linking to the comparison.
 * VI: Một thanh nhỏ ở cuối trang khi đã chọn từ hai lô trở lên, dẫn tới trang so sánh.
 */
export function CompareTray() {
  const t = useTranslations("compare");
  const list = useCompareList();
  const pathname = usePathname();
  if (list.length < 2 || pathname === "/compare") return null;

  return (
    <div
      className="fixed inset-x-0 bottom-4 z-40 flex justify-center px-4 print:hidden"
      data-testid="compare-tray"
    >
      <div className="border-line bg-background/95 flex max-w-full items-center gap-3 border px-4 py-3 shadow-lg backdrop-blur">
        <Columns3
          className="text-signal-text size-4 shrink-0"
          aria-hidden="true"
        />
        <Link
          href={`/compare?ids=${list.join(",")}`}
          className="label hover:text-signal-text truncate"
        >
          {t("tray", { count: list.length })}
        </Link>
        <button
          type="button"
          onClick={clearCompare}
          aria-label={t("clear")}
          className="text-dim hover:text-foreground"
        >
          <X className="size-4" />
        </button>
      </div>
    </div>
  );
}
