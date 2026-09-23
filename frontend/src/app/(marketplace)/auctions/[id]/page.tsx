import { BadgeCheck, ChevronLeft, Star } from "lucide-react";
import type { Metadata } from "next";
import { getFormatter, getTranslations } from "next-intl/server";
import Link from "next/link";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";

import { CategoryName } from "@/components/common/category-name";
import { AuctionRoomView } from "@/components/nexbid/auction-room";
import { AuctionCatalogueItem } from "@/components/nexbid/catalogue-item";
import { ProductStage } from "@/components/nexbid/product-stage";
import { SectionHead } from "@/components/nexbid/section-head";
import { formatCurrency } from "@/lib/format";
import { getAuction, listAuctions } from "@/services/auction-service";

export const dynamic = "force-dynamic";

export async function generateMetadata(
  props: PageProps<"/auctions/[id]">,
): Promise<Metadata> {
  const { id } = await props.params;
  const auction = await getAuction(id);

  if (!auction) {
    const t = await getTranslations("auction");
    return { title: t("notFoundTitle") };
  }

  return {
    title: auction.product.name,
    description: auction.product.description.slice(0, 160),
  };
}

export default async function AuctionDetailPage(
  props: PageProps<"/auctions/[id]">,
) {
  const { id } = await props.params;
  const auction = await getAuction(id);

  if (!auction) notFound();

  const [t, tf, format, related] = await Promise.all([
    getTranslations("auction"),
    getTranslations("focus"),
    getFormatter(),
    listAuctions({
      categorySlugs: [auction.category.slug],
      statuses: ["ACTIVE"],
      pageSize: 5,
    }),
  ]);

  const serverTime = new Date().toISOString();
  const relatedLots = related.items
    .filter((item) => item.id !== auction.id)
    .slice(0, 4);

  return (
    <>
      <div className="mx-auto max-w-[1680px] px-5 py-6 pb-28 sm:px-8 lg:py-10 lg:pb-16">
        <Link
          href="/auctions"
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-sm font-medium transition-colors"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
          {t("backToAuctions")}
        </Link>

        <div className="mt-6 grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,400px)] lg:gap-12">
          {/* Left column: what the lot is. */}
          <div className="min-w-0">
            <ProductStage
              images={auction.product.images}
              lotNumber={auction.lotNumber}
            />

            <div className="mt-8">
              <p className="label text-muted-foreground">
                <CategoryName category={auction.category} />
              </p>
              <h1 className="display mt-3 text-[clamp(2rem,5vw,4rem)]">
                {auction.product.name}
              </h1>

              <dl className="border-line mt-8 border-t">
                <SpecRow
                  label={tf("year")}
                  value={format.dateTime(new Date(auction.product.createdAt), {
                    year: "numeric",
                  })}
                />
                <SpecRow
                  label={tf("condition")}
                  value={<ConditionLabel condition={auction.product.condition} />}
                />
                <SpecRow
                  label={t("bidPanelTitle")}
                  value={formatCurrency(auction.startingPrice)}
                />
                <SpecRow
                  label={tf("provenance")}
                  value={tf("originalOwner")}
                />
                <SpecRow label={tf("included")} value={tf("boxAndPapers")} />
              </dl>

              <h2 className="display mt-14 text-[clamp(2rem,5vw,4rem)]">
                {tf("theObject")}
              </h2>
              <p className="text-muted-foreground mt-6 max-w-2xl text-[15px] leading-[1.75] whitespace-pre-line">
                {auction.product.description}
              </p>

              <div className="border-line mt-10 flex items-center gap-4 border-t pt-6">
                <span
                  className="bg-foreground text-background flex size-11 shrink-0 items-center justify-center text-sm font-semibold"
                  aria-hidden="true"
                >
                  {auction.seller.displayName.slice(0, 2).toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1.5 text-sm font-semibold">
                    {auction.seller.displayName}
                    {auction.seller.verified ? (
                      <BadgeCheck
                        className="text-signal-text size-4"
                        aria-label={t("verifiedSeller")}
                      />
                    ) : null}
                  </p>
                  <p className="text-muted-foreground mt-0.5 flex flex-wrap items-center gap-x-3 text-xs">
                    <span className="inline-flex items-center gap-1">
                      <Star className="size-3 fill-current" aria-hidden="true" />
                      {t("sellerRating", { rating: auction.seller.rating })}
                    </span>
                    <span>
                      {t("sellerSales", { count: auction.seller.totalSales })}
                    </span>
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right column: what it costs and how to win it. */}
          <div id="bid-panel" className="min-w-0 scroll-mt-24">
            <AuctionRoomView auction={auction} serverTime={serverTime} />
          </div>
        </div>

        {relatedLots.length > 0 ? (
          <section className="mt-20" aria-labelledby="related-heading">
            <SectionHead
              title={t("similarLots", { category: auction.category.name })}
              action={{
                label: t("backToAuctions"),
                href: `/auctions?category=${auction.category.slug}`,
              }}
            />
            <div className="mt-12 grid gap-x-8 gap-y-14 sm:grid-cols-2 lg:grid-cols-4">
              {relatedLots.map((lot) => (
                <AuctionCatalogueItem
                  key={lot.id}
                  auction={lot}
                  serverTime={related.serverTime}
                  scale="standard"
                />
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </>
  );
}

function SpecRow({ label, value }: { label: ReactNode; value: ReactNode }) {
  return (
    <div className="border-line flex items-baseline justify-between gap-8 border-b py-3.5">
      <dt className="label-sm text-dim">{label}</dt>
      <dd className="text-right text-sm">{value}</dd>
    </div>
  );
}

/** Server-side bridge to the translated condition label. */
async function ConditionLabel({ condition }: { condition: string }) {
  const t = await getTranslations("enums");
  return <>{t(`condition.${condition}`)}</>;
}
