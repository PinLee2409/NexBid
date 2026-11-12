"use client";

import { BellOff, Loader2, MailCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useSyncExternalStore, useTransition } from "react";

import { Container } from "@/components/layout/container";
import { EmptyState } from "@/components/common/empty-state";
import { Button } from "@/components/ui/button";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { unsubscribeFromOutbidEmails } from "@/services/account-service";

const noSubscription = () => () => {};
const readToken = () => new URLSearchParams(window.location.search).get("token");

/**
 * EN: Where the link in an outbid email lands. It asks for a click rather than acting on arrival: mail scanners open
 *     links on their own, and that must not unsubscribe anyone.
 * VI: Nơi link trong email bị vượt giá dẫn tới. Trang yêu cầu bấm một lần thay vì tự làm khi vừa mở: trình quét mail tự
 *     mở link, và việc đó không được làm ai bị huỷ đăng ký.
 */
export default function UnsubscribePage() {
  const t = useTranslations("account");
  const errorMessage = useApiErrorMessage();
  const token = useSyncExternalStore(noSubscription, readToken, () => null);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function confirm() {
    if (!token) return;
    startTransition(async () => {
      try {
        await unsubscribeFromOutbidEmails(token);
        setDone(true);
      } catch (failure) {
        setError(errorMessage(failure));
      }
    });
  }

  return (
    <Container className="py-16 lg:py-24">
      <div className="mx-auto max-w-xl">
        {done ? (
          <EmptyState
            icon={MailCheck}
            title={t("unsubscribedTitle")}
            description={t("unsubscribedBody")}
            action={{ label: t("emailTitle"), href: "/profile#email" }}
          />
        ) : (
          <EmptyState
            icon={BellOff}
            tone={error ? "error" : "default"}
            title={t("unsubscribeTitle")}
            description={error ?? (token ? t("unsubscribeBody") : t("unsubscribeNoToken"))}
          >
            {token ? (
              <Button onClick={confirm} disabled={isPending} className="mt-6">
                {isPending ? <Loader2 className="size-4 animate-spin" /> : <BellOff className="size-4" />}
                {t("unsubscribeConfirm")}
              </Button>
            ) : null}
          </EmptyState>
        )}
      </div>
    </Container>
  );
}
