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
 * Categories as exhibition rooms: a grid of photographic panels with the name set over the image and the count of open
 * lots, so every room fits on one screen. No captions underneath — you look into the room.
 */
export function RoomsSection({ categories }: RoomsSectionProps) {
  const t = useTranslations("rooms");

  if (categories.length === 0) return null;

  return (
    <section
      id="categories"
      className="mx-auto max-w-[1680px] scroll-mt-20 px-5 py-16 sm:px-8 lg:py-24"
    >
      <SectionHead
        title={t("title")}
        description={t("lead", { count: categories.length })}
      />

      <div className="mt-12 grid grid-cols-2 gap-3 lg:grid-cols-4" data-testid="rooms">
        {categories.map((category, index) => (
          <Link
            key={category.id}
            href={`/auctions?category=${category.slug}`}
            className={cn(
              "group on-media bg-surface border-line relative flex aspect-square flex-col justify-between overflow-hidden border p-4 sm:aspect-[4/3] sm:p-5",
              // EN: The first room leads, two cells wide, when there are enough to fill the rows. / VI: Phòng đầu tiên rộng gấp đôi khi đủ phòng để lấp các hàng.
              index === 0 && categories.length > 4 ? "lg:col-span-2 lg:aspect-auto" : "",
            )}
          >
            <span className="absolute inset-0" aria-hidden="true">
              {category.imageUrl ? (
                <Image
                  src={category.imageUrl}
                  alt=""
                  fill
                  sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
                  className="object-cover opacity-45 transition-all duration-[900ms] ease-out group-hover:scale-105 group-hover:opacity-60"
                />
              ) : null}
              <span className="from-background via-background/40 absolute inset-0 bg-gradient-to-t to-transparent" />
            </span>

            <span className="relative flex items-start justify-between gap-4">
              <span
                className="mono-figure text-dim group-hover:text-signal-text text-xs transition-colors"
                aria-hidden="true"
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <span className="label text-dim group-hover:text-signal-text text-right transition-colors">
                {t("lotsLive", { count: category.auctionCount ?? 0 })}
              </span>
            </span>

            <span className="relative flex items-end justify-between gap-4">
              <span className="display min-w-0 text-lg sm:text-[clamp(1.5rem,3vw,2.75rem)]">
                <CategoryName category={category} />
              </span>
              <span className="text-signal-text inline-block shrink-0 transition-transform group-hover:translate-x-1" aria-hidden="true">
                →
              </span>
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
