"use client";

import { useTranslations } from "next-intl";

import type { BidRejection } from "@/lib/auction-rules";
import { formatCurrency } from "@/lib/format";
import type {
  AuctionStatus,
  AuditAction,
  Category,
  NotificationType,
  OrderStatus,
  PaymentStatus,
  ProductCondition,
  UserRole,
} from "@/types";

/**
 * Localised labels for domain enums.
 *
 * Enum *values* live in `@/types`; their copy lives in `messages/*.json`.
 * These hooks are the only bridge between the two, so no component ever
 * hard-codes a status name.
 */
export function useEnumLabels() {
  const t = useTranslations("enums");

  return {
    auctionStatus: (status: AuctionStatus) => t(`auctionStatus.${status}`),
    condition: (condition: ProductCondition) => t(`condition.${condition}`),
    sort: (sort: string) => t(`sort.${sort}`),
    paymentStatus: (status: PaymentStatus) => t(`paymentStatus.${status}`),
    orderStatus: (status: OrderStatus) => t(`orderStatus.${status}`),
    notificationType: (type: NotificationType) => t(`notificationType.${type}`),
    auditAction: (action: AuditAction) => t(`auditAction.${action}`),
    role: (role: UserRole) => t(`role.${role}`),
    userStatus: (status: "ACTIVE" | "BLOCKED") => t(`userStatus.${status}`),
  };
}

/**
 * Category names come from the catalogue (and will come from the API), but
 * their display copy is translated by slug.
 */
export function useCategoryLabels() {
  const t = useTranslations("categories");

  return {
    name: (category: Pick<Category, "slug" | "name">) => {
      try {
        return t(`${category.slug}.name`);
      } catch {
        // Unknown slug from the API: fall back to whatever the server sent.
        return category.name;
      }
    },
    description: (category: Pick<Category, "slug" | "description">) => {
      try {
        return t(`${category.slug}.description`);
      } catch {
        return category.description;
      }
    },
  };
}

/** Turns a bid rejection code into the message a bidder should read. */
export function useBidRejectionMessage() {
  const t = useTranslations("bidErrors");

  return (rejection: BidRejection): string => {
    if (rejection.code === "BID_TOO_LOW") {
      return t("BID_TOO_LOW", {
        amount: formatCurrency(rejection.minimumAmount ?? 0),
      });
    }
    return t(rejection.code);
  };
}
