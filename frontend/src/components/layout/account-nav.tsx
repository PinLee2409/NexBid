"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { BUYER_NAV } from "@/constants/site";
import { cn } from "@/lib/utils";
import { useUnreadNotificationCount } from "@/services/notification-service";
import { useWatchedAuctionIds } from "@/services/watchlist-service";

/**
 * Account navigation: a ruled index on desktop, a scrolling strip on phones.
 * Counts are live, so the number here and the one in the header never disagree.
 */
export function AccountNav() {
  const t = useTranslations("nav");
  const pathname = usePathname();

  const unread = useUnreadNotificationCount();
  const watching = useWatchedAuctionIds().size;

  function countFor(href: string): number {
    if (href === "/notifications") return unread;
    if (href === "/watchlist") return watching;
    return 0;
  }

  return (
    <nav aria-label={t("yourAccount")}>
      {/* Phones: a horizontal strip that scrolls rather than wrapping. */}
      <ul className="scrollbar-none border-line -mx-5 flex gap-6 overflow-x-auto border-b px-5 lg:hidden">
        {BUYER_NAV.map((item) => {
          const active = pathname === item.href;
          return (
            <li key={item.href} className="shrink-0">
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "label relative inline-flex items-center gap-2 py-4 transition-colors",
                  active
                    ? "text-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {t(item.labelKey)}
                <NavCount count={countFor(item.href)} />
                {active ? (
                  <span
                    className="bg-signal-text absolute inset-x-0 bottom-0 h-0.5"
                    aria-hidden="true"
                  />
                ) : null}
              </Link>
            </li>
          );
        })}
      </ul>

      <ul className="hidden lg:block">
        {BUYER_NAV.map((item) => {
          const active = pathname === item.href;
          return (
            <li key={item.href} className="border-line border-b first:border-t">
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "label relative flex items-center gap-3 py-3.5 pl-4 transition-colors",
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
                <span className="flex-1">{t(item.labelKey)}</span>
                <NavCount count={countFor(item.href)} />
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function NavCount({ count }: { count: number }) {
  if (count === 0) return null;

  return (
    <span className="mono-figure text-signal-text text-[11px]">
      {String(count).padStart(2, "0")}
    </span>
  );
}
