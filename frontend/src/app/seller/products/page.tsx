"use client";

import { Gavel, Package, Pencil, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Image from "next/image";
import Link from "next/link";

import { AuctionStatusBadge } from "@/components/auction/auction-status-badge";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { useAsyncData } from "@/hooks/use-async-data";
import { useCategoryLabels, useEnumLabels } from "@/hooks/use-labels";
import { isLocalImage } from "@/lib/images";
import { listMyProducts, type ProductWithMeta } from "@/services/seller-service";
import type { ProductStatus } from "@/types";

export default function SellerProductsPage() {
  const t = useTranslations("seller");
  const tc = useTranslations("common");

  const { state } = useAsyncData("seller-products", listMyProducts);

  return (
    <>
      <PageHeader
        title={t("productsTitle")}
        description={t("productsSubtitle")}
        actions={
          <Button asChild>
            <Link href="/seller/products/create">
              <Plus className="size-4" />
              {t("newProduct")}
            </Link>
          </Button>
        }
      />

      <div className="mt-10">
        {state.status === "loading" ? (
          <div>
            {Array.from({ length: 3 }).map((_, index) => (
              <AuctionListRowSkeleton key={index} />
            ))}
          </div>
        ) : state.status === "error" ? (
          <EmptyState
            tone="error"
            icon={Package}
            title={tc("tryAgain")}
            description={state.error.message}
          />
        ) : state.data.length === 0 ? (
          <EmptyState
            icon={Package}
            title={t("productsEmptyTitle")}
            description={t("productsEmptyBody")}
            action={{ label: t("newProduct"), href: "/seller/products/create" }}
          />
        ) : (
          <ul>
            {state.data.map((entry) => (
              <ProductRow key={entry.product.id} entry={entry} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

const PRODUCT_STATUS_KEYS: Record<ProductStatus, string> = {
  DRAFT: "productStatusDraft",
  AVAILABLE: "productStatusAvailable",
  IN_AUCTION: "productStatusInAuction",
  SOLD: "productStatusSold",
};

function ProductRow({ entry }: { entry: ProductWithMeta }) {
  const t = useTranslations("seller");
  const tc = useTranslations("common");
  const labels = useEnumLabels();
  const categoryLabels = useCategoryLabels();
  const format = useFormatter();

  const { product, category, auction } = entry;
  const cover = product.images[0];

  return (
    <li className="border-line group flex flex-wrap items-center gap-x-6 gap-y-4 border-t py-5 last:border-b">
      <div className="on-media bg-surface relative size-16 shrink-0 overflow-hidden">
        {cover ? (
          <Image
            unoptimized={isLocalImage(cover.url)}
            src={cover.url}
            alt={cover.alt}
            fill
            sizes="80px"
            className="object-cover"
          />
        ) : null}
      </div>

      <div className="min-w-[12rem] flex-1">
        <h3 className="display text-lg sm:text-xl">
          <span className="line-clamp-1">{product.name}</span>
        </h3>
        <p className="label-sm text-dim mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
          <span>{category ? categoryLabels.name(category) : "—"}</span>
          <span aria-hidden="true">·</span>
          <span>{labels.condition(product.condition)}</span>
          <span aria-hidden="true">·</span>
          <span className="mono-figure">
            {format.dateTime(new Date(product.createdAt), { dateStyle: "medium" })}
          </span>
        </p>
      </div>

      <div className="shrink-0">
        <p className="label-sm text-dim mb-1">{tc("status")}</p>
        <p className="label">{t(PRODUCT_STATUS_KEYS[product.status])}</p>
      </div>

      <div className="w-32 shrink-0">
        {auction ? (
          <AuctionStatusBadge status={auction.status} size="sm" />
        ) : (
          <span className="text-dim text-xs">—</span>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <Button asChild size="sm" variant="outline">
          <Link href={`/seller/products/${product.id}/edit`}>
            <Pencil className="size-3.5" />
            {tc("edit")}
          </Link>
        </Button>
        {!auction ? (
          <Button asChild size="sm">
            <Link href={`/seller/auctions/create?productId=${product.id}`}>
              <Gavel className="size-3.5" />
              {t("scheduleAuction")}
            </Link>
          </Button>
        ) : null}
      </div>
    </li>
  );
}
