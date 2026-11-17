"use client";

import { AnimatePresence, m } from "framer-motion";
import { X } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState } from "react";

import { ProductPhoto } from "@/components/common/product-photo";
import { useAsyncData } from "@/hooks/use-async-data";
import { useCategoryLabels } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { listCategories } from "@/services/auction-service";
import { suggest } from "@/services/market-service";

/** EN: Lower case without Vietnamese marks, so "dong ho" finds "Đồng hồ". / VI: Chữ thường bỏ dấu, để "dong ho" tìm ra "Đồng hồ". */
function fold(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase();
}

interface SearchOverlayProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Search takes the whole page rather than dropping a small panel: one enormous
 * prompt, and results that build underneath as the query is typed.
 */
export function SearchOverlay({ open, onOpenChange }: SearchOverlayProps) {
  const money = useMoney();
  const t = useTranslations("search");
  const tc = useTranslations("common");
  const [query, setQuery] = useState("");
  // EN: Asks once the typing pauses, not on every key. / VI: Chỉ hỏi khi ngừng gõ, không phải mỗi phím.
  const [settled, setSettled] = useState("");
  const categoryLabels = useCategoryLabels();
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(query.trim()), 180);
    return () => window.clearTimeout(timer);
  }, [query]);

  // Escape closes; the body must not scroll behind the overlay.
  useEffect(() => {
    if (!open) return;

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onOpenChange(false);
    }

    document.addEventListener("keydown", onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onOpenChange]);

  const trimmed = query.trim();
  // The overlay is mounted on every page; ask the server only once there is a query to show.
  const searching = open && settled.length >= 2;
  const { state } = useAsyncData(`search-${searching ? settled : ""}`, () =>
    searching ? suggest(settled) : Promise.resolve(null),
  );
  const categories = useAsyncData(`search-categories-${open}`, () => (open ? listCategories() : Promise.resolve([])));

  const suggestions = state.status === "success" ? state.data : null;
  const results = suggestions?.lots ?? [];
  const sellers = suggestions?.sellers ?? [];
  const matchingCategories =
    trimmed.length >= 2 && categories.state.status === "success"
      ? categories.state.data.filter((category) => fold(categoryLabels.name(category)).includes(fold(trimmed))).slice(0, 4)
      : [];
  const close = () => onOpenChange(false);

  return (
    <AnimatePresence>
      {open ? (
        <m.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
          className="bg-background/98 fixed inset-0 z-[70] overflow-y-auto backdrop-blur-xl"
          role="dialog"
          aria-modal="true"
          aria-label={tc("search")}
        >
          <div className="mx-auto max-w-[1680px] px-5 py-6 sm:px-8">
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => onOpenChange(false)}
                aria-label={tc("close")}
                className="text-muted-foreground hover:text-foreground inline-flex size-10 items-center justify-center transition-colors"
              >
                <X className="size-6" />
              </button>
            </div>

            <m.div
              initial={{ y: 24, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ duration: 0.34, delay: 0.04, ease: [0.22, 1, 0.36, 1] }}
              className="pt-8 sm:pt-16"
            >
              <label htmlFor="nexbid-search" className="sr-only">
                {tc("search")}
              </label>
              <p className="display text-dim text-[clamp(2rem,7vw,5.5rem)]">
                {t("prompt")}
              </p>

              <input
                id="nexbid-search"
                autoFocus
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t("placeholder")}
                className="display placeholder:text-dim/50 text-foreground caret-signal-text mt-4 w-full border-none bg-transparent text-[clamp(2.5rem,9vw,7rem)] outline-none"
              />
              <div className="bg-signal-text mt-2 h-px w-full" />
            </m.div>

            <div className="mt-10 pb-20">
              {suggestions?.correction ? (
                <p className="label text-muted-foreground mb-6" data-testid="search-correction">
                  {t("didYouMean")}{" "}
                  <button
                    type="button"
                    onClick={() => setQuery(suggestions.correction ?? "")}
                    className="text-signal-text underline underline-offset-4"
                  >
                    {suggestions.correction}
                  </button>
                </p>
              ) : null}
              {matchingCategories.length > 0 || sellers.length > 0 ? (
                <div className="mb-8 flex flex-wrap gap-2" data-testid="search-shortcuts">
                  {matchingCategories.map((category) => (
                    <Link
                      key={category.id}
                      href={`/auctions?category=${category.slug}`}
                      onClick={close}
                      className="label border-line hover:border-signal-text hover:text-signal-text border px-3 py-2 transition-colors"
                    >
                      {t("inCategory", { name: categoryLabels.name(category) })}
                    </Link>
                  ))}
                  {sellers.map((seller) => (
                    <Link
                      key={seller.id}
                      href={`/sellers/${seller.id}`}
                      onClick={close}
                      className="label border-line hover:border-signal-text hover:text-signal-text border px-3 py-2 transition-colors"
                    >
                      {t("seller", { name: seller.name })}
                    </Link>
                  ))}
                </div>
              ) : null}
              {trimmed === "" ? (
                <p className="label text-dim">{t("hint")}</p>
              ) : results.length === 0 && state.status === "success" && settled === trimmed ? (
                <p className="label text-dim">
                  {t("noResults", { query: trimmed })}
                </p>
              ) : (
                <ul>
                  {results.map((auction, index) => (
                    <m.li
                      key={auction.auctionId}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.2, delay: index * 0.03 }}
                      className="border-line border-t last:border-b"
                    >
                      <Link
                        href={`/auctions/${auction.auctionId}`}
                        onClick={close}
                        className="group flex items-center gap-5 py-3 sm:gap-8"
                      >
                        <span className="bg-surface relative size-12 shrink-0 overflow-hidden">
                          <ProductPhoto src={auction.coverImageUrl ?? undefined} fill sizes="48px" fallback="icon" className="object-cover" />
                        </span>
                        <span className="display group-hover:text-signal-text min-w-0 flex-1 truncate text-xl transition-colors sm:text-3xl">
                          {auction.title}
                        </span>
                        <span className="mono-figure text-muted-foreground shrink-0 text-sm">
                          {money.currency(auction.currentPrice)}
                        </span>
                      </Link>
                    </m.li>
                  ))}
                </ul>
              )}
              {trimmed.length >= 2 ? (
                <Link
                  href={`/auctions?search=${encodeURIComponent(trimmed)}`}
                  onClick={close}
                  className="label text-signal-text mt-6 inline-block underline-offset-4 hover:underline"
                >
                  {t("seeAll", { query: trimmed })}
                </Link>
              ) : null}
            </div>
          </div>
        </m.div>
      ) : null}
    </AnimatePresence>
  );
}
