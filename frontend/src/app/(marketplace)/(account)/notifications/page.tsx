"use client";

import { BellOff, CheckCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { NotificationItem } from "@/components/common/notification-item";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  markAllAsRead,
  markAsRead,
  useNotifications,
  useUnreadNotificationCount,
} from "@/services/notification-service";

type NotificationFilter = "ALL" | "UNREAD";

export default function NotificationsPage() {
  const t = useTranslations("account");

  const notifications = useNotifications();
  const unreadCount = useUnreadNotificationCount();
  const [filter, setFilter] = useState<NotificationFilter>("ALL");

  const visible =
    filter === "UNREAD"
      ? notifications.filter((item) => !item.isRead)
      : notifications;

  return (
    <>
      <PageHeader
        title={t("notificationsTitle")}
        description={t("notificationsSubtitle")}
        actions={
          unreadCount > 0 ? (
            <Button variant="outline" size="sm" onClick={() => markAllAsRead()}>
              <CheckCheck className="size-4" />
              {t("markAllRead")}
            </Button>
          ) : null
        }
      />

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <Tabs
          value={filter}
          onValueChange={(value) => setFilter(value as NotificationFilter)}
        >
          <TabsList>
            <TabsTrigger value="ALL">{t("filterAll")}</TabsTrigger>
            <TabsTrigger value="UNREAD">{t("filterUnread")}</TabsTrigger>
          </TabsList>
        </Tabs>

        <p className="text-muted-foreground text-sm">
          {t("unreadCount", { count: unreadCount })}
        </p>
      </div>

      <div className="mt-6">
        {visible.length === 0 ? (
          <EmptyState
            icon={BellOff}
            title={t("notificationsEmptyTitle")}
            description={t("notificationsEmptyBody")}
          />
        ) : (
          <ul>
            {visible.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onMarkRead={(id) => markAsRead(id)}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
