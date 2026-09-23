import { SearchX, SlidersHorizontal } from "lucide-react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { AuctionToolbar } from "@/components/auction/auction-toolbar";
import { FilterSidebar } from "@/components/auction/filter-sidebar";
import { EmptyState } from "@/components/common/empty-state";
import { Pagination } from "@/components/common/pagination";
import { Container } from "@/components/layout/container";
import { AuctionCatalogueItem } from "@/components/nexbid/catalogue-item";
import {
  buildAuctionSearchParams,
  parseAuctionQuery,
} from "@/lib/auction-query";
import { listAuctions, listCategories } from "@/services/auction-service";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("auctions");
  return { title: t("title"), description: t("subtitle") };
}

export const dynamic = "force-dynamic";

/**
 * The full index. Where the home page curates, this page lists — so it opens
 * on the catalogue title and the count, and hands the rest of the screen to
 * the lots themselves.
 */
export default async function AuctionsPage(props: PageProps<"/auctions">) {
  const searchParams = await props.searchParams;
  const query = parseAuctionQuery(searchParams);

  const [result, categories, t] = await Promise.all([
    listAuctions(query),
    listCategories(),
    getTranslations("auctions"),
  ]);

  const isSearch = Boolean(query.search);

  return (
    <Container className="py-12 lg:py-20">
      <header className="border-line border-b pb-8">
        <h1 className="display text-[clamp(2.5rem,7vw,6rem)]">{t("title")}</h1>
        <p className="text-muted-foreground mt-4 max-w-xl text-[15px] leading-relaxed">
          {t("subtitle")}
        </p>
      </header>

      <div className="mt-10 grid gap-10 lg:grid-cols-[236px_minmax(0,1fr)] lg:gap-16">
        <FilterSidebar
          query={query}
          categories={categories}
          className="lg:sticky lg:top-24 lg:self-start"
        />

        <div className="min-w-0">
          <AuctionToolbar
            query={query}
            categories={categories}
            totalItems={result.totalItems}
          />

          {result.items.length === 0 ? (
            <EmptyState
              className="mt-10"
              icon={isSearch ? SearchX : SlidersHorizontal}
              title={
                isSearch
                  ? t("searchEmptyTitle", { query: query.search ?? "" })
                  : t("emptyTitle")
              }
              description={isSearch ? t("searchEmptyBody") : t("emptyBody")}
              action={{ label: t("browseAll"), href: "/auctions" }}
            />
          ) : (
            <>
              <div className="mt-10 grid gap-x-6 gap-y-12 sm:grid-cols-2 xl:grid-cols-3">
                {result.items.map((auction, index) => (
                  <AuctionCatalogueItem
                    key={auction.id}
                    auction={auction}
                    serverTime={result.serverTime}
                    scale="standard"
                    priority={index < 3}
                  />
                ))}
              </div>

              <Pagination
                className="mt-16"
                page={result.page}
                totalPages={result.totalPages}
                basePath="/auctions"
                params={Object.fromEntries(
                  buildAuctionSearchParams({ ...query, page: 1 }),
                )}
              />
            </>
          )}
        </div>
      </div>
    </Container>
  );
}
