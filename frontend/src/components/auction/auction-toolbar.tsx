"use client";

import { X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useTransition } from "react";

import { SearchBar } from "@/components/common/search-bar";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { AUCTION_SORT_OPTIONS } from "@/constants/auction";
import { useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { buildAuctionHref, countActiveFilters } from "@/lib/auction-query";
import { formatCurrency } from "@/lib/format";
import type { AuctionQuery, AuctionSort, Category } from "@/types";

interface AuctionToolbarProps {
  query: AuctionQuery;
  categories: Category[];
  totalItems: number;
}

/**
 * Search, sort and the chips that show what is currently narrowing the list.
 * Every control is a navigation, keeping the URL authoritative.
 */
export function AuctionToolbar({
  query,
  categories,
  totalItems,
}: AuctionToolbarProps) {
  const t = useTranslations("auctions");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const categoryLabels = useCategoryLabels();
  const router = useRouter();
  const [, startTransition] = useTransition();

  function navigate(next: AuctionQuery) {
    startTransition(() => {
      router.push(buildAuctionHref({ ...next, page: 1 }));
    });
  }

  const chips: { key: string; label: string; onRemove: () => void }[] = [];

  if (query.search) {
    chips.push({
      key: `search-${query.search}`,
      label: `"${query.search}"`,
      onRemove: () => navigate({ ...query, search: undefined }),
    });
  }

  for (const slug of query.categorySlugs ?? []) {
    const category = categories.find((item) => item.slug === slug);
    chips.push({
      key: `category-${slug}`,
      label: category ? categoryLabels.name(category) : slug,
      onRemove: () =>
        navigate({
          ...query,
          categorySlugs: query.categorySlugs?.filter((item) => item !== slug),
        }),
    });
  }

  for (const status of query.statuses ?? []) {
    chips.push({
      key: `status-${status}`,
      label: labels.auctionStatus(status),
      onRemove: () =>
        navigate({
          ...query,
          statuses: query.statuses?.filter((item) => item !== status),
        }),
    });
  }

  for (const condition of query.conditions ?? []) {
    chips.push({
      key: `condition-${condition}`,
      label: labels.condition(condition),
      onRemove: () =>
        navigate({
          ...query,
          conditions: query.conditions?.filter((item) => item !== condition),
        }),
    });
  }

  if (typeof query.minPrice === "number" || typeof query.maxPrice === "number") {
    const from = query.minPrice ?? 0;
    const to = query.maxPrice;
    chips.push({
      key: "price",
      label: to
        ? `${formatCurrency(from)} – ${formatCurrency(to)}`
        : `${formatCurrency(from)}+`,
      onRemove: () =>
        navigate({ ...query, minPrice: undefined, maxPrice: undefined }),
    });
  }

  if (query.endingSoon) {
    chips.push({
      key: "endingSoon",
      label: t("endingSoonLabel"),
      onRemove: () => navigate({ ...query, endingSoon: false }),
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-x-6 gap-y-3">
        <SearchBar
          className="min-w-0 flex-1 sm:max-w-sm"
          defaultValue={query.search ?? ""}
          onSearch={(value) => navigate({ ...query, search: value || undefined })}
        />

        <div className="flex items-center gap-2">
          <label htmlFor="auction-sort" className="sr-only">
            {t("sortBy")}
          </label>
          <Select
            value={query.sort ?? "ENDING_SOON"}
            onValueChange={(value) =>
              navigate({ ...query, sort: value as AuctionSort })
            }
          >
            <SelectTrigger id="auction-sort" className="w-[200px]">
              <SelectValue placeholder={t("sortBy")} />
            </SelectTrigger>
            <SelectContent align="end">
              {AUCTION_SORT_OPTIONS.map((option) => (
                <SelectItem key={option} value={option}>
                  {labels.sort(option)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="border-line flex flex-wrap items-center gap-x-3 gap-y-2 border-t pt-4">
        <p className="label-sm text-dim mr-2">
          {t("resultCount", { count: totalItems })}
        </p>

        {/* Each narrowing is a removable token, set like a lot reference. */}
        {chips.map((chip) => (
          <button
            key={chip.key}
            type="button"
            onClick={chip.onRemove}
            className="label-sm border-line text-foreground hover:border-foreground inline-flex items-center gap-2 border px-2.5 py-1.5 transition-colors"
          >
            {chip.label}
            <X className="text-dim size-3" aria-hidden="true" />
            <span className="sr-only">{tc("remove")}</span>
          </button>
        ))}

        {countActiveFilters(query) > 0 || query.search ? (
          <Button variant="ghost" size="sm" onClick={() => navigate({ sort: query.sort })}>
            {tc("clearAll")}
          </Button>
        ) : null}
      </div>
    </div>
  );
}
