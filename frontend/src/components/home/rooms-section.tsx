"use client";

import { useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { CategoryName } from "@/components/common/category-name";
import { SectionHead } from "@/components/nexbid/section-head";
import { cn } from "@/lib/utils";
import type { Category } from "@/types";

interface RoomsSectionProps {
  categories: Category[];
}

/**
 * Categories as exhibition rooms: full photographic panels with the name set
 * over the image. No tiles, no captions underneath — you look into the room.
 */
export function RoomsSection({ categories }: RoomsSectionProps) {
  const t = useTranslations("rooms");

  if (categories.length === 0) return null;

  return (
    <section
      id="categories"
      className="mx-auto max-w-[1680px] scroll-mt-20 px-5 py-16 sm:px-8 lg:py-24"
    >
      <SectionHead title={t("title")} description={t("lead")} />

      <div className="mt-12">
        {categories.map((category, index) => (
          <Link
            key={category.id}
            href={`/auctions?category=${category.slug}`}
            className="group border-line relative flex items-center overflow-hidden border-t last:border-b"
          >
            {/* The photograph only appears as the row is approached. */}
            <span className="absolute inset-0" aria-hidden="true">
              <Image
                src={category.imageUrl}
                alt=""
                fill
                sizes="100vw"
                className="object-cover opacity-0 transition-all duration-[900ms] ease-out group-hover:scale-105 group-hover:opacity-40"
              />
              <span className="from-background via-background/70 absolute inset-0 bg-gradient-to-r to-transparent" />
            </span>

            <span className="relative flex w-full items-baseline justify-between gap-6 py-7 sm:py-10">
              <span className="flex items-baseline gap-5 sm:gap-8">
                <span
                  className="mono-figure text-dim group-hover:text-signal-text w-8 text-xs transition-colors"
                  aria-hidden="true"
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
                <span
                  className={cn(
                    "display text-[clamp(1.75rem,5.5vw,4.5rem)] transition-all duration-500",
                    "group-hover:translate-x-2",
                  )}
                >
                  <CategoryName category={category} />
                </span>
              </span>

              <span className="label text-dim group-hover:text-signal-text shrink-0 text-right transition-colors">
                {t("lotsLive", { count: category.auctionCount })}
                <span className="ml-3 inline-block transition-transform group-hover:translate-x-1">
                  →
                </span>
              </span>
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
