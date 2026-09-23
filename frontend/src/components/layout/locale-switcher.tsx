"use client";

import { Check, Languages } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useTransition } from "react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { LOCALES, LOCALE_LABELS, type Locale } from "@/i18n/config";
import { setUserLocale } from "@/i18n/locale";
import { cn } from "@/lib/utils";

interface LocaleSwitcherProps {
  /** `compact` shows only the code (header), `full` shows the name (footer). */
  variant?: "compact" | "full";
  className?: string;
}

/**
 * Language switcher. The locale lives in a cookie, so changing it is a server
 * action followed by a refresh — every server-rendered string updates at once.
 */
export function LocaleSwitcher({
  variant = "compact",
  className,
}: LocaleSwitcherProps) {
  const t = useTranslations("common");
  const current = useLocale() as Locale;
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  function handleSelect(locale: Locale) {
    if (locale === current) return;
    startTransition(async () => {
      await setUserLocale(locale);
      router.refresh();
    });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size={variant === "compact" ? "icon" : "sm"}
          disabled={isPending}
          aria-label={t("changeLanguage")}
          className={cn(variant === "compact" && "relative", className)}
        >
          <Languages aria-hidden="true" />
          {variant === "full" ? (
            <span>{LOCALE_LABELS[current].name}</span>
          ) : (
            <span className="sr-only">{LOCALE_LABELS[current].name}</span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-44">
        {LOCALES.map((locale) => (
          <DropdownMenuItem
            key={locale}
            onSelect={() => handleSelect(locale)}
            className="justify-between"
          >
            {LOCALE_LABELS[locale].name}
            {locale === current ? (
              <Check className="size-4" aria-hidden="true" />
            ) : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
