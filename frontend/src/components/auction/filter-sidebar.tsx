"use client";

import { SlidersHorizontal, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { BROWSABLE_STATUSES, PRODUCT_CONDITIONS } from "@/constants/auction";
import { useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { buildAuctionHref, countActiveFilters } from "@/lib/auction-query";
import { cn } from "@/lib/utils";
import type {
  AuctionQuery,
  AuctionStatus,
  Category,
  ProductCondition,
} from "@/types";

interface FilterSidebarProps {
  query: AuctionQuery;
  categories: Category[];
  className?: string;
}

/**
 * Browse filters. Every change rewrites the URL rather than local state, so
 * the server re-renders the results and the view stays shareable.
 */
export function FilterSidebar({ query, categories, className }: FilterSidebarProps) {
  return (
    <>
      <aside
        className={cn("hidden lg:block", className)}
        aria-label="Filters"
      >
        <FilterControls query={query} categories={categories} />
      </aside>

      <MobileFilterSheet query={query} categories={categories} />
    </>
  );
}

function MobileFilterSheet({
  query,
  categories,
}: {
  query: AuctionQuery;
  categories: Category[];
}) {
  const t = useTranslations("auctions");
  const [open, setOpen] = useState(false);
  const activeCount = countActiveFilters(query);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="lg:hidden">
          <SlidersHorizontal className="size-4" />
          {t("showFilters")}
          {activeCount > 0 ? (
            <span className="mono-figure text-signal-text ml-1 text-[11px]">
              {String(activeCount).padStart(2, "0")}
            </span>
          ) : null}
        </Button>
      </SheetTrigger>
      <SheetContent
        side="left"
        className="bg-background border-line w-[min(22rem,90vw)] overflow-y-auto rounded-none border-r"
      >
        <SheetHeader className="border-line border-b px-5 py-4">
          <SheetTitle className="display text-left text-2xl">
            {t("showFilters")}
          </SheetTitle>
        </SheetHeader>
        <div className="px-5 pb-8">
          <FilterControls
            query={query}
            categories={categories}
            onNavigate={() => setOpen(false)}
          />
        </div>
      </SheetContent>
    </Sheet>
  );
}

function FilterControls({
  query,
  categories,
  onNavigate,
}: {
  query: AuctionQuery;
  categories: Category[];
  onNavigate?: () => void;
}) {
  const t = useTranslations("auctions");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const categoryLabels = useCategoryLabels();
  const router = useRouter();
  const [, startTransition] = useTransition();

  // Price is a free-text pair, so it commits on blur/submit rather than
  // firing a navigation on every keystroke.
  const [minPrice, setMinPrice] = useState(query.minPrice?.toString() ?? "");
  const [maxPrice, setMaxPrice] = useState(query.maxPrice?.toString() ?? "");

  function navigate(next: AuctionQuery) {
    startTransition(() => {
      // Any filter change resets to the first page.
      router.push(buildAuctionHref({ ...next, page: 1 }));
      onNavigate?.();
    });
  }

  function toggleInList<T extends string>(
    list: T[] | undefined,
    value: T,
  ): T[] {
    const current = list ?? [];
    return current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value];
  }

  function commitPrice() {
    const min = minPrice === "" ? undefined : Number(minPrice);
    const max = maxPrice === "" ? undefined : Number(maxPrice);

    navigate({
      ...query,
      minPrice: Number.isFinite(min) ? min : undefined,
      maxPrice: Number.isFinite(max) ? max : undefined,
    });
  }

  return (
    <div className="space-y-8">
      <FilterGroup title={t("filterStatus")}>
        {BROWSABLE_STATUSES.map((status: AuctionStatus) => (
          <FilterCheckbox
            key={status}
            id={`status-${status}`}
            label={labels.auctionStatus(status)}
            checked={query.statuses?.includes(status) ?? false}
            onChange={() =>
              navigate({
                ...query,
                statuses: toggleInList(query.statuses, status),
              })
            }
          />
        ))}
        <FilterCheckbox
          id="ending-soon"
          label={t("endingSoonLabel")}
          checked={query.endingSoon ?? false}
          onChange={() => navigate({ ...query, endingSoon: !query.endingSoon })}
        />
      </FilterGroup>


      <FilterGroup title={t("filterCategory")}>
        {categories.map((category) => (
          <FilterCheckbox
            key={category.id}
            id={`category-${category.slug}`}
            label={categoryLabels.name(category)}
            checked={query.categorySlugs?.includes(category.slug) ?? false}
            onChange={() =>
              navigate({
                ...query,
                categorySlugs: toggleInList(query.categorySlugs, category.slug),
              })
            }
          />
        ))}
      </FilterGroup>


      <FilterGroup title={t("filterPrice")}>
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <Label htmlFor="min-price" className="sr-only">
              {t("minPrice")}
            </Label>
            <Input
              id="min-price"
              inputMode="numeric"
              placeholder={t("minPrice")}
              value={minPrice}
              onChange={(event) => setMinPrice(event.target.value.replace(/\D/g, ""))}
              onBlur={commitPrice}
              onKeyDown={(event) => {
                if (event.key === "Enter") commitPrice();
              }}
              className="h-10"
            />
          </div>
          <span className="text-muted-foreground text-sm" aria-hidden="true">
            –
          </span>
          <div className="flex-1">
            <Label htmlFor="max-price" className="sr-only">
              {t("maxPrice")}
            </Label>
            <Input
              id="max-price"
              inputMode="numeric"
              placeholder={t("maxPrice")}
              value={maxPrice}
              onChange={(event) => setMaxPrice(event.target.value.replace(/\D/g, ""))}
              onBlur={commitPrice}
              onKeyDown={(event) => {
                if (event.key === "Enter") commitPrice();
              }}
              className="h-10"
            />
          </div>
        </div>
      </FilterGroup>


      <FilterGroup title={t("filterCondition")}>
        {PRODUCT_CONDITIONS.map((condition: ProductCondition) => (
          <FilterCheckbox
            key={condition}
            id={`condition-${condition}`}
            label={labels.condition(condition)}
            checked={query.conditions?.includes(condition) ?? false}
            onChange={() =>
              navigate({
                ...query,
                conditions: toggleInList(query.conditions, condition),
              })
            }
          />
        ))}
      </FilterGroup>

      {countActiveFilters(query) > 0 ? (
        <Button
          variant="ghost"
          size="sm"
          className="w-full"
          onClick={() =>
            navigate({ search: query.search, sort: query.sort, page: 1 })
          }
        >
          <X className="size-4" />
          {tc("clearFilters")}
        </Button>
      ) : null}
    </div>
  );
}

function FilterGroup({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    // The heading is a <p> rather than a <legend>: a floated legend pulls the
    // first control out of flow, and the group is named for the fieldset.
    <fieldset className="border-line border-t pt-4" aria-label={title}>
      <p className="label-sm text-dim mb-3.5">{title}</p>
      <div className="space-y-3">{children}</div>
    </fieldset>
  );
}

function FilterCheckbox({
  id,
  label,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <Checkbox id={id} checked={checked} onCheckedChange={onChange} />
      <Label
        htmlFor={id}
        className="text-muted-foreground hover:text-foreground cursor-pointer text-sm font-normal transition-colors"
      >
        {label}
      </Label>
    </div>
  );
}
