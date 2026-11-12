"use client";

import { Star } from "lucide-react";
import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

const STARS = [1, 2, 3, 4, 5] as const;

/**
 * EN: Stars as a picture with one accessible name ("4 out of 5 stars"); a fraction rounds to the nearest star.
 * VI: Các ngôi sao như một hình với một tên đọc được ("4 trên 5 sao"); số lẻ được làm tròn tới sao gần nhất.
 */
export function StarRating({ rating, className }: { rating: number; className?: string }) {
  const tc = useTranslations("common");
  const filled = Math.round(rating);

  return (
    <span role="img" aria-label={tc("starsOutOf", { rating })} className={cn("inline-flex gap-0.5", className)}>
      {STARS.map((star) => (
        <Star
          key={star}
          aria-hidden="true"
          className={cn("size-3.5", star <= filled ? "text-signal-text fill-current" : "text-dim")}
        />
      ))}
    </span>
  );
}

/**
 * EN: Picking 1–5 stars. Native radio buttons underneath, so arrow keys and screen readers work as for any choice.
 * VI: Chọn 1–5 sao. Bên dưới là radio gốc, nên phím mũi tên và trình đọc màn hình hoạt động như mọi lựa chọn khác.
 */
export function StarInput({
  name,
  value,
  onChange,
  labelledBy,
}: {
  name: string;
  value: number;
  onChange: (value: number) => void;
  labelledBy: string;
}) {
  const tc = useTranslations("common");

  return (
    <div role="radiogroup" aria-labelledby={labelledBy} className="flex gap-1">
      {STARS.map((star) => (
        <label key={star} className="cursor-pointer">
          <input
            type="radio"
            name={name}
            value={star}
            checked={value === star}
            onChange={() => onChange(star)}
            className="peer sr-only"
            aria-label={tc("starsOutOf", { rating: star })}
          />
          <Star
            aria-hidden="true"
            className={cn(
              "size-7 transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2",
              star <= value ? "text-signal-text fill-current" : "text-dim hover:text-signal-text",
            )}
          />
        </label>
      ))}
    </div>
  );
}
