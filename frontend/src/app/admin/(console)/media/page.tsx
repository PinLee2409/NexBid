"use client";

import { Flag, ImageIcon } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ApiErrorState } from "@/components/common/api-error-state";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { ProductPhoto } from "@/components/common/product-photo";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAsyncData } from "@/hooks/use-async-data";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { keepMedia, mediaQueue, removeMedia, type MediaItem } from "@/services/market-service";

/**
 * EN: Photos and videos no admin has looked at yet, newest first, with reported lots on top. Keep marks one as looked
 *     at; Remove takes it down and tells the seller why.
 * VI: Ảnh và video chưa admin nào xem, mới nhất trước, lô bị báo cáo xếp trên cùng. Giữ thì đánh dấu đã xem; Gỡ thì
 *     xoá đi và báo người bán lý do.
 */
export default function AdminMediaPage() {
  const t = useTranslations("media");
  const { state, refresh } = useAsyncData("admin-media", mediaQueue);

  return (
    <>
      <PageHeader title={t("title")} description={t("subtitle")} />
      <div className="mt-8">
        {state.status === "loading" ? (
          <AuctionListRowSkeleton />
        ) : state.status === "error" ? (
          <ApiErrorState icon={ImageIcon} error={state.error} />
        ) : state.data.length === 0 ? (
          <EmptyState icon={ImageIcon} title={t("emptyTitle")} description={t("emptyBody")} />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" data-testid="media-queue">
            {state.data.map((item) => (
              <MediaCard key={`${item.kind}-${item.id}`} item={item} onDone={refresh} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function MediaCard({ item, onDone }: { item: MediaItem; onDone: () => void }) {
  const t = useTranslations("media");
  const format = useFormatter();
  const errorMessage = useApiErrorMessage();
  const [reason, setReason] = useState("");
  const [isPending, startTransition] = useTransition();

  function run(task: () => Promise<void>, done: string) {
    startTransition(async () => {
      try {
        await task();
        toast.success(done);
        onDone();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <li className="border-line flex flex-col border">
      <div className="bg-surface relative aspect-[4/3]">
        {item.kind === "VIDEO" ? (
          <video src={item.url} controls preload="metadata" className="size-full object-contain" />
        ) : (
          <ProductPhoto src={item.url} alt={item.productName} fill sizes="(max-width: 640px) 100vw, 33vw" className="object-contain" />
        )}
        {item.reported ? (
          <span className="label-sm bg-danger text-background absolute top-2 left-2 inline-flex items-center gap-1 px-2 py-1">
            <Flag className="size-3" aria-hidden="true" />
            {t("reported")}
          </span>
        ) : null}
      </div>
      <div className="flex flex-1 flex-col gap-3 p-4 text-sm">
        <div>
          <p className="font-medium [overflow-wrap:anywhere]">{item.productName}</p>
          <p className="text-dim text-xs">
            {item.kind === "VIDEO" ? t("video") : t("photo")} · {item.sellerName} ·{" "}
            {format.dateTime(new Date(item.uploadedAt), { dateStyle: "medium", timeStyle: "short" })}
          </p>
          <Link href={`/sellers/${item.sellerId}`} className="text-muted-foreground hover:text-signal-text text-xs">
            {t("sellerPage")}
          </Link>
        </div>
        <Input
          aria-label={t("reason")}
          placeholder={t("reason")}
          value={reason}
          maxLength={300}
          onChange={(event) => setReason(event.target.value)}
          className="h-9"
        />
        <div className="mt-auto flex gap-2">
          <Button size="sm" variant="outline" disabled={isPending} onClick={() => run(() => keepMedia(item), t("kept"))}>
            {t("keep")}
          </Button>
          <Button
            size="sm"
            variant="destructive"
            disabled={isPending}
            onClick={() => run(() => removeMedia(item, reason), t("removed"))}
          >
            {t("remove")}
          </Button>
        </div>
      </div>
    </li>
  );
}
