"use client";

import { BadgeCheck, Columns3, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, type ReactNode } from "react";

import { CategoryName } from "@/components/common/category-name";
import { EmptyState } from "@/components/common/empty-state";
import { AuctionListRowSkeleton } from "@/components/common/loading-skeleton";
import { ProductPhoto } from "@/components/common/product-photo";
import { useAsyncData } from "@/hooks/use-async-data";
import { useEnumLabels } from "@/hooks/use-labels";
import { useMoney } from "@/hooks/use-money";
import { toggleCompare, useCompareList } from "@/lib/compare-store";
import { getAuction, getSellerReputation } from "@/services/auction-service";
import { getShippingEstimate } from "@/services/shipping-service";

/**
 * EN: Two or three lots side by side: price, buy-now, time left, condition, shipping to the reader and the seller's
 *     score. The lots come from the link (so it can be shared) or else from this browser's comparison tray.
 * VI: Hai hoặc ba lô cạnh nhau: giá, mua ngay, thời gian còn lại, tình trạng, phí ship tới người xem và điểm người bán.
 *     Các lô lấy từ link (để chia sẻ được) hoặc từ khay so sánh của trình duyệt này.
 */
export default function ComparePage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-[1400px] px-5 py-16">
          <AuctionListRowSkeleton />
        </div>
      }
    >
      <Comparison />
    </Suspense>
  );
}

async function load(id: string) {
  const auction = await getAuction(id);
  if (!auction) return null;
  const [shipping, seller] = await Promise.all([
    getShippingEstimate(id).catch(() => null),
    getSellerReputation(auction.seller.id, 0).catch(() => null),
  ]);
  return { auction, shipping, seller };
}

function Comparison() {
  const t = useTranslations("compare");
  const money = useMoney();
  const format = useFormatter();
  const labels = useEnumLabels();
  const params = useSearchParams();
  const tray = useCompareList();
  const ids = (params.get("ids")?.split(",").filter(Boolean) ?? tray).slice(
    0,
    3,
  );
  const { state } = useAsyncData(`compare-${ids.join(",")}`, () =>
    Promise.all(ids.map(load)),
  );

  const lots =
    state.status === "success" ? state.data.filter((lot) => lot !== null) : [];

  return (
    <div className="mx-auto max-w-[1400px] px-5 pt-28 pb-20 sm:px-8">
      <h1 className="display text-[clamp(2rem,5vw,4rem)]">{t("title")}</h1>
      <p className="text-muted-foreground mt-2 max-w-2xl">{t("lead")}</p>

      {ids.length === 0 ? (
        <EmptyState
          icon={Columns3}
          title={t("emptyTitle")}
          description={t("emptyBody")}
          action={{ label: t("browse"), href: "/auctions" }}
          className="mt-10"
        />
      ) : state.status === "loading" ? (
        <div className="mt-10">
          <AuctionListRowSkeleton />
        </div>
      ) : (
        <div className="mt-10 overflow-x-auto" data-testid="comparison" tabIndex={0} role="region" aria-label={t("title")}>
          <table className="w-full min-w-[640px] table-fixed text-sm">
            <thead>
              <tr>
                <th className="w-40" />
                {lots.map(({ auction }) => (
                  <th
                    key={auction.id}
                    className="px-3 pb-4 text-left align-top font-normal"
                  >
                    <div className="bg-surface relative aspect-[4/3] overflow-hidden">
                      <ProductPhoto
                        src={auction.product.images[0]?.url}
                        alt={auction.product.name}
                        fill
                        sizes="(max-width: 768px) 50vw, 25vw"
                        className="object-cover"
                      />
                    </div>
                    <div className="mt-3 flex items-start justify-between gap-2">
                      <Link
                        href={`/auctions/${auction.id}`}
                        className="display hover:text-signal-text min-w-0 text-lg [overflow-wrap:anywhere]"
                      >
                        {auction.product.name}
                      </Link>
                      <button
                        type="button"
                        onClick={() => toggleCompare(auction.id)}
                        aria-label={t("remove", { name: auction.product.name })}
                        className="text-dim hover:text-foreground shrink-0"
                      >
                        <X className="size-4" />
                      </button>
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <Row label={t("status")}>
                {lots.map(({ auction }) =>
                  labels.auctionStatus(auction.status),
                )}
              </Row>
              <Row label={t("current")}>
                {lots.map(({ auction }) =>
                  money.currency(auction.currentPrice),
                )}
              </Row>
              <Row label={t("buyNow")}>
                {lots.map(({ auction }) =>
                  auction.buyNowPrice !== null && auction.bidCount === 0
                    ? money.currency(auction.buyNowPrice)
                    : "—",
                )}
              </Row>
              <Row label={t("bids")}>
                {lots.map(({ auction }) => String(auction.bidCount))}
              </Row>
              <Row label={t("ends")}>
                {lots.map(({ auction }) =>
                  format.dateTime(new Date(auction.endTime), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  }),
                )}
              </Row>
              <Row label={t("condition")}>
                {lots.map(({ auction }) =>
                  labels.condition(auction.product.condition),
                )}
              </Row>
              <Row label={t("category")}>
                {lots.map(({ auction }) => (
                  <CategoryName key={auction.id} category={auction.category} />
                ))}
              </Row>
              <Row label={t("shipping")}>
                {lots.map(({ auction, shipping }) =>
                  auction.freeShipping
                    ? t("free")
                    : shipping?.fee !== null && shipping?.fee !== undefined
                      ? money.currency(shipping.fee)
                      : t("shippingUnknown"),
                )}
              </Row>
              <Row label={t("seller")}>
                {lots.map(({ auction, seller }) => (
                  <span
                    key={auction.id}
                    className="inline-flex flex-wrap items-center gap-1"
                  >
                    <Link
                      href={`/sellers/${auction.seller.id}`}
                      className="hover:text-signal-text underline-offset-4 hover:underline"
                    >
                      {auction.seller.displayName}
                    </Link>
                    {auction.seller.verified ? (
                      <BadgeCheck
                        className="text-signal-text size-3.5"
                        aria-label={t("verified")}
                      />
                    ) : null}
                    <span className="text-dim">
                      {seller?.average !== null && seller?.average !== undefined
                        ? t("score", {
                            average: seller.average,
                            count: seller.reviewCount,
                          })
                        : t("noReviews")}
                    </span>
                  </span>
                ))}
              </Row>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode[] }) {
  return (
    <tr className="border-line border-t">
      <th
        scope="row"
        className="label-sm text-dim py-3 pr-3 text-left align-top font-normal"
      >
        {label}
      </th>
      {children.map((cell, index) => (
        <td key={index} className="mono-figure px-3 py-3 align-top">
          {cell}
        </td>
      ))}
    </tr>
  );
}
