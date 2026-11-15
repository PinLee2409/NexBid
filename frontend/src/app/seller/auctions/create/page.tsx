"use client";

import { useTranslations } from "next-intl";
import { use } from "react";

import { PageHeader } from "@/components/layout/page-header";
import { AuctionForm, type RelistFrom } from "@/components/seller/auction-form";
import { Skeleton } from "@/components/ui/skeleton";
import { useAsyncData } from "@/hooks/use-async-data";
import { isRelistable } from "@/lib/auction-rules";
import { listMyAuctions, listMyProducts } from "@/services/seller-service";

export default function CreateAuctionPage(
  props: PageProps<"/seller/auctions/create">,
) {
  const searchParams = use(props.searchParams);
  const t = useTranslations("seller");
  const tn = useTranslations("nav");

  const { state } = useAsyncData("seller-products", listMyProducts);
  const { state: lots } = useAsyncData("seller-auctions", listMyAuctions);

  const productIdParam = searchParams.productId;
  const initialProductId = Array.isArray(productIdParam)
    ? productIdParam[0]
    : productIdParam;
  const relistParam = searchParams.relist;
  const relistId = Array.isArray(relistParam) ? relistParam[0] : relistParam;
  const previous =
    relistId && lots.status === "success" ? lots.data.find((lot) => lot.id === relistId) : undefined;
  const relist: RelistFrom | undefined = previous
    ? {
        productId: previous.productId,
        startingPrice: previous.startingPrice,
        minimumIncrement: previous.minimumIncrement,
        reservePrice: previous.reservePrice ?? null,
        buyNowPrice: previous.buyNowPrice,
        freeShipping: previous.freeShipping,
        antiSniping: previous.antiSniping,
      }
    : undefined;

  // EN: Products free to list: never auctioned, or back with the seller after a lot that did not sell.
  // VI: Sản phẩm được đăng: chưa từng đấu giá, hoặc đã về lại người bán sau một lô không bán được.
  const available =
    state.status === "success"
      ? state.data.filter((entry) => entry.auction === null || isRelistable(entry.auction.status, entry.product.status))
      : [];
  const waiting = state.status === "loading" || (relistId !== undefined && lots.status === "loading");

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

      {waiting ? (
        <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_300px]">
          <div className="space-y-6">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-40 w-full rounded-none" />
            ))}
          </div>
          <Skeleton className="h-80 w-full rounded-none" />
        </div>
      ) : (
        <AuctionForm
          key={relist?.productId ?? "new"}
          products={available}
          initialProductId={initialProductId}
          relist={relist}
        />
      )}
    </>
  );
}
