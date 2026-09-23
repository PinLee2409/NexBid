import { cn } from "@/lib/utils";

type LotSize = "xs" | "sm" | "md" | "lg" | "xl" | "hero";

interface LotNumberProps {
  lot: number;
  size?: LotSize;
  /** Hides the small "LOT" caption, leaving just the figure. */
  bare?: boolean;
  className?: string;
}

const SIZES: Record<LotSize, string> = {
  xs: "text-[13px]",
  sm: "text-lg",
  md: "text-3xl",
  lg: "text-6xl",
  xl: "text-[110px]",
  hero: "text-[clamp(5rem,16vw,15rem)]",
};

/** Lot numbers are always three digits — `24` reads as `024`. */
export function formatLot(lot: number): string {
  return String(lot).padStart(3, "0");
}

/**
 * The catalogue index. At large sizes it stops being a label and becomes part
 * of the page composition, the way a lot number does in a printed catalogue.
 */
export function LotNumber({
  lot,
  size = "sm",
  bare = false,
  className,
}: LotNumberProps) {
  return (
    <span className={cn("inline-flex items-baseline gap-2", className)}>
      {!bare ? (
        <span className="label-sm text-dim translate-y-[-0.1em]">LOT</span>
      ) : null}
      <span className={cn("display figure", SIZES[size])}>{formatLot(lot)}</span>
    </span>
  );
}
