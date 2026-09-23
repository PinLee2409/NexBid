import Link from "next/link";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface SectionHeadProps {
  title: string;
  description?: string;
  action?: { label: string; href: string };
  /** Small element beside the title, e.g. a live count. */
  badge?: ReactNode;
  className?: string;
}

/**
 * Section openings are a rule, a title and — at most — one line of lead.
 * The rule is what separates sections; nothing is boxed.
 */
export function SectionHead({
  title,
  description,
  action,
  badge,
  className,
}: SectionHeadProps) {
  return (
    <div className={cn("border-line border-t pt-6", className)}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-8 gap-y-4">
        <div className="flex items-baseline gap-4">
          <h2 className="display text-[clamp(2rem,5vw,4rem)]">{title}</h2>
          {badge}
        </div>

        {action ? (
          <Link
            href={action.href}
            className="label text-muted-foreground hover:text-signal-text group inline-flex items-center gap-2 transition-colors"
          >
            {action.label}
            <span className="transition-transform group-hover:translate-x-1">→</span>
          </Link>
        ) : null}
      </div>

      {description ? (
        <p className="text-muted-foreground mt-4 max-w-xl text-[15px] leading-relaxed">
          {description}
        </p>
      ) : null}
    </div>
  );
}
