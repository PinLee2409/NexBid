"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface PaginationProps {
  page: number;
  totalPages: number;
  /** Route the links point at, e.g. `/auctions`. */
  basePath: string;
  /**
   * Query params to preserve, as a plain object so this stays serialisable
   * across the server/client boundary. `page` is set by the component.
   */
  params?: Record<string, string>;
  className?: string;
}

/**
 * Page numbers with ellipses: always shows the first, last and the pages
 * around the current one, so the control stays one line on mobile.
 */
function pageList(page: number, totalPages: number): (number | "gap")[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const pages: (number | "gap")[] = [1];
  const start = Math.max(2, page - 1);
  const end = Math.min(totalPages - 1, page + 1);

  if (start > 2) pages.push("gap");
  for (let index = start; index <= end; index += 1) pages.push(index);
  if (end < totalPages - 1) pages.push("gap");

  pages.push(totalPages);
  return pages;
}

export function Pagination({
  page,
  totalPages,
  basePath,
  params = {},
  className,
}: PaginationProps) {
  const t = useTranslations("common");
  const ta = useTranslations("auctions");

  if (totalPages <= 1) return null;

  function hrefFor(target: number): string {
    const search = new URLSearchParams(params);
    if (target > 1) {
      search.set("page", String(target));
    } else {
      search.delete("page");
    }
    const queryString = search.toString();
    return queryString ? `${basePath}?${queryString}` : basePath;
  }

  const pages = pageList(page, totalPages);

  return (
    <nav
      aria-label={ta("pagination", { page, total: totalPages })}
      className={cn(
        "border-line flex items-center justify-center gap-1 border-t pt-6",
        className,
      )}
    >
      {page > 1 ? (
        <Button asChild variant="outline" size="icon" aria-label={t("previous")}>
          <Link href={hrefFor(page - 1)} rel="prev">
            <ChevronLeft className="size-4" />
          </Link>
        </Button>
      ) : (
        <Button variant="outline" size="icon" disabled aria-label={t("previous")}>
          <ChevronLeft className="size-4" />
        </Button>
      )}

      {pages.map((entry, index) =>
        entry === "gap" ? (
          <span
            key={`gap-${index}`}
            className="text-dim px-1.5 text-sm"
            aria-hidden="true"
          >
            …
          </span>
        ) : (
          <Button
            key={entry}
            asChild
            variant={entry === page ? "secondary" : "ghost"}
            size="icon"
            aria-current={entry === page ? "page" : undefined}
          >
            <Link href={hrefFor(entry)} className="mono-figure text-xs">
              {String(entry).padStart(2, "0")}
            </Link>
          </Button>
        ),
      )}

      {page < totalPages ? (
        <Button asChild variant="outline" size="icon" aria-label={t("next")}>
          <Link href={hrefFor(page + 1)} rel="next">
            <ChevronRight className="size-4" />
          </Link>
        </Button>
      ) : (
        <Button variant="outline" size="icon" disabled aria-label={t("next")}>
          <ChevronRight className="size-4" />
        </Button>
      )}
    </nav>
  );
}
