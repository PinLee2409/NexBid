"use client";

import { BellPlus, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { buildAuctionHref } from "@/lib/auction-query";
import { canSaveSearch, saveSearch } from "@/services/alert-service";
import { useSession } from "@/services/session-service";
import type { AuctionQuery } from "@/types";

/**
 * EN: Saves the catalogue's current filters as an alert. Shown once the list is narrowed by something worth keeping;
 *     a visitor is sent to sign in and brought back to the same filters.
 * VI: Lưu bộ lọc hiện tại của danh mục thành một lần báo lô mới. Chỉ hiện khi danh sách đã được thu hẹp bởi thứ đáng giữ;
 *     khách được đưa đi đăng nhập rồi quay lại đúng bộ lọc đó.
 */
export function SaveSearchButton({ query }: { query: AuctionQuery }) {
  const t = useTranslations("auctions");
  const { user, hydrated } = useSession();
  const router = useRouter();
  const errorMessage = useApiErrorMessage();
  const [isPending, startTransition] = useTransition();

  if (!canSaveSearch(query) || !hydrated) return null;

  if (!user) {
    const back = buildAuctionHref({ ...query, page: 1 });
    return (
      <Button asChild variant="outline" size="sm" className="ml-auto h-auto max-w-full shrink py-1.5 text-left whitespace-normal">
        <Link href={`/login?redirect=${encodeURIComponent(back)}`}>
          <BellPlus className="size-3.5" aria-hidden="true" />
          {t("saveSearchSignIn")}
        </Link>
      </Button>
    );
  }

  function save() {
    startTransition(async () => {
      try {
        await saveSearch(query);
        toast.success(t("saveSearchSaved"), {
          action: { label: t("saveSearchManage"), onClick: () => router.push("/alerts") },
        });
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <Button variant="outline" size="sm" className="ml-auto h-auto max-w-full shrink py-1.5 text-left whitespace-normal" onClick={save} disabled={isPending}>
      {isPending ? (
        <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
      ) : (
        <BellPlus className="size-3.5" aria-hidden="true" />
      )}
      {t("saveSearch")}
    </Button>
  );
}
