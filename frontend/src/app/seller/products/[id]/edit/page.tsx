"use client";

import { PackageX } from "lucide-react";
import { useTranslations } from "next-intl";
import { use } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { TextBlockSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { ProductForm } from "@/components/seller/product-form";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { listCategories } from "@/services/auction-service";
import { getProduct } from "@/services/seller-service";

/**
 * Client-rendered so it reads the same in-browser catalogue the seller has
 * been editing in this session, rather than a fresh server-side copy.
 */
export default function EditProductPage(
  props: PageProps<"/seller/products/[id]/edit">,
) {
  const { id } = use(props.params);

  const t = useTranslations("seller");
  const tn = useTranslations("nav");

  const product = useAsyncData(`product-${id}`, () => getProduct(id));
  const categories = useAsyncData("categories", listCategories);

  const loading =
    product.state.status === "loading" || categories.state.status === "loading";

  return (
    <>
      <PageHeader
        title={t("editProductTitle")}
        description={
          product.state.status === "success"
            ? (product.state.data?.name ?? undefined)
            : undefined
        }
        breadcrumbs={[
          { label: tn("products"), href: "/seller/products" },
          { label: t("editProductTitle") },
        ]}
      />

      {loading ? (
        <div className="mt-8 max-w-2xl space-y-6">
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-11 w-full" />
          <TextBlockSkeleton lines={5} />
        </div>
      ) : product.state.status === "success" &&
        product.state.data !== null &&
        categories.state.status === "success" ? (
        <ProductForm
          categories={categories.state.data}
          product={product.state.data}
        />
      ) : (
        <EmptyState
          className="mt-8"
          icon={PackageX}
          title={t("productsEmptyTitle")}
          description={t("productsEmptyBody")}
          action={{ label: t("newProduct"), href: "/seller/products/create" }}
        />
      )}
    </>
  );
}
