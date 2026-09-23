"use client";

import { Heart, Menu, Search, User } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, useSyncExternalStore } from "react";

import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { Logo } from "@/components/layout/logo";
import { ThemeSwitcher } from "@/components/layout/theme-switcher";
import { LivePulse } from "@/components/nexbid/live-pulse";
import { SearchOverlay } from "@/components/nexbid/search-overlay";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { ADMIN_NAV, BUYER_NAV, SELLER_NAV } from "@/constants/site";
import { cn } from "@/lib/utils";
import { useUnreadNotificationCount } from "@/services/notification-service";
import { hasRole, signOut, useSession } from "@/services/session-service";
import { useWatchedAuctionIds } from "@/services/watchlist-service";

interface SiteHeaderProps {
  /** Live auction count, shown beside the LIVE entry. */
  liveCount?: number;
}

function subscribeToScroll(onChange: () => void) {
  window.addEventListener("scroll", onChange, { passive: true });
  return () => window.removeEventListener("scroll", onChange);
}

const PRIMARY_NAV = [
  { key: "live", href: "/auctions?status=ACTIVE", live: true },
  { key: "discover", href: "/auctions", live: false },
  { key: "sell", href: "/seller/products/create", live: false },
] as const;

/**
 * A broadcast header: identity, three destinations, and the tools. It stays
 * transparent over the opening stage and only draws its rule once the page
 * has moved, so the featured lot owns the first screen.
 */
export function SiteHeader({ liveCount = 0 }: SiteHeaderProps) {
  const t = useTranslations("nav");
  const tc = useTranslations("common");
  const pathname = usePathname();

  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [openedAt, setOpenedAt] = useState(pathname);

  if (openedAt !== pathname) {
    setOpenedAt(pathname);
    setMobileOpen(false);
    setSearchOpen(false);
  }

  const scrolled = useSyncExternalStore(
    subscribeToScroll,
    () => window.scrollY > 24,
    () => false,
  );

  const { user } = useSession();
  const unread = useUnreadNotificationCount();
  const watched = useWatchedAuctionIds().size;

  return (
    <>
      <header
        className={cn(
          "sticky top-0 z-50 transition-colors duration-300",
          scrolled
            ? "border-line bg-background/92 border-b backdrop-blur-xl"
            : "border-b border-transparent",
          // Un-scrolled, the home header floats on the opening stage, which is
          // dark in both themes — so it borrows the stage's ink until it lifts
          // off and takes its own background.
          !scrolled && pathname === "/" && "on-media",
        )}
      >
        <a
          href="#main"
          className="bg-signal text-signal-ink label-sm sr-only focus:not-sr-only focus:absolute focus:top-3 focus:left-4 focus:z-50 focus:px-4 focus:py-2"
        >
          {t("skipToContent")}
        </a>

        <div className="mx-auto flex h-16 max-w-[1680px] items-center gap-8 px-5 sm:px-8">
          <Logo size={26} />

          <nav aria-label="Main" className="hidden items-center gap-7 lg:flex">
            {PRIMARY_NAV.map((item) => {
              const active = pathname.startsWith(item.href.split("?")[0]);
              return (
                <Link
                  key={item.key}
                  href={item.href}
                  className={cn(
                    "label inline-flex items-center gap-2 transition-colors",
                    active
                      ? "text-foreground"
                      : "text-muted-foreground hover:text-foreground",
                  )}
                >
                  {item.live ? <LivePulse /> : null}
                  {t(item.key)}
                  {item.live && liveCount > 0 ? (
                    <span className="mono-figure text-signal-text text-[11px]">
                      {String(liveCount).padStart(2, "0")}
                    </span>
                  ) : null}
                </Link>
              );
            })}
          </nav>

          <div className="ml-auto flex items-center gap-1">
            <HeaderAction label={tc("search")} onClick={() => setSearchOpen(true)}>
              <Search className="size-[18px]" />
            </HeaderAction>

            <HeaderAction
              label={t("watchlist")}
              href="/watchlist"
              count={watched}
              className="hidden sm:inline-flex"
            >
              <Heart className="size-[18px]" />
            </HeaderAction>

            <HeaderAction
              label={t("notifications")}
              href="/notifications"
              count={unread}
              tone="danger"
            >
              <Bell />
            </HeaderAction>

            <HeaderAction
              label={user ? t("accountMenu") : tc("signIn")}
              href={user ? "/profile" : "/login"}
              className="hidden sm:inline-flex"
            >
              <User className="size-[18px]" />
            </HeaderAction>

            <ThemeSwitcher />
            <LocaleSwitcher className="hidden sm:inline-flex" />

            <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
              <SheetTrigger asChild>
                <button
                  type="button"
                  aria-label={t("openMenu")}
                  className="text-muted-foreground hover:text-foreground inline-flex size-9 items-center justify-center transition-colors lg:hidden"
                >
                  <Menu className="size-5" />
                </button>
              </SheetTrigger>
              <SheetContent
                side="right"
                className="bg-background border-line w-[min(24rem,92vw)] rounded-none border-l p-0"
              >
                <MobileMenu
                  liveCount={liveCount}
                  signedIn={Boolean(user)}
                  isSeller={hasRole(user, "SELLER")}
                  isAdmin={hasRole(user, "ADMIN")}
                />
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </header>

      <SearchOverlay open={searchOpen} onOpenChange={setSearchOpen} />
    </>
  );
}

/** Bell drawn inline so the icon set stays to a minimum. */
function Bell() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="size-[18px]" aria-hidden="true">
      <path
        d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M13.73 21a2 2 0 0 1-3.46 0"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function HeaderAction({
  label,
  href,
  onClick,
  count = 0,
  tone = "accent",
  className,
  children,
}: {
  label: string;
  href?: string;
  onClick?: () => void;
  count?: number;
  tone?: "accent" | "danger";
  className?: string;
  children: React.ReactNode;
}) {
  const inner = (
    <>
      {children}
      {count > 0 ? (
        <span
          className={cn(
            "mono-figure absolute top-1.5 right-1 text-[9px] leading-none",
            tone === "danger" ? "text-danger-text" : "text-signal-text",
          )}
          aria-hidden="true"
        >
          {count > 9 ? "9+" : count}
        </span>
      ) : null}
    </>
  );

  const classes = cn(
    "text-muted-foreground hover:text-foreground relative inline-flex size-9 items-center justify-center transition-colors",
    className,
  );

  if (href) {
    return (
      <Link
        href={href}
        aria-label={count > 0 ? `${label} (${count})` : label}
        className={classes}
      >
        {inner}
      </Link>
    );
  }

  return (
    <button type="button" onClick={onClick} aria-label={label} className={classes}>
      {inner}
    </button>
  );
}

function MobileMenu({
  liveCount,
  signedIn,
  isSeller,
  isAdmin,
}: {
  liveCount: number;
  signedIn: boolean;
  isSeller: boolean;
  isAdmin: boolean;
}) {
  const t = useTranslations("nav");
  const tc = useTranslations("common");
  const tt = useTranslations("theme");

  return (
    <div className="flex h-full flex-col">
      <SheetHeader className="border-line border-b px-5 py-4">
        <SheetTitle className="text-left">
          <Logo href={null} size={24} />
        </SheetTitle>
      </SheetHeader>

      <nav aria-label="Mobile" className="flex-1 overflow-y-auto">
        <ul className="border-line border-b">
          {PRIMARY_NAV.map((item) => (
            <li key={item.key} className="border-line border-b last:border-b-0">
              <SheetClose asChild>
                <Link
                  href={item.href}
                  className="display hover:text-signal-text flex items-center justify-between px-5 py-5 text-3xl transition-colors"
                >
                  <span className="inline-flex items-center gap-3">
                    {item.live ? <LivePulse /> : null}
                    {t(item.key)}
                  </span>
                  {item.live && liveCount > 0 ? (
                    <span className="mono-figure text-signal-text text-sm">
                      {String(liveCount).padStart(2, "0")}
                    </span>
                  ) : null}
                </Link>
              </SheetClose>
            </li>
          ))}
        </ul>

        <MobileGroup title={t("yourAccount")} items={BUYER_NAV} />
        {isSeller ? <MobileGroup title={t("sellerArea")} items={SELLER_NAV} /> : null}
        {isAdmin ? <MobileGroup title={t("adminArea")} items={ADMIN_NAV} /> : null}

        <div className="border-line mt-2 border-t px-5 py-4">
          <p className="label-sm text-dim pb-2">{tc("language")}</p>
          <LocaleSwitcher variant="full" className="-ml-2" />
        </div>

        <div className="border-line border-b px-5 py-4">
          <p className="label-sm text-dim pb-2">{tt("label")}</p>
          <ThemeSwitcher variant="full" className="-ml-2" />
        </div>
      </nav>

      <div className="border-line border-t p-5">
        {signedIn ? (
          <button
            type="button"
            onClick={() => signOut()}
            className="label border-line hover:border-foreground w-full border py-3 transition-colors"
          >
            {tc("signOut")}
          </button>
        ) : (
          <SheetClose asChild>
            <Link
              href="/login"
              className="label bg-signal text-signal-ink block w-full py-3 text-center"
            >
              {tc("signIn")}
            </Link>
          </SheetClose>
        )}
      </div>
    </div>
  );
}

function MobileGroup({
  title,
  items,
}: {
  title: string;
  items: typeof BUYER_NAV;
}) {
  const t = useTranslations("nav");

  return (
    <div className="border-line border-b px-5 py-4">
      <p className="label-sm text-dim pb-2.5">{title}</p>
      <ul className="grid grid-cols-2 gap-x-4 gap-y-2.5">
        {items.map((item) => (
          <li key={item.href}>
            <SheetClose asChild>
              <Link
                href={item.href}
                className="text-muted-foreground hover:text-foreground flex items-center gap-2 text-sm transition-colors"
              >
                <item.icon className="size-3.5 shrink-0" aria-hidden="true" />
                {t(item.labelKey)}
              </Link>
            </SheetClose>
          </li>
        ))}
      </ul>
    </div>
  );
}
