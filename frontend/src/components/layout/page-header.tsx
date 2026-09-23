import Link from "next/link";
import { Fragment, type ReactNode } from "react";

import { cn } from "@/lib/utils";

export interface Crumb {
  label: string;
  href?: string;
}

interface PageHeaderProps {
  title: string;
  description?: string;
  /** Right-aligned actions such as "Create auction". */
  actions?: ReactNode;
  breadcrumbs?: Crumb[];
  className?: string;
  /** Extra content rendered below the title block (tabs, filters, stats). */
  children?: ReactNode;
}

/**
 * Every workspace screen opens the way a catalogue section does: a trail set
 * in micro-caps, the name in the display face, and a rule underneath. No box,
 * no shadow — the rule is the structure.
 */
export function PageHeader({
  title,
  description,
  actions,
  breadcrumbs,
  className,
  children,
}: PageHeaderProps) {
  return (
    <div className={cn("border-line border-b pb-6", className)}>
      {breadcrumbs?.length ? (
        // A plain trail rather than shadcn's breadcrumb: at this size the
        // separators and links are one line of micro-caps, not a component.
        <nav aria-label="Breadcrumb" className="mb-4">
          <ol className="label-sm text-dim flex flex-wrap items-center gap-2">
            {breadcrumbs.map((crumb, index) => {
              const isLast = index === breadcrumbs.length - 1;
              return (
                <Fragment key={`${crumb.label}-${index}`}>
                  <li>
                    {isLast || !crumb.href ? (
                      <span aria-current={isLast ? "page" : undefined}>
                        {crumb.label}
                      </span>
                    ) : (
                      <Link
                        href={crumb.href}
                        className="hover:text-signal-text transition-colors"
                      >
                        {crumb.label}
                      </Link>
                    )}
                  </li>
                  {!isLast ? (
                    <li aria-hidden="true" className="text-dim/60">
                      /
                    </li>
                  ) : null}
                </Fragment>
              );
            })}
          </ol>
        </nav>
      ) : null}

      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-5">
        <div className="min-w-0">
          <h1 className="display text-[clamp(1.875rem,4vw,3rem)]">{title}</h1>
          {description ? (
            <p className="text-muted-foreground mt-3 max-w-2xl text-[15px] leading-relaxed">
              {description}
            </p>
          ) : null}
        </div>
        {actions ? (
          <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>
        ) : null}
      </div>

      {children ? <div className="mt-6">{children}</div> : null}
    </div>
  );
}
