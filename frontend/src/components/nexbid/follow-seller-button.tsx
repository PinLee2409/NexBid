"use client";

import { BellRing, Check, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { isFollowing, setFollowing } from "@/services/alert-service";
import { useSession } from "@/services/session-service";

/**
 * EN: Follow the lot's seller to hear of their new lots. Hidden from the seller themself; a visitor is sent to sign in.
 * VI: Theo dõi người bán của lô để được báo lô mới của họ. Ẩn với chính người bán; khách được đưa đi đăng nhập.
 */
export function FollowSellerButton({ sellerId, returnTo }: { sellerId: string; returnTo: string }) {
  const t = useTranslations("auction");
  const { user, hydrated } = useSession();
  const errorMessage = useApiErrorMessage();
  const [following, setFollowingState] = useState<boolean | null>(null);
  const [isPending, startTransition] = useTransition();

  const userId = user?.id;
  useEffect(() => {
    if (!userId || userId === sellerId) return;
    let cancelled = false;
    isFollowing(sellerId)
      .then((value) => {
        if (!cancelled) setFollowingState(value);
      })
      .catch(() => {
        if (!cancelled) setFollowingState(false);
      });
    return () => {
      cancelled = true;
    };
  }, [userId, sellerId]);

  if (!hydrated || userId === sellerId) return null;

  if (!user) {
    return (
      <Button asChild variant="outline" size="sm">
        <Link href={`/login?redirect=${encodeURIComponent(returnTo)}`}>
          <BellRing className="size-3.5" aria-hidden="true" />
          {t("followSignIn")}
        </Link>
      </Button>
    );
  }

  function toggle() {
    const next = !following;
    startTransition(async () => {
      try {
        setFollowingState(await setFollowing(sellerId, next));
        toast.success(next ? t("followed") : t("unfollowed"));
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <Button
      variant={following ? "secondary" : "outline"}
      size="sm"
      onClick={toggle}
      disabled={following === null || isPending}
      aria-pressed={following ?? false}
    >
      {isPending || following === null ? (
        <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
      ) : following ? (
        <Check className="size-3.5" aria-hidden="true" />
      ) : (
        <BellRing className="size-3.5" aria-hidden="true" />
      )}
      {following ? t("followingSeller") : t("followSeller")}
    </Button>
  );
}
