"use client";

import { animate, m, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState } from "react";

import { useMoney } from "@/hooks/use-money";
import { LOCALE_INTL } from "@/i18n/config";
import { CURRENCY } from "@/lib/format";
import { cn } from "@/lib/utils";

interface AuctionPriceTickerProps {
  amount: number;
  className?: string;
  /** Flashes the surface behind the figure when the price moves. */
  flashOnChange?: boolean;
}

const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

/** EN: How long the figure takes to count up to a new price. / VI: Thời gian con số đếm lên tới giá mới. */
const COUNT_SECONDS = 0.9;

/**
 * EN: The figure shown while the price moves: it counts from the old price to the new one, so a jump from
 *     2.000.000 to 3.000.000 runs through every value in between and all the digits spin, not just the one
 *     that changed. The first paint shows the price as it is, and readers who ask for reduced motion get the
 *     new price at once.
 * VI: Con số hiển thị khi giá thay đổi: nó đếm từ giá cũ lên giá mới, nên khi nhảy từ 2.000.000 lên
 *     3.000.000 thì chạy qua mọi giá trị ở giữa và tất cả chữ số cùng quay, không chỉ chữ số thay đổi. Lần vẽ
 *     đầu hiện đúng giá, và người đọc chọn giảm chuyển động thì thấy giá mới ngay.
 */
function useCountUp(target: number): number {
  const reduceMotion = useReducedMotion();
  const [shown, setShown] = useState(target);
  const from = useRef(target);

  useEffect(() => {
    const start = from.current;
    from.current = target;
    if (start === target || reduceMotion) return;
    const controls = animate(start, target, {
      duration: COUNT_SECONDS,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (value) => setShown(Math.round(value)),
    });
    return () => controls.stop();
  }, [target, reduceMotion]);

  return reduceMotion ? target : shown;
}

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

      <m.span
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
      </m.span>
    </span>
  );
}

/**
 * The price, rendered as a live figure. Every bid counts the figure up to the
 * new price, rolling the digits as it goes; nothing else on the line moves.
 */
export function AuctionPriceTicker({
  amount,
  className,
  flashOnChange = true,
}: AuctionPriceTickerProps) {
  const money = useMoney();
  const price = Math.max(0, Math.round(amount));
  const formatted = money.amount(useCountUp(price));

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
    <span className={cn("figure relative inline-flex items-start leading-none", className)}>
      {/* The rolling digits are decorative; assistive tech reads this instead. */}
      <span className="sr-only">{money.currency(price)}</span>
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
        {money.symbolAfter ? null : <CurrencySymbol className="mr-[0.06em]" />}
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
        {/* EN: Vietnamese writes the symbol after the figure. / VI: Tiếng Việt viết ký hiệu sau con số. */}
        {money.symbolAfter ? <CurrencySymbol className="ml-[0.12em]" /> : null}
      </span>
    </span>
  );
}

function CurrencySymbol({ className }: { className: string }) {
  return (
    <span className={cn("inline-block opacity-70", className)} style={{ lineHeight: "1em", verticalAlign: "top" }}>
      {CURRENCY.symbol}
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

/** `+ ₫500,000 / +4.17%` — the movement line under a live price. */
export function PriceDelta({ delta, percent, className }: PriceDeltaProps) {
  const money = useMoney();
  if (delta <= 0) return null;

  return (
    <span
      className={cn(
        "mono-figure text-signal-text inline-flex items-center gap-3 text-xs",
        className,
      )}
    >
      <span>+{money.currency(delta)}</span>
      <span className="text-dim">/</span>
      <span>
        +
        {new Intl.NumberFormat(LOCALE_INTL[money.locale], {
          style: "percent",
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(percent)}
      </span>
    </span>
  );
}
