"use client";

import { Check, Coins } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { FX_CHOICES, setFxChoice, useFx, type FxChoice } from "@/lib/fx-store";
import { cn } from "@/lib/utils";

/**
 * EN: Picks the reference currency shown beside prices, or none. Kept in this browser; payment stays in VND.
 * VI: Chọn loại tiền tham khảo hiện bên cạnh giá, hoặc không hiện. Lưu trong trình duyệt này; thanh toán vẫn bằng VND.
 */
export function CurrencySwitcher({ className }: { className?: string }) {
  const t = useTranslations("fx");
  const fx = useFx();

  const label = (choice: FxChoice) => (choice === "off" ? t("off") : t(choice));

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className={cn(className)}>
          <Coins aria-hidden="true" />
          <span className="sr-only">{t("change")}: </span>
          <span>{label(fx.choice)}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-56">
        <DropdownMenuLabel className="text-muted-foreground text-xs font-normal leading-relaxed">
          {t("note")}
        </DropdownMenuLabel>
        {FX_CHOICES.map((choice) => (
          <DropdownMenuItem key={choice} onSelect={() => setFxChoice(choice)} className="justify-between">
            {label(choice)}
            {choice === fx.choice ? <Check className="size-4" aria-hidden="true" /> : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
