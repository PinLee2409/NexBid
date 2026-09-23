"use client";

import { useTranslations } from "next-intl";
import { use } from "react";

import { PageHeader } from "@/components/layout/page-header";
import { AuctionForm } from "@/components/seller/auction-form";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { listMyProducts } from "@/services/seller-service";

export default function CreateAuctionPage(
  props: PageProps<"/seller/auctions/create">,
) {
  const searchParams = use(props.searchParams);
  const t = useTranslations("seller");
  const tn = useTranslations("nav");

  const { state } = useAsyncData("seller-products", listMyProducts);

  const productIdParam = searchParams.productId;
  const initialProductId = Array.isArray(productIdParam)
    ? productIdParam[0]
    : productIdParam;

  // Only products not already tied to an auction can be scheduled.
  const available =
    state.status === "success"
      ? state.data.filter((entry) => entry.auction === null)
      : [];

  return (
    <>
      <PageHeader
        title={t("createAuctionTitle")}
        description={t("createAuctionSubtitle")}
        breadcrumbs={[
          { label: tn("auctions"), href: "/seller/auctions" },
          { label: t("createAuctionTitle") },
        ]}
      />

      {state.status === "loading" ? (
        <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_300px]">
          <div className="space-y-6">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-40 w-full rounded-none" />
            ))}
          </div>
          <Skeleton className="h-80 w-full rounded-none" />
        </div>
      ) : (
        <AuctionForm products={available} initialProductId={initialProductId} />
      )}
    </>
  );
}
