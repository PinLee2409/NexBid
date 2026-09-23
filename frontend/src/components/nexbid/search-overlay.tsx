"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState } from "react";

import { LotNumber } from "@/components/nexbid/lot-number";
import { useAsyncData } from "@/hooks/use-async-data";
import { formatCurrency } from "@/lib/format";
import { listAuctions } from "@/services/auction-service";

interface SearchOverlayProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Search takes the whole page rather than dropping a small panel: one enormous
 * prompt, and results that build underneath as the query is typed.
 */
export function SearchOverlay({ open, onOpenChange }: SearchOverlayProps) {
  const t = useTranslations("search");
  const tc = useTranslations("common");
  const [query, setQuery] = useState("");

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
  const { state } = useAsyncData(`search-${trimmed}`, () =>
    listAuctions({ search: trimmed || undefined, pageSize: 6 }),
  );

  const results = state.status === "success" ? state.data.items : [];

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
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

            <motion.div
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
            </motion.div>

            <div className="mt-10 pb-20">
              {trimmed === "" ? (
                <p className="label text-dim">{t("hint")}</p>
              ) : results.length === 0 && state.status === "success" ? (
                <p className="label text-dim">
                  {t("noResults", { query: trimmed })}
                </p>
              ) : (
                <ul>
                  {results.map((auction, index) => (
                    <motion.li
                      key={auction.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.2, delay: index * 0.03 }}
                      className="border-line border-t last:border-b"
                    >
                      <Link
                        href={`/auctions/${auction.id}`}
                        onClick={() => onOpenChange(false)}
                        className="group flex items-baseline gap-5 py-4 sm:gap-8"
                      >
                        <LotNumber
                          lot={auction.lotNumber}
                          size="xs"
                          bare
                          className="text-dim group-hover:text-signal-text w-10 shrink-0 transition-colors"
                        />
                        <span className="display group-hover:text-signal-text min-w-0 flex-1 truncate text-xl transition-colors sm:text-3xl">
                          {auction.product.name}
                        </span>
                        <span className="mono-figure text-muted-foreground shrink-0 text-sm">
                          {formatCurrency(auction.currentPrice)}
                        </span>
                      </Link>
                    </motion.li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
