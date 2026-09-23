"use client";

import { ArrowLeft, Menu } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

import { Logo } from "@/components/layout/logo";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { ADMIN_NAV, SELLER_NAV, type AccountNavItem } from "@/constants/site";
import { cn } from "@/lib/utils";

interface WorkspaceSidebarProps {
  /** Which workspace this rail belongs to. */
  variant: "seller" | "admin";
  /** Label above the nav, e.g. "Seller workspace". */
  title: string;
}

/**
 * The rail for the seller workspace and the admin console. Selection is shown
 * by a signal bar against the row rather than a filled pill — the same device
 * the image strip on a lot uses, so "you are here" reads the same everywhere.
 */
export function WorkspaceSidebar({ variant, title }: WorkspaceSidebarProps) {
  // Nav data is imported here rather than passed in: Lucide icons are
  // functions and cannot cross the server-to-client boundary.
  const items: AccountNavItem[] = variant === "seller" ? SELLER_NAV : ADMIN_NAV;

  const t = useTranslations("nav");
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  const isActive = (href: string) =>
    href === pathname || (href !== "/admin" && pathname.startsWith(`${href}/`));

  const nav = (
    <nav aria-label={title} className="flex-1 py-6">
      <p className="label-sm text-dim px-5 pb-4">{title}</p>
      <ul>
        {items.map((item) => {
          const active = isActive(item.href);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                onClick={() => setOpen(false)}
                className={cn(
                  "label relative flex items-center gap-3 px-5 py-3.5 transition-colors",
                  active
                    ? "text-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {active ? (
                  <span
                    className="bg-signal-text absolute inset-y-0 left-0 w-0.5"
                    aria-hidden="true"
                  />
                ) : null}
                <item.icon className="size-4 shrink-0" aria-hidden="true" />
                {t(item.labelKey)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );

  const back = (
    <Link
      href="/"
      className="label text-muted-foreground hover:text-foreground border-line flex items-center gap-2.5 border-t px-5 py-4 transition-colors"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      {t("marketplace")}
    </Link>
  );

  return (
    <>
      {/* Desktop rail */}
      <aside className="bg-surface border-line sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r lg:flex">
        <div className="border-line flex h-16 shrink-0 items-center border-b px-5">
          <Logo size={24} />
        </div>
        {nav}
        {back}
      </aside>

      {/* Mobile top bar */}
      <div className="bg-background/92 border-line sticky top-0 z-40 flex h-16 items-center gap-3 border-b px-4 backdrop-blur-xl lg:hidden">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <button
              type="button"
              aria-label={t("openMenu")}
              className="text-muted-foreground hover:text-foreground inline-flex size-9 items-center justify-center transition-colors"
            >
              <Menu className="size-5" />
            </button>
          </SheetTrigger>
          <SheetContent
            side="left"
            className="bg-background border-line w-[min(20rem,86vw)] rounded-none border-r p-0"
          >
            <SheetHeader className="border-line border-b px-5 py-4">
              <SheetTitle className="text-left">
                <Logo href={null} size={24} />
              </SheetTitle>
            </SheetHeader>
            {nav}
            <SheetClose asChild>{back}</SheetClose>
          </SheetContent>
        </Sheet>

        <Logo size={24} />
      </div>
    </>
  );
}
