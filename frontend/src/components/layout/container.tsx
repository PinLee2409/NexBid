import type { ElementType, ReactNode } from "react";

import { cn } from "@/lib/utils";

interface ContainerProps {
  as?: ElementType;
  className?: string;
  children: ReactNode;
  /** Anchor target for in-page navigation (e.g. `#categories`). */
  id?: string;
}

/**
 * The single horizontal rhythm, shared with the home page's full-bleed
 * sections so a gutter never shifts when you move between screens.
 */
export function Container({
  as: Component = "div",
  className,
  children,
  id,
}: ContainerProps) {
  return (
    <Component
      id={id}
      className={cn("mx-auto w-full max-w-[1680px] px-5 sm:px-8", className)}
    >
      {children}
    </Component>
  );
}
