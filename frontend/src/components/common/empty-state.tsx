import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface EmptyStateAction {
  label: string;
  href: string;
}

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: EmptyStateAction;
  secondaryAction?: EmptyStateAction;
  /** Custom controls when the action is not a navigation. */
  children?: ReactNode;
  className?: string;
  tone?: "default" | "error";
}

/**
 * Nothing here yet, said the way the rest of the system speaks: a rule, a
 * display line, one instruction. An empty catalogue is still a catalogue.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  secondaryAction,
  children,
  className,
  tone = "default",
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "border-line flex flex-col items-start border-y px-1 py-16",
        className,
      )}
    >
      <Icon
        className={cn(
          "size-6",
          tone === "error" ? "text-danger-text" : "text-dim",
        )}
        aria-hidden="true"
      />

      <h3 className="display mt-5 text-[clamp(1.5rem,3vw,2.25rem)]">{title}</h3>
      <p className="text-muted-foreground mt-3 max-w-md text-[15px] leading-relaxed">
        {description}
      </p>

      {action || secondaryAction || children ? (
        <div className="mt-8 flex flex-wrap items-center gap-2.5">
          {action ? (
            <Button asChild>
              <Link href={action.href}>{action.label}</Link>
            </Button>
          ) : null}
          {secondaryAction ? (
            <Button asChild variant="outline">
              <Link href={secondaryAction.href}>{secondaryAction.label}</Link>
            </Button>
          ) : null}
          {children}
        </div>
      ) : null}
    </div>
  );
}
