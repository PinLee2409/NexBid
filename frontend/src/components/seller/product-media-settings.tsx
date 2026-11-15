"use client";

import { Loader2, RotateCw, Trash2, Video } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useApiErrorMessage } from "@/hooks/use-labels";
import { removeProductVideo, setProductSpin, uploadProductVideo } from "@/services/seller-service";

/**
 * EN: A short video and the 360° view, for a product that exists already (both are saved at once, not with the form).
 * VI: Video ngắn và chế độ xem 360°, cho sản phẩm đã có (cả hai lưu ngay, không đợi lưu form).
 */
export function ProductMediaSettings({
  productId,
  videoUrl,
  spinView,
}: {
  productId: string;
  videoUrl: string | null;
  spinView: boolean;
}) {
  const t = useTranslations("seller");
  const errorMessage = useApiErrorMessage();
  const [video, setVideo] = useState(videoUrl);
  const [spin, setSpin] = useState(spinView);
  const [isPending, startTransition] = useTransition();

  function run(action: () => Promise<void>) {
    startTransition(async () => {
      try {
        await action();
      } catch (error) {
        toast.error(errorMessage(error));
      }
    });
  }

  return (
    <fieldset className="border-line space-y-5 border-t pt-6" data-testid="product-media">
      <legend className="label-sm text-dim pr-3">{t("mediaTitle")}</legend>

      <div className="space-y-2">
        <Label htmlFor="product-video" className="label-sm text-dim">
          {t("videoLabel")}
        </Label>
        {video ? (
          <div className="flex flex-wrap items-center gap-3">
            <video src={video} controls preload="metadata" className="max-h-48 max-w-full" />
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isPending}
              onClick={() =>
                run(async () => {
                  await removeProductVideo(productId);
                  setVideo(null);
                })
              }
            >
              <Trash2 className="size-4" />
              {t("videoRemove")}
            </Button>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Input
              id="product-video"
              type="file"
              accept="video/mp4,video/webm"
              disabled={isPending}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (!file) return;
                run(async () => {
                  const saved = await uploadProductVideo(productId, file);
                  setVideo(saved.videoUrl ?? null);
                  toast.success(t("videoSaved"));
                });
              }}
            />
            {isPending ? <Loader2 className="size-4 animate-spin" /> : <Video className="text-dim size-4" />}
          </div>
        )}
        <p className="text-dim text-xs">{t("videoHint")}</p>
      </div>

      <div className="flex items-start justify-between gap-6">
        <div>
          <Label htmlFor="product-spin" className="label inline-flex items-center gap-1.5">
            <RotateCw className="size-3.5" aria-hidden="true" />
            {t("spinLabel")}
          </Label>
          <p className="text-muted-foreground mt-1 text-xs leading-relaxed">{t("spinHint")}</p>
        </div>
        <Switch
          id="product-spin"
          checked={spin}
          disabled={isPending}
          onCheckedChange={(next) =>
            run(async () => {
              await setProductSpin(productId, next);
              setSpin(next);
            })
          }
        />
      </div>
    </fieldset>
  );
}
