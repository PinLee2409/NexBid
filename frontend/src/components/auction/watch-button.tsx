"use client";

import { Heart } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { toggleWatch, useIsWatched } from "@/services/watchlist-service";

interface WatchButtonProps {
  auctionId: string;
  productName: string;
  /** `icon` sits on product imagery, `full` is a labelled secondary action. */
  variant?: "icon" | "full";
  className?: string;
}

export function WatchButton({
  auctionId,
  productName,
  variant = "icon",
  className,
}: WatchButtonProps) {
  const t = useTranslations("common");
  const watched = useIsWatched(auctionId);
  const [isPending, startTransition] = useTransition();

  function handleToggle(event: React.MouseEvent) {
    // Watch buttons often sit inside a linked card.
    event.preventDefault();
    event.stopPropagation();

    startTransition(async () => {
      const nowWatching = await toggleWatch(auctionId);
      toast.success(
        nowWatching ? t("watchlistAdded") : t("watchlistRemoved"),
        { description: productName },
      );
    });
  }

  const label = watched
    ? t("removeFromWatchlist", { name: productName })
    : t("addNameToWatchlist", { name: productName });

  if (variant === "full") {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={handleToggle}
        disabled={isPending}
        aria-pressed={watched}
        className={cn("w-full", className)}
      >
        <Heart className={cn("size-4", watched && "fill-current text-danger-text")} />
        {watched ? t("inWatchlist") : t("addToWatchlist")}
      </Button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleToggle}
      disabled={isPending}
      aria-label={label}
      aria-pressed={watched}
      className={cn(
        "on-media text-foreground hover:text-signal-text inline-flex size-9 items-center justify-center transition-colors disabled:opacity-40",
        className,
      )}
    >
      <Heart
        className={cn(
          "size-4 transition-colors",
          watched ? "fill-current text-danger-text" : "text-foreground",
        )}
      />
    </button>
  );
}
