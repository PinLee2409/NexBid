"use client";

import { Check, Monitor, Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { useSyncExternalStore } from "react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

/** The store never changes — it only has to read `false` on the server. */
const noopSubscribe = () => () => {};

const OPTIONS = [
  { value: "light", icon: Sun, labelKey: "light" },
  { value: "dark", icon: Moon, labelKey: "dark" },
  { value: "system", icon: Monitor, labelKey: "system" },
] as const;

interface ThemeSwitcherProps {
  /** `compact` shows only the icon (header), `full` shows the name (footer). */
  variant?: "compact" | "full";
  className?: string;
}

/**
 * Appearance switcher. The chosen theme only exists in the browser, so the
 * trigger renders a neutral placeholder until mount — otherwise the server
 * would have to guess an icon and get it wrong half the time.
 */
export function ThemeSwitcher({
  variant = "compact",
  className,
}: ThemeSwitcherProps) {
  const t = useTranslations("theme");
  const { theme, resolvedTheme, setTheme } = useTheme();

  const mounted = useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );

  const current = mounted ? (theme ?? "system") : undefined;
  // The icon reflects what is on screen; the tick reflects what was chosen.
  const Icon = !mounted ? Moon : resolvedTheme === "light" ? Sun : Moon;

  const selected = OPTIONS.find((option) => option.value === current);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size={variant === "compact" ? "icon" : "sm"}
          aria-label={t("toggle")}
          className={cn(variant === "compact" && "relative", className)}
        >
          <Icon aria-hidden="true" />
          {variant === "full" ? (
            <span>{selected ? t(selected.labelKey) : t("label")}</span>
          ) : (
            <span className="sr-only">{t("label")}</span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-44">
        {OPTIONS.map((option) => (
          <DropdownMenuItem
            key={option.value}
            onSelect={() => setTheme(option.value)}
            className="justify-between"
          >
            <span className="inline-flex items-center gap-2">
              <option.icon className="size-4" aria-hidden="true" />
              {t(option.labelKey)}
            </span>
            {mounted && option.value === current ? (
              <Check className="size-4" aria-hidden="true" />
            ) : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
