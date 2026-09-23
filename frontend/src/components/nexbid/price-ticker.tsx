"use client";

import { motion } from "framer-motion";
import { useState } from "react";

import { CURRENCY } from "@/lib/format";
import { cn } from "@/lib/utils";

interface AuctionPriceTickerProps {
  amount: number;
  className?: string;
  /** Flashes the surface behind the figure when the price moves. */
  flashOnChange?: boolean;
}

const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

/**
 * A single odometer column. The strip of digits slides so the current one
 * lands in the window — the number *moves* rather than being replaced.
 */
function DigitColumn({ digit }: { digit: number }) {
  return (
    <span
      className="relative inline-block overflow-hidden"
      // One line tall, and exactly as wide as a figure in whatever face is in
      // use — `ch` is unreliable across the display and UI fonts, so the width
      // is taken from a hidden glyph instead.
      style={{ height: "1em", lineHeight: "1em", verticalAlign: "top" }}
    >
      <span className="invisible" aria-hidden="true">
        0
      </span>

      <motion.span
        className="absolute top-0 left-0 flex w-full flex-col"
        // No roll on first paint — only on a genuine price change.
        initial={false}
        animate={{ y: `-${digit}em` }}
        transition={{ type: "spring", stiffness: 240, damping: 28, mass: 0.7 }}
      >
        {DIGITS.map((value) => (
          <span
            key={value}
            className="flex items-center justify-center"
            style={{ height: "1em", lineHeight: "1em" }}
          >
            {value}
          </span>
        ))}
      </motion.span>
    </span>
  );
}

/**
 * The price, rendered as a live figure. Every bid rolls the affected digits
 * upward; nothing else on the line moves.
 */
export function AuctionPriceTicker({
  amount,
  className,
  flashOnChange = true,
}: AuctionPriceTickerProps) {
  const formatted = new Intl.NumberFormat(CURRENCY.locale, {
    maximumFractionDigits: 0,
  }).format(Math.max(0, Math.round(amount)));

  /**
   * Each price change bumps a key, which remounts the flash layer and replays
   * its one-shot animation — no timer, and no state left behind afterwards.
   */
  const [previousAmount, setPreviousAmount] = useState(amount);
  const [flashKey, setFlashKey] = useState(0);

  if (previousAmount !== amount) {
    setPreviousAmount(amount);
    setFlashKey((key) => key + 1);
  }

  return (
    <span
      className={cn("figure relative inline-flex items-start leading-none", className)}
      // The rolling digits are decorative; assistive tech reads the value.
      aria-label={`${CURRENCY.symbol}${formatted}`}
    >
      {flashOnChange && flashKey > 0 ? (
        <span
          key={flashKey}
          aria-hidden="true"
          className="flash-signal pointer-events-none absolute -inset-x-3 -inset-y-2"
        />
      ) : null}

      <span
        aria-hidden="true"
        className="inline-flex items-start"
        style={{ lineHeight: "1em" }}
      >
        <span
          className="mr-[0.06em] inline-block opacity-70"
          style={{ lineHeight: "1em", verticalAlign: "top" }}
        >
          {CURRENCY.symbol}
        </span>
        {formatted.split("").map((char, index) =>
          /\d/.test(char) ? (
            <DigitColumn key={`${index}-digit`} digit={Number(char)} />
          ) : (
            <span
              key={`${index}-sep`}
              className="inline-block"
              style={{ lineHeight: "1em", verticalAlign: "top" }}
            >
              {char}
            </span>
          ),
        )}
      </span>
    </span>
  );
}

interface PriceDeltaProps {
  /** Absolute increase over the previous bid. */
  delta: number;
  /** Increase as a share of the previous price, e.g. `0.0417`. */
  percent: number;
  className?: string;
}

/** `+ $750 / +4.17%` — the movement line under a live price. */
export function PriceDelta({ delta, percent, className }: PriceDeltaProps) {
  if (delta <= 0) return null;

  return (
    <span
      className={cn(
        "mono-figure text-signal-text inline-flex items-center gap-3 text-xs",
        className,
      )}
    >
      <span>
        +{CURRENCY.symbol}
        {new Intl.NumberFormat(CURRENCY.locale, {
          maximumFractionDigits: 0,
        }).format(delta)}
      </span>
      <span className="text-dim">/</span>
      <span>+{(percent * 100).toFixed(2)}%</span>
    </span>
  );
}
