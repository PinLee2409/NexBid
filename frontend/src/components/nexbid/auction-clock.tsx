"use client";

import { useTranslations } from "next-intl";

import { useCountdown } from "@/hooks/use-countdown";
import { padTwo } from "@/lib/format";
import { cn } from "@/lib/utils";

type ClockVariant = "inline" | "terminal" | "hero";

interface AuctionClockProps {
  endTime: string;
  serverTime?: string;
  variant?: ClockVariant;
  onComplete?: () => void;
  className?: string;
  /** Text shown once the clock reaches zero. */
  endedLabel?: string;
}

/**
 * The clock every screen reads from.
 *
 * Under a minute it changes character rather than blinking: the figures grow,
 * the colour moves to the loss red, and seconds become the dominant unit. The
 * browser only renders the number — the server owns when an auction ends.
 */
export function AuctionClock({
  endTime,
  serverTime,
  variant = "inline",
  onComplete,
  className,
  endedLabel,
}: AuctionClockProps) {
  const t = useTranslations("common");
  const { duration, msRemaining, isComplete } = useCountdown(endTime, {
    serverTime,
    onComplete,
  });

  const critical = msRemaining !== null && msRemaining > 0 && msRemaining <= 60_000;
  const urgent =
    msRemaining !== null && msRemaining > 60_000 && msRemaining <= 600_000;

  if (duration === null) {
    return (
      <span
        className={cn("mono-figure text-dim", className)}
        aria-label={t("loading")}
      >
        --:--
      </span>
    );
  }

  if (isComplete) {
    return (
      <span className={cn("label text-dim", className)}>
        {endedLabel ?? t("ended")}
      </span>
    );
  }

  const { days, hours, minutes, seconds } = duration;

  /**
   * Show the two units that matter right now. Past a day, hours are noise;
   * inside the last hour, seconds are the whole story.
   */
  const segments: string[] =
    days > 0
      ? [`${days}D`, padTwo(hours)]
      : hours > 0
        ? [padTwo(hours), padTwo(minutes)]
        : [padTwo(minutes), padTwo(seconds)];

  const label = `${days > 0 ? `${days} days ` : ""}${hours}h ${minutes}m ${seconds}s remaining`;

  const tone = critical
    ? "text-danger-text"
    : urgent
      ? "text-signal-text"
      : "text-foreground";

  if (variant === "hero") {
    return (
      <span
        suppressHydrationWarning
        aria-label={label}
        className={cn(
          "display figure block tabular-nums transition-all duration-500",
          critical
            ? "text-danger-text text-[clamp(4rem,11vw,9rem)]"
            : "text-[clamp(2.5rem,6vw,5rem)]",
          className,
        )}
      >
        {segments[0]}
        <span className="text-dim mx-[0.12em]">:</span>
        {segments[1]}
      </span>
    );
  }

  if (variant === "terminal") {
    return (
      <span
        suppressHydrationWarning
        aria-label={label}
        className={cn(
          "mono-figure block leading-none font-medium transition-all duration-500",
          tone,
          critical ? "text-[56px]" : "text-[40px]",
          className,
        )}
      >
        {segments[0]}
        <span className="text-dim mx-1.5">:</span>
        {segments[1]}
      </span>
    );
  }

  return (
    <span
      suppressHydrationWarning
      aria-label={label}
      className={cn("mono-figure text-sm", tone, className)}
    >
      {segments[0]}:{segments[1]}
    </span>
  );
}
