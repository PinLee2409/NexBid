"use client";

import { Loader2, Mail, RotateCcw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { listEmails, retryEmail, type EmailStatus, type OutboxEmail } from "@/services/email-admin-service";

const TABS: EmailStatus[] = ["FAILED", "PENDING", "SENT", "SKIPPED"];

/**
 * EN: The email outbox. Emails Brevo kept refusing end up FAILED after their retries; once the cause is fixed (sender
 *     address, key, an outage), the admin sends them again from here.
 * VI: Hàng chờ email. Email mà Brevo liên tục từ chối sẽ thành FAILED sau các lần thử lại; khi đã sửa nguyên nhân (địa
 *     chỉ người gửi, key, sự cố), admin gửi lại chúng từ đây.
 */
export default function AdminEmailsPage() {
  const t = useTranslations("admin");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [tab, setTab] = useState<EmailStatus>("FAILED");
  const { state, refresh } = useAsyncData(`admin-emails-${tab}`, () => listEmails(tab));
  const [retrying, setRetrying] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  function retry(email: OutboxEmail) {
    setRetrying(email.id);
    startTransition(async () => {
      try {
        await retryEmail(email.id);
        toast.success(t("emailsRetried"));
        refresh();
      } catch (error) {
        toast.error(errorMessage(error));
      } finally {
        setRetrying(null);
      }
    });
  }

  const counts = state.status === "success" ? state.data.counts : null;

  return (
    <>
      <PageHeader title={t("emailsTitle")} description={t("emailsSubtitle")} />

      <Tabs value={tab} onValueChange={(value) => setTab(value as EmailStatus)} className="mt-6 block">
        <TabsList>
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`emailStatus.${key}`)}
              {counts ? <span className="mono-figure text-dim ml-1.5 text-[11px]">{counts[key] ?? 0}</span> : null}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-8">
          {state.status === "loading" ? (
            <AuctionListRowSkeleton />
          ) : state.status === "error" ? (
            <ApiErrorState icon={Mail} error={state.error} />
          ) : state.data.items.length === 0 ? (
            <EmptyState icon={Mail} title={t("emailsEmptyTitle")} description={t("emailsEmptyBody")} />
          ) : (
            <ul className="border-line divide-line divide-y border-y">
              {state.data.items.map((email) => (
                <li key={email.id} className="flex flex-wrap items-center gap-x-6 gap-y-2 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{email.subject}</p>
                    <p className="text-muted-foreground text-xs">
                      {email.toEmail} · <span className="mono-figure">{email.kind}</span> ·{" "}
                      {format.dateTime(new Date(email.createdAt), { dateStyle: "medium", timeStyle: "short" })}
                      {email.attempts > 0 ? ` · ${t("emailsAttempts", { count: email.attempts })}` : ""}
                    </p>
                    {email.lastError ? (
                      <p className="text-danger-text mt-1 line-clamp-2 text-xs">{email.lastError}</p>
                    ) : null}
                  </div>
                  {email.status === "FAILED" ? (
                    <Button size="sm" variant="outline" onClick={() => retry(email)} disabled={retrying !== null}>
                      {retrying === email.id ? (
                        <Loader2 className="size-4 animate-spin" />
                      ) : (
                        <RotateCcw className="size-4" />
                      )}
                      {t("emailsRetry")}
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>
    </>
  );
}
