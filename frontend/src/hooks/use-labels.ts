"use client";

import { useTranslations } from "next-intl";

import type { BidRejection } from "@/lib/auction-rules";
import { formatCurrency } from "@/lib/format";
import { ApiError } from "@/services/api/http";
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
 * Category names come from the API, but the seeded ones have translated
 * display copy keyed by slug. A category an admin adds later has none, so it
 * is shown as the server sent it.
 */
export function useCategoryLabels() {
  const t = useTranslations("categories");

  return {
    name: (category: Pick<Category, "slug" | "name">) =>
      t.has(`${category.slug}.name`) ? t(`${category.slug}.name`) : category.name,
    description: (category: Pick<Category, "slug" | "description">) =>
      t.has(`${category.slug}.description`)
        ? t(`${category.slug}.description`)
        : category.description,
  };
}

/** Turns a bid rejection code into the message a bidder should read. */
export function useBidRejectionMessage() {
  const t = useTranslations("bidErrors");

  return (rejection: BidRejection): string => {
    if (rejection.code === "BID_TOO_LOW" || rejection.code === "AUTO_BID_INVALID") {
      return t(rejection.code, {
        amount: formatCurrency(rejection.minimumAmount ?? 0),
      });
    }
    return t(rejection.code);
  };
}

/**
 * Localised text for any refusal from the API. The code is the contract; the
 * server's own message is for developers and never shown.
 */
/**
 * EN: Why signing in or up failed, in the reader's language. The server's own message is English; only its
 *     code is used.
 * VI: Vì sao đăng nhập hay đăng ký thất bại, theo ngôn ngữ người đọc. Thông báo của server là tiếng Anh; chỉ
 *     dùng mã lỗi của nó.
 */
export function useAuthErrorMessage() {
  const t = useTranslations("auth.errors");
  const apiError = useApiErrorMessage();

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : "INTERNAL_ERROR";
    switch (code) {
      case "INVALID_CREDENTIALS":
        return t("invalidCredentials");
      case "EMAIL_ALREADY_EXISTS":
        return t("emailExists");
      case "ACCOUNT_BLOCKED":
        return t("blocked");
      case "LOGIN_RATE_LIMITED": {
        const seconds = error instanceof ApiError ? error.retryAfterSeconds ?? 60 : 60;
        return t("tooManyAttempts", { minutes: Math.max(1, Math.ceil(seconds / 60)) });
      }
      default:
        return apiError(error);
    }
  };
}

export function useApiErrorMessage() {
  const t = useTranslations("apiErrors");

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : "INTERNAL_ERROR";
    return t.has(code) ? t(code) : t("INTERNAL_ERROR");
  };
}
