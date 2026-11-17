"use client";

import { AnimatePresence, m } from "framer-motion";
import { useTranslations } from "next-intl";
import { useState } from "react";

import { ProductPhoto } from "@/components/common/product-photo";
import { formatLot } from "@/components/nexbid/lot-number";
import { cn } from "@/lib/utils";
import type { ProductImage } from "@/types";

interface ProductStageProps {
  images: ProductImage[];
  lotNumber: number;
  className?: string;
}

/**
 * The object, floated against the dark, the image crossfading between photos. The stage stops short of the fold so the
 * price and the description are in reach, and a strip of thumbnails underneath jumps straight to any photo.
 */
export function ProductStage({ images, lotNumber, className }: ProductStageProps) {
  const t = useTranslations("auction");
  const [index, setIndex] = useState(0);

  if (images.length === 0) {
    return (
      <div className={cn("bg-surface relative aspect-[4/5]", className)}>
        <ProductPhoto src={undefined} />
      </div>
    );
  }

  const active = images[Math.min(index, images.length - 1)];

  return (
    <div className={cn("relative", className)}>
      <div className="on-media bg-surface relative aspect-[4/5] overflow-hidden sm:aspect-[4/3] lg:aspect-auto lg:h-[min(70svh,720px)]">
        <AnimatePresence mode="wait" initial={false}>
          <m.div
            key={active.id}
            initial={{ opacity: 0, scale: 1.02 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
            className="absolute inset-0"
          >
            <ProductPhoto
              src={active.url}
              alt={active.alt}
              loading="eager"
              fetchPriority="high"
              sizes="(max-width: 1024px) 100vw, 70vw"
              className="object-cover"
            />
          </m.div>
        </AnimatePresence>

        {/* The lot index, set into the corner of the stage. */}
        <span
          className="display figure over-image text-foreground/70 absolute top-5 left-5 text-[clamp(1.75rem,3vw,2.75rem)] leading-none select-none"
          aria-hidden="true"
        >
          {formatLot(lotNumber)}
        </span>

        {images.length > 1 ? (
          <span className="mono-figure label-sm bg-background/75 absolute right-4 bottom-4 px-2 py-1" aria-hidden="true">
            {String(index + 1).padStart(2, "0")} / {String(images.length).padStart(2, "0")}
          </span>
        ) : null}
      </div>

      {images.length > 1 ? (
        <nav aria-label={t("gallery")} className="mt-3 flex gap-2 overflow-x-auto pb-1" data-testid="stage-thumbnails">
          {images.map((image, position) => (
            <button
              key={image.id}
              type="button"
              onClick={() => setIndex(position)}
              aria-label={t("viewImage", { index: String(position + 1).padStart(2, "0") })}
              aria-current={position === index}
              className={cn(
                "bg-surface relative size-16 shrink-0 overflow-hidden border-2 transition-colors sm:size-20",
                position === index ? "border-signal" : "border-transparent opacity-70 hover:opacity-100",
              )}
            >
              <ProductPhoto src={image.url} alt="" sizes="80px" className="object-cover" />
            </button>
          ))}
        </nav>
      ) : null}
    </div>
  );
}
