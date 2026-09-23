"use client";

import { Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";

import { cn } from "@/lib/utils";

interface SearchBarProps {
  defaultValue?: string;
  placeholder?: string;
  className?: string;
  /** Called instead of navigating — used by the listing page's inline search. */
  onSearch?: (value: string) => void;
  autoFocus?: boolean;
}

/**
 * A ruled field rather than a boxed one: an underline the query sits on, which
 * turns signal green on focus. Submitting navigates to the browse page with
 * the query applied, so header, stage and listing behave identically.
 */
export function SearchBar({
  defaultValue = "",
  placeholder,
  className,
  onSearch,
  autoFocus = false,
}: SearchBarProps) {
  const t = useTranslations("common");
  const router = useRouter();
  const inputId = useId();
  const [value, setValue] = useState(defaultValue);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = value.trim();

    if (onSearch) {
      onSearch(trimmed);
      return;
    }

    router.push(
      trimmed ? `/auctions?search=${encodeURIComponent(trimmed)}` : "/auctions",
    );
  }

  return (
    <form
      role="search"
      onSubmit={handleSubmit}
      className={cn("group relative", className)}
    >
      <label htmlFor={inputId} className="sr-only">
        {t("search")}
      </label>
      <Search
        className="text-dim group-focus-within:text-signal-text pointer-events-none absolute top-1/2 left-0 size-4 -translate-y-1/2 transition-colors"
        aria-hidden="true"
      />
      <input
        id={inputId}
        type="search"
        value={value}
        autoFocus={autoFocus}
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder ?? t("searchPlaceholder")}
        className={cn(
          "border-line focus:border-signal-text placeholder:text-dim caret-signal-text h-11 w-full border-0 border-b bg-transparent pr-2 pl-7 text-sm transition-colors outline-none",
          "[&::-webkit-search-cancel-button]:appearance-none",
        )}
      />
    </form>
  );
}
