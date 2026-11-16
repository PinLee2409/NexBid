"use client";

import { RotateCw } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState, type KeyboardEvent, type PointerEvent } from "react";

import { cn } from "@/lib/utils";
import type { ProductImage } from "@/types";

/** EN: Pixels of drag per frame. / VI: Số pixel kéo cho mỗi khung hình. */
const STEP = 12;

/**
 * EN: A 360° view: the photos, in order, are frames of a full turn. Drag sideways (mouse or finger) or use the arrow
 *     keys to turn the object; it wraps round. All frames are loaded up front so turning never waits.
 * VI: Xem 360°: các ảnh, theo thứ tự, là khung hình của một vòng xoay. Kéo ngang (chuột hoặc ngón tay) hoặc dùng phím
 *     mũi tên để xoay; tới cuối thì quay lại đầu. Mọi khung được tải trước nên xoay không phải chờ.
 */
export function SpinViewer({ images, className }: { images: ProductImage[]; className?: string }) {
  const t = useTranslations("auction");
  const [frame, setFrame] = useState(0);
  const drag = useRef<{ x: number; start: number } | null>(null);
  const count = images.length;

  const turn = (by: number) => setFrame((current) => (((current + by) % count) + count) % count);

  function down(event: PointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { x: event.clientX, start: frame };
  }

  function move(event: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return;
    const steps = Math.round((event.clientX - drag.current.x) / STEP);
    setFrame((((drag.current.start - steps) % count) + count) % count);
  }

  function key(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowLeft") turn(-1);
    else if (event.key === "ArrowRight") turn(1);
    else return;
    event.preventDefault();
  }

  return (
    <div
      role="slider"
      tabIndex={0}
      aria-label={t("spinLabel")}
      aria-valuemin={1}
      aria-valuemax={count}
      aria-valuenow={frame + 1}
      aria-valuetext={t("spinFrame", { frame: frame + 1, count })}
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={() => (drag.current = null)}
      onPointerCancel={() => (drag.current = null)}
      onKeyDown={key}
      className={cn(
        "on-media bg-surface relative aspect-[4/5] cursor-grab touch-pan-y overflow-hidden select-none active:cursor-grabbing sm:aspect-[4/3] lg:aspect-auto lg:h-[min(70svh,720px)]",
        className,
      )}
      data-testid="spin-viewer"
    >
      {images.map((image, index) => (
        // eslint-disable-next-line @next/next/no-img-element -- frames swap instantly; next/image would lazy-load and flicker
        <img
          key={image.id}
          src={image.url}
          alt={index === frame ? image.alt ?? "" : ""}
          aria-hidden={index !== frame}
          draggable={false}
          className={cn("absolute inset-0 size-full object-cover", index === frame ? "opacity-100" : "opacity-0")}
        />
      ))}
      <span className="label-sm bg-background/80 absolute bottom-6 left-6 inline-flex items-center gap-1.5 px-2.5 py-1.5">
        <RotateCw className="size-3.5" aria-hidden="true" />
        {t("spinHint")}
      </span>
    </div>
  );
}
