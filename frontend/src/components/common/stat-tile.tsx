import type { LucideIcon } from "lucide-react";
import Link from "next/link";

import { cn } from "@/lib/utils";

interface StatTileProps {
  icon: LucideIcon;
  label: string;
  value: string;
  /** Marks a figure that is asking for a decision, e.g. lots awaiting review. */
  urgent?: boolean;
  /** Makes the whole tile a link to where the figure can be acted on. */
  href?: string;
  className?: string;
}

/**
 * A figure under a rule. The workspace reads its numbers the same way a lot
 * reads its price: micro-caps label, display figure, nothing boxed.
 */
export function StatTile({
  icon: Icon,
  label,
  value,
  urgent = false,
  href,
  className,
}: StatTileProps) {
  const content = (
    <>
      <span className="label-sm text-dim flex items-center gap-2">
        <Icon className="size-3.5" aria-hidden="true" />
        {label}
      </span>
      <span
        className={cn(
          "display mt-3 block text-[clamp(1.75rem,3vw,2.5rem)]",
          urgent && "text-danger-text",
        )}
      >
        {value}
      </span>
    </>
  );

  const shell = cn("border-line block border-t pt-4", className);

  if (href) {
    return (
      <Link
        href={href}
        className={cn(
          shell,
          "hover:border-signal-text group transition-colors",
        )}
      >
        {content}
      </Link>
    );
  }

  return <div className={shell}>{content}</div>;
}
