"use client";

import { BellRing, BellOff, Loader2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { getPushState, turnPushOff, turnPushOn, type PushState } from "@/services/push-service";

/**
 * EN: Browser notifications on the profile: outbid, ending soon, won and the rest arrive even with NexBid closed. Per
 *     browser, since each one asks its own permission.
 * VI: Thông báo trình duyệt trên trang hồ sơ: bị vượt giá, sắp kết thúc, thắng phiên và các thông báo khác tới cả khi
 *     đã đóng NexBid. Theo từng trình duyệt, vì mỗi trình duyệt tự xin quyền riêng.
 */
export function PushSettings() {
  const t = useTranslations("account");
  const locale = useLocale();
  const errorMessage = useApiErrorMessage();
  const [state, setState] = useState<PushState | null>(null);
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    let live = true;
    getPushState().then(
      (found) => live && setState(found),
      () => live && setState("off"),
    );
    return () => {
      live = false;
    };
  }, []);

  function change(on: boolean) {
    startTransition(async () => {
      try {
        const next = on ? await turnPushOn(locale) : await turnPushOff();
        setState(next);
        if (next === "on") toast.success(t("pushOn"));
        else if (next === "denied") toast.error(t("pushDenied"));
        else if (!on) toast.success(t("pushOff"));
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <section className="border-line mt-16 border-t pt-10" aria-labelledby="push-heading">
      <h2 id="push-heading" className="display text-2xl">
        {t("pushTitle")}
      </h2>
      <p className="text-muted-foreground mt-2 max-w-xl text-sm leading-relaxed">{t("pushBody")}</p>
      <div className="mt-6" data-testid="push-settings">
        {state === null ? (
          <Skeleton className="h-11 w-48" />
        ) : state === "unsupported" ? (
          <p className="text-dim text-sm">{t("pushUnsupported")}</p>
        ) : state === "denied" ? (
          <p className="text-dim text-sm">{t("pushDenied")}</p>
        ) : state === "on" ? (
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-success inline-flex items-center gap-1.5 text-sm font-medium">
              <BellRing className="size-4" />
              {t("pushOnHere")}
            </span>
            <Button variant="outline" size="sm" onClick={() => change(false)} disabled={isPending}>
              {isPending ? <Loader2 className="size-4 animate-spin" /> : <BellOff className="size-4" />}
              {t("pushTurnOff")}
            </Button>
          </div>
        ) : (
          <Button onClick={() => change(true)} disabled={isPending}>
            {isPending ? <Loader2 className="size-4 animate-spin" /> : <BellRing className="size-4" />}
            {t("pushTurnOn")}
          </Button>
        )}
      </div>
    </section>
  );
}
