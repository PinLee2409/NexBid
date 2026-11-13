"use client";

import {
  AlertTriangle,
  Banknote,
  CalendarClock,
  CheckCircle2,
  CreditCard,
  PackageCheck,
  RotateCcw,
  Scale,
  ShieldAlert,
  Star,
  Store,
  Timer,
  Trophy,
  XCircle,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import type { LucideIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useEnumLabels } from "@/hooks/use-labels";
import { cn } from "@/lib/utils";
import type { AppNotification, NotificationType } from "@/types";

interface NotificationItemProps {
  notification: AppNotification;
  onMarkRead?: (id: string) => void;
}

const ICONS: Record<NotificationType, LucideIcon> = {
  OUTBID: AlertTriangle,
  AUCTION_STARTING: CalendarClock,
  AUCTION_ENDING: Timer,
  AUCTION_WON: Trophy,
  AUCTION_LOST: XCircle,
  AUCTION_EXTENDED: Timer,
  PAYMENT_REQUIRED: CreditCard,
  PAYMENT_SUCCESS: CheckCircle2,
  PAYMENT_EXPIRED: XCircle,
  AUCTION_CANCELLED: XCircle,
  SELLER_APPROVED: Store,
  SELLER_REJECTED: XCircle,
  SECOND_CHANCE_OFFER: RotateCcw,
  SECOND_CHANCE_ACCEPTED: CheckCircle2,
  SECOND_CHANCE_DECLINED: XCircle,
  NEW_REVIEW: Star,
  RESERVE_NOT_MET: AlertTriangle,
  DISPUTE_OPENED: ShieldAlert,
  DISPUTE_RESOLVED: Scale,
  DISPUTE_SELLER_RESOLVED: Scale,
  PAYOUT_SENT: Banknote,
  ORDER_COMPLETING: PackageCheck,
  ORDER_REFUNDED: Banknote,
};

/** Tone carries meaning: red only for things that cost the reader something. */
const TONES: Record<NotificationType, string> = {
  OUTBID: "text-danger-text",
  AUCTION_ENDING: "text-warning",
  AUCTION_EXTENDED: "text-warning",
  PAYMENT_REQUIRED: "text-danger-text",
  PAYMENT_EXPIRED: "text-danger-text",
  AUCTION_CANCELLED: "text-dim",
  AUCTION_LOST: "text-dim",
  AUCTION_STARTING: "text-signal-text",
  AUCTION_WON: "text-success",
  PAYMENT_SUCCESS: "text-success",
  SELLER_APPROVED: "text-success",
  SELLER_REJECTED: "text-dim",
  SECOND_CHANCE_OFFER: "text-signal-text",
  SECOND_CHANCE_ACCEPTED: "text-success",
  SECOND_CHANCE_DECLINED: "text-dim",
  NEW_REVIEW: "text-signal-text",
  RESERVE_NOT_MET: "text-warning",
  DISPUTE_OPENED: "text-danger-text",
  DISPUTE_RESOLVED: "text-signal-text",
  DISPUTE_SELLER_RESOLVED: "text-signal-text",
  PAYOUT_SENT: "text-success",
  ORDER_COMPLETING: "text-warning",
  ORDER_REFUNDED: "text-success",
};

/**
 * A line in the log. Unread is marked by a signal rule down the left edge
 * rather than a tinted card — the ledger stays flat and scannable.
 */
export function NotificationItem({
  notification,
  onMarkRead,
}: NotificationItemProps) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const format = useFormatter();

  const Icon = ICONS[notification.type];

  return (
    <li className="border-line group relative flex gap-4 border-t py-5 last:border-b">
      {!notification.isRead ? (
        <span
          className="bg-signal-text absolute inset-y-0 -left-4 w-0.5"
          aria-hidden="true"
        />
      ) : null}

      <span
        className={cn("mt-0.5 shrink-0", TONES[notification.type])}
        aria-hidden="true"
      >
        <Icon className="size-4" />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <h3
            className={cn(
              "label",
              notification.isRead ? "text-muted-foreground" : "text-foreground",
            )}
          >
            {notification.href ? (
              <Link
                href={notification.href}
                className="hover:text-signal-text transition-colors after:absolute after:inset-0 after:content-['']"
                onClick={() => onMarkRead?.(notification.id)}
              >
                {notification.title}
              </Link>
            ) : (
              notification.title
            )}
          </h3>
          <span className="mono-figure text-dim text-[11px]">
            {format.relativeTime(new Date(notification.createdAt))}
          </span>
          <span className="sr-only">
            {labels.notificationType(notification.type)}
          </span>
        </div>

        <p className="text-muted-foreground mt-2 text-sm leading-relaxed">
          {notification.message}
        </p>
      </div>

      {!notification.isRead && onMarkRead ? (
        <Button
          variant="ghost"
          size="sm"
          className="relative z-20 shrink-0 self-start"
          onClick={() => onMarkRead(notification.id)}
        >
          {t("markRead")}
          <span className="sr-only"> — {tc("confirm")}</span>
        </Button>
      ) : null}
    </li>
  );
}
