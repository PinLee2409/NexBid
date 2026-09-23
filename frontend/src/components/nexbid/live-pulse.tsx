import { cn } from "@/lib/utils";

interface LivePulseProps {
  /** `critical` beats roughly twice as fast — used inside the final minute. */
  tone?: "accent" | "danger";
  critical?: boolean;
  className?: string;
}

/**
 * The heartbeat of the platform. A single dot, pulsing — present wherever
 * something is genuinely happening right now, and nowhere else.
 */
export function LivePulse({
  tone = "accent",
  critical = false,
  className,
}: LivePulseProps) {
  const color = tone === "danger" ? "bg-danger" : "bg-signal-text";

  return (
    <span
      className={cn("relative inline-flex size-[7px] shrink-0", className)}
      aria-hidden="true"
    >
      <span
        className={cn(
          "absolute inline-flex size-full rounded-full",
          color,
          critical ? "live-dot-critical" : "live-dot",
        )}
      />
      <span className={cn("relative inline-flex size-[7px] rounded-full", color)} />
    </span>
  );
}

interface LiveTagProps {
  label: string;
  tone?: "accent" | "danger";
  critical?: boolean;
  className?: string;
}

/** `● LIVE` — the pulse plus its label, set as a wide micro-caps tag. */
export function LiveTag({
  label,
  tone = "accent",
  critical = false,
  className,
}: LiveTagProps) {
  return (
    <span
      className={cn(
        "label-sm inline-flex items-center gap-2",
        tone === "danger" ? "text-danger-text" : "text-signal-text",
        className,
      )}
    >
      <LivePulse tone={tone} critical={critical} />
      {label}
    </span>
  );
}
