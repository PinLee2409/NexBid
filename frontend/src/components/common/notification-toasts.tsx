"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { toast } from "sonner";

import { markAsRead, onNotificationArrival } from "@/services/notification-service";

/**
 * EN: Pops each notice that arrives live (spec §38), with a shortcut to what it is about. Skipped on the
 *     page the notice links to — the lot page already shows its own outbid banner.
 * VI: Bật lên mỗi thông báo tới trực tiếp (spec §38), kèm lối tắt tới nội dung liên quan. Bỏ qua khi đang ở
 *     đúng trang mà thông báo trỏ tới — trang lô đã có banner bị vượt giá của riêng nó.
 */
export function NotificationToasts() {
  const t = useTranslations("common");
  const router = useRouter();

  useEffect(
    () =>
      onNotificationArrival((notification) => {
        const href = notification.href;
        if (href && window.location.pathname === href) return;

        toast(notification.title, {
          description: notification.message,
          action: href
            ? {
                label: t("viewDetails"),
                onClick: () => {
                  void markAsRead(notification.id);
                  router.push(href);
                },
              }
            : undefined,
        });
      }),
    [router, t],
  );

  return null;
}
