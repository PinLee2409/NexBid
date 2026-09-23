import type { LucideIcon } from "lucide-react";
import {
  Bell,
  Gavel,
  Heart,
  LayoutDashboard,
  ListChecks,
  Package,
  Receipt,
  Trophy,
  UserRound,
  Wallet,
} from "lucide-react";

export const SITE = {
  name: "NexBid",
  /** Repository link shown in the footer and the how-it-works page. */
  repositoryUrl: "https://github.com/PinLee2409/NexBid",
} as const;

/**
 * Navigation is defined by translation key, never by literal copy, so every
 * label follows the reader's locale.
 */
export interface NavItem {
  /** Key inside the `nav` (or `footer`) message namespace. */
  labelKey: string;
  href: string;
}

export const MAIN_NAV: NavItem[] = [
  { labelKey: "auctions", href: "/auctions" },
  { labelKey: "categories", href: "/auctions#categories" },
  { labelKey: "howItWorks", href: "/how-it-works" },
];

export interface AccountNavItem extends NavItem {
  icon: LucideIcon;
}

/** Buyer account area (spec §36). */
export const BUYER_NAV: AccountNavItem[] = [
  { labelKey: "profile", href: "/profile", icon: UserRound },
  { labelKey: "myBids", href: "/my-bids", icon: Gavel },
  { labelKey: "myWins", href: "/my-wins", icon: Trophy },
  { labelKey: "watchlist", href: "/watchlist", icon: Heart },
  { labelKey: "notifications", href: "/notifications", icon: Bell },
  { labelKey: "payments", href: "/payments", icon: Wallet },
  { labelKey: "orders", href: "/orders", icon: Receipt },
];

/** Seller workspace (spec §36). */
export const SELLER_NAV: AccountNavItem[] = [
  { labelKey: "dashboard", href: "/seller/dashboard", icon: LayoutDashboard },
  { labelKey: "products", href: "/seller/products", icon: Package },
  { labelKey: "auctions", href: "/seller/auctions", icon: Gavel },
];

/** Admin console (spec §36). */
export const ADMIN_NAV: AccountNavItem[] = [
  { labelKey: "overview", href: "/admin", icon: LayoutDashboard },
  { labelKey: "auctions", href: "/admin/auctions", icon: Gavel },
  { labelKey: "users", href: "/admin/users", icon: UserRound },
  { labelKey: "auditLogs", href: "/admin/audit-logs", icon: ListChecks },
];

export interface FooterColumn {
  /** Key inside the `footer` message namespace. */
  titleKey: string;
  links: NavItem[];
}

export const FOOTER_COLUMNS: FooterColumn[] = [
  {
    titleKey: "marketplace",
    links: [
      { labelKey: "liveAuctions", href: "/auctions?status=ACTIVE" },
      { labelKey: "upcoming", href: "/auctions?status=SCHEDULED" },
      { labelKey: "endingSoon", href: "/auctions?sort=ENDING_SOON" },
      { labelKey: "categories", href: "/auctions#categories" },
    ],
  },
  {
    titleKey: "help",
    links: [
      { labelKey: "howItWorks", href: "/how-it-works" },
      { labelKey: "biddingRules", href: "/how-it-works#rules" },
      { labelKey: "payments", href: "/payments" },
      { labelKey: "support", href: "/how-it-works#support" },
    ],
  },
  {
    titleKey: "seller",
    links: [
      { labelKey: "sellAnItem", href: "/seller/products/create" },
      { labelKey: "sellerDashboard", href: "/seller/dashboard" },
      { labelKey: "createAuction", href: "/seller/auctions/create" },
      { labelKey: "sellerGuide", href: "/how-it-works#selling" },
    ],
  },
  {
    titleKey: "legal",
    links: [
      { labelKey: "terms", href: "/legal/terms" },
      { labelKey: "privacy", href: "/legal/privacy" },
      { labelKey: "buyerProtection", href: "/legal/buyer-protection" },
      { labelKey: "cookies", href: "/legal/cookies" },
    ],
  },
];

export interface SocialLink {
  label: string;
  href: string;
}

/** Brand names are not translated. */
export const SOCIAL_LINKS: SocialLink[] = [
  { label: "X", href: "https://x.com" },
  { label: "Instagram", href: "https://instagram.com" },
  { label: "LinkedIn", href: "https://linkedin.com" },
  { label: "GitHub", href: SITE.repositoryUrl },
];
