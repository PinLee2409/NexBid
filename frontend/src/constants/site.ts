import type { LucideIcon } from "lucide-react";

import type { UserRole } from "@/types";
import {
  BarChart3,
  Banknote,
  Bell,
  BellRing,
  Flag,
  Mail,
  MessagesSquare,
  Gavel,
  Heart,
  Landmark,
  FileText,
  HandCoins,
  Settings,
  ShieldCheck,
  UsersRound,
  TicketPercent,
  LayoutDashboard,
  ListChecks,
  Package,
  Receipt,
  ShieldAlert,
  ShieldQuestion,
  Store,
  Tags,
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
interface NavItem {
  /** Key inside the `nav` (or `footer`) message namespace. */
  labelKey: string;
  href: string;
}

export interface AccountNavItem extends NavItem {
  icon: LucideIcon;
  /** EN: Admin console: the staff roles that see this item (ADMIN always does). / VI: Trang quản trị: các vai trò nhân viên thấy mục này (ADMIN luôn thấy). */
  roles?: UserRole[];
}

/** EN: Roles that open part of the admin console. / VI: Các vai trò mở một phần trang quản trị. */
export const STAFF_ROLES: UserRole[] = ["ADMIN", "MODERATOR", "FINANCE", "SUPPORT"];

/** Buyer account area (spec §36). */
export const BUYER_NAV: AccountNavItem[] = [
  { labelKey: "profile", href: "/profile", icon: UserRound },
  { labelKey: "myBids", href: "/my-bids", icon: Gavel },
  { labelKey: "myWins", href: "/my-wins", icon: Trophy },
  { labelKey: "watchlist", href: "/watchlist", icon: Heart },
  { labelKey: "alerts", href: "/alerts", icon: BellRing },
  { labelKey: "notifications", href: "/notifications", icon: Bell },
  { labelKey: "payments", href: "/payments", icon: Wallet },
  { labelKey: "offers", href: "/offers", icon: HandCoins },
  { labelKey: "orders", href: "/orders", icon: Receipt },
  { labelKey: "messages", href: "/messages", icon: MessagesSquare },
];

/** Seller workspace (spec §36). */
export const SELLER_NAV: AccountNavItem[] = [
  { labelKey: "dashboard", href: "/seller/dashboard", icon: LayoutDashboard },
  { labelKey: "products", href: "/seller/products", icon: Package },
  { labelKey: "auctions", href: "/seller/auctions", icon: Gavel },
  { labelKey: "offers", href: "/seller/offers", icon: HandCoins },
  { labelKey: "stats", href: "/seller/stats", icon: BarChart3 },
  { labelKey: "orders", href: "/seller/orders", icon: Receipt },
  { labelKey: "earnings", href: "/seller/earnings", icon: Wallet },
  { labelKey: "statements", href: "/seller/statements", icon: FileText },
  { labelKey: "messages", href: "/messages", icon: MessagesSquare },
  { labelKey: "shopSettings", href: "/seller/settings", icon: Settings },
];

/** Admin console (spec §36). */
export const ADMIN_NAV: AccountNavItem[] = [
  { labelKey: "overview", href: "/admin", icon: LayoutDashboard, roles: ["MODERATOR", "FINANCE", "SUPPORT"] },
  { labelKey: "auctions", href: "/admin/auctions", icon: Gavel, roles: ["MODERATOR"] },
  { labelKey: "categories", href: "/admin/categories", icon: Tags, roles: ["MODERATOR"] },
  { labelKey: "users", href: "/admin/users", icon: UserRound, roles: ["SUPPORT"] },
  { labelKey: "sellerRequests", href: "/admin/sellers", icon: Store, roles: ["MODERATOR"] },
  { labelKey: "orders", href: "/admin/orders", icon: Receipt, roles: ["SUPPORT", "FINANCE"] },
  { labelKey: "disputes", href: "/admin/disputes", icon: ShieldAlert, roles: ["SUPPORT", "FINANCE"] },
  { labelKey: "reports", href: "/admin/reports", icon: Flag, roles: ["MODERATOR"] },
  { labelKey: "trust", href: "/admin/trust", icon: ShieldQuestion, roles: ["MODERATOR"] },
  { labelKey: "payouts", href: "/admin/payouts", icon: Banknote, roles: ["FINANCE"] },
  { labelKey: "deposits", href: "/admin/deposits", icon: ShieldCheck, roles: ["FINANCE"] },
  { labelKey: "finance", href: "/admin/finance", icon: Landmark, roles: ["FINANCE"] },
  { labelKey: "promos", href: "/admin/promos", icon: TicketPercent, roles: ["FINANCE"] },
  { labelKey: "emails", href: "/admin/emails", icon: Mail, roles: ["SUPPORT"] },
  { labelKey: "staff", href: "/admin/staff", icon: UsersRound },
  { labelKey: "auditLogs", href: "/admin/audit-logs", icon: ListChecks },
];

/** EN: Whether a user sees an admin nav item. / VI: Người dùng có thấy một mục menu quản trị không. */
export function canSeeAdminItem(roles: readonly string[], item: AccountNavItem): boolean {
  return roles.includes("ADMIN") || (item.roles ?? []).some((role) => roles.includes(role));
}

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
      { labelKey: "calendar", href: "/calendar" },
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
