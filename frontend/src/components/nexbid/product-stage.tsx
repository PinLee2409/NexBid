"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useTranslations } from "next-intl";
import Image from "next/image";
import { useState } from "react";

import { formatLot } from "@/components/nexbid/lot-number";
import { isLocalImage } from "@/lib/images";
import { cn } from "@/lib/utils";
import type { ProductImage } from "@/types";

interface ProductStageProps {
  images: ProductImage[];
  lotNumber: number;
  className?: string;
}

/**
 * The object, floated against the dark. No frame, no thumbnails strip — a
 * numbered index down the side, and the image crossfading between them.
 */
export function ProductStage({ images, lotNumber, className }: ProductStageProps) {
  const t = useTranslations("auction");
  const [index, setIndex] = useState(0);

  if (images.length === 0) {
    return <div className={cn("bg-surface aspect-[4/5]", className)} />;
  }

  const active = images[Math.min(index, images.length - 1)];

  return (
    <div className={cn("relative", className)}>
      <div className="on-media bg-surface relative aspect-[4/5] overflow-hidden lg:aspect-auto lg:h-[calc(100svh-4rem)]">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={active.id}
            initial={{ opacity: 0, scale: 1.02 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
            className="absolute inset-0"
          >
            <Image
              unoptimized={isLocalImage(active.url)}
              src={active.url}
              alt={active.alt}
              fill
              priority
              sizes="(max-width: 1024px) 100vw, 70vw"
              className="object-cover"
            />
          </motion.div>
        </AnimatePresence>

        {/* The lot index, set into the corner of the stage. */}
        <span
          className="display over-image text-foreground/15 absolute top-6 left-6 text-[clamp(3rem,7vw,7rem)] leading-none select-none"
          aria-hidden="true"
        >
          {formatLot(lotNumber)}
        </span>

        {images.length > 1 ? (
          <nav
            aria-label={t("gallery")}
            className="absolute bottom-6 left-6 flex items-center gap-4"
          >
            {images.map((image, position) => (
              <button
                key={image.id}
                type="button"
                onClick={() => setIndex(position)}
                aria-label={t("viewImage", { index: position + 1 })}
                aria-current={position === index}
                className={cn(
                  "mono-figure text-xs transition-colors",
                  position === index
                    ? "text-signal-text"
                    : "text-foreground/45 hover:text-foreground",
                )}
              >
                {String(position + 1).padStart(2, "0")}
                <span
                  className={cn(
                    "mt-1.5 block h-px transition-all duration-300",
                    position === index ? "bg-signal-text w-6" : "bg-foreground/30 w-3",
                  )}
                />
              </button>
            ))}
          </nav>
        ) : null}
      </div>
    </div>
  );
}
