import { BadgeCheck, Star, Store } from "lucide-react";
import type { Metadata } from "next";
import { getFormatter, getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";

import { EmptyState } from "@/components/common/empty-state";
import { StarRating } from "@/components/common/star-rating";
import { Container } from "@/components/layout/container";
import { AuctionCatalogueItem } from "@/components/nexbid/catalogue-item";
import { FollowSellerButton } from "@/components/nexbid/follow-seller-button";
import { getSellerProfile } from "@/services/auction-service";

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params;
  const seller = await getSellerProfile(id);
  const t = await getTranslations("seller");
  return { title: seller ? seller.displayName : t("profileMissing") };
}

/**
 * EN: A seller's public page: their score overall and per aspect, followers, what is on sale and what sold lately, and
 *     the latest reviews.
 * VI: Trang công khai của người bán: điểm tổng và từng khía cạnh, người theo dõi, đang bán gì và gần đây đã bán gì, và
 *     các đánh giá mới nhất.
 */
export default async function SellerPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [seller, t, format] = await Promise.all([getSellerProfile(id), getTranslations("seller"), getFormatter()]);
  if (!seller) notFound();
  const score = seller.score;
  const aspects = [
    ["packaging", score.aspects.packaging],
    ["accuracy", score.aspects.accuracy],
    ["speed", score.aspects.speed],
  ] as const;

  return (
    <Container className="py-12 lg:py-20">
      <header className="border-line flex flex-wrap items-end justify-between gap-6 border-b pb-8">
        <div className="min-w-0">
          <p className="label-sm text-dim">{t("profileLabel")}</p>
          <h1 className="display mt-2 flex items-center gap-3 text-[clamp(2.25rem,6vw,4.5rem)] [overflow-wrap:anywhere]">
            {seller.displayName}
            {seller.verified ? <BadgeCheck className="text-signal-text size-8 shrink-0" aria-label={t("profileVerified")} /> : null}
          </h1>
          <p className="text-muted-foreground mt-3 text-sm">
            {t("profileSince", { date: format.dateTime(new Date(seller.memberSince), { month: "long", year: "numeric" }) })}
            {" · "}
            {t("profileFollowers", { count: seller.followers })}
            {" · "}
            {t("profileSales", { count: score.sales })}
          </p>
        </div>
        <FollowSellerButton sellerId={seller.id} returnTo={`/sellers/${seller.id}`} />
      </header>

      <section className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-4" aria-label={t("profileScore")}>
        <div>
          <p className="label-sm text-dim">{t("profileOverall")}</p>
          <p className="figure mt-2 flex items-center gap-2 text-3xl" data-testid="seller-average">
            <Star className="size-6 fill-current" aria-hidden="true" />
            {score.average === null ? "—" : format.number(score.average, { minimumFractionDigits: 1 })}
          </p>
          <p className="text-dim mt-1 text-xs">{t("profileReviews", { count: score.reviewCount })}</p>
        </div>
        {aspects.map(([key, value]) => (
          <div key={key}>
            <p className="label-sm text-dim">{t(`profileAspect.${key}`)}</p>
            <p className="figure mt-2 text-3xl">{value === null ? "—" : format.number(value, { minimumFractionDigits: 1 })}</p>
          </div>
        ))}
      </section>

      <section className="mt-16" aria-labelledby="on-sale-heading">
        <h2 id="on-sale-heading" className="display border-line border-b pb-4 text-2xl">
          {t("profileOnSale")}
        </h2>
        {seller.onSale.length === 0 ? (
          <div className="mt-8">
            <EmptyState icon={Store} title={t("profileNothingOnSale")} description={t("profileNothingOnSaleBody")} />
          </div>
        ) : (
          <div className="mt-8 grid grid-cols-[minmax(0,1fr)] gap-x-6 gap-y-12 sm:grid-cols-2 xl:grid-cols-3">
            {seller.onSale.map((lot) => (
              <AuctionCatalogueItem key={lot.id} auction={lot} scale="standard" />
            ))}
          </div>
        )}
      </section>

      {seller.sold.length > 0 ? (
        <section className="mt-16" aria-labelledby="sold-heading">
          <h2 id="sold-heading" className="display border-line border-b pb-4 text-2xl">
            {t("profileSold")}
          </h2>
          <div className="mt-8 grid grid-cols-[minmax(0,1fr)] gap-x-6 gap-y-12 sm:grid-cols-2 xl:grid-cols-4">
            {seller.sold.map((lot) => (
              <AuctionCatalogueItem key={lot.id} auction={lot} scale="standard" />
            ))}
          </div>
        </section>
      ) : null}

      <section className="mt-16" aria-labelledby="reviews-heading">
        <h2 id="reviews-heading" className="display border-line border-b pb-4 text-2xl">
          {t("profileLatestReviews")}
        </h2>
        {score.reviews.length === 0 ? (
          <p className="text-dim mt-6 text-sm">{t("profileNoReviews")}</p>
        ) : (
          <ul className="mt-6 space-y-6">
            {score.reviews.map((review) => (
              <li key={review.id} className="border-line border-l-2 pl-4">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <StarRating rating={review.rating} />
                  <span className="mono-figure text-dim text-[11px]">
                    {review.reviewer} · {format.dateTime(new Date(review.createdAt), { dateStyle: "medium" })}
                  </span>
                </div>
                {review.comment ? <p className="mt-1.5 text-sm leading-relaxed">{review.comment}</p> : null}
                {review.lotName ? <p className="text-muted-foreground mt-1 text-xs">{t("profileReviewOn", { lot: review.lotName })}</p> : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </Container>
  );
}
