import { AuctionGridSkeleton } from "@/components/common/loading-skeleton";
import { Container } from "@/components/layout/container";
import { Skeleton } from "@/components/ui/skeleton";

/** Shown while the marketplace feed resolves. Mirrors the opening stage. */
export default function MarketplaceLoading() {
  return (
    <>
      {/* The stage is dark in both themes, so its placeholder is too. */}
      <section className="on-media bg-background -mt-16 flex min-h-[100svh] flex-col">
        <Container className="flex flex-1 flex-col pt-24 pb-10">
          <Skeleton className="h-3 w-32 rounded-none" />

          <div className="mt-auto space-y-5">
            <Skeleton className="h-[clamp(2.75rem,9vw,8.5rem)] w-full max-w-4xl rounded-none" />
            <Skeleton className="h-[clamp(2.75rem,9vw,8.5rem)] w-2/3 max-w-2xl rounded-none" />

            <div className="border-line flex flex-wrap gap-x-14 gap-y-8 border-t pt-7">
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="space-y-3">
                  <Skeleton className="h-2.5 w-20 rounded-none" />
                  <Skeleton className="h-10 w-32 rounded-none" />
                </div>
              ))}
            </div>
          </div>
        </Container>
      </section>

      <Container className="py-16 lg:py-24">
        <Skeleton className="h-12 w-72 rounded-none" />
        <AuctionGridSkeleton className="mt-12" />
      </Container>
    </>
  );
}
