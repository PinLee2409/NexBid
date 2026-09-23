import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { PageHeader } from "@/components/layout/page-header";
import { ProductForm } from "@/components/seller/product-form";
import { listCategories } from "@/services/auction-service";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("seller");
  return { title: t("createProductTitle") };
}

export default async function CreateProductPage() {
  const [categories, t, tn] = await Promise.all([
    listCategories(),
    getTranslations("seller"),
    getTranslations("nav"),
  ]);

  return (
    <>
      <PageHeader
        title={t("createProductTitle")}
        description={t("createProductSubtitle")}
        breadcrumbs={[
          { label: tn("products"), href: "/seller/products" },
          { label: t("createProductTitle") },
        ]}
      />
      <ProductForm categories={categories} />
    </>
  );
}
