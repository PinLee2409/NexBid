import { AuctionGridSkeleton } from "@/components/common/loading-skeleton";
import { Container } from "@/components/layout/container";
import { Skeleton } from "@/components/ui/skeleton";

export default function AuctionsLoading() {
  return (
    <Container className="py-12 lg:py-20">
      <div className="border-line border-b pb-8">
        <Skeleton className="h-16 w-80 rounded-none" />
        <Skeleton className="mt-4 h-4 w-96 max-w-full rounded-none" />
      </div>

      <div className="mt-10 grid gap-10 lg:grid-cols-[236px_minmax(0,1fr)] lg:gap-16">
        <div className="hidden space-y-8 lg:block">
          {Array.from({ length: 4 }).map((_, group) => (
            <div key={group} className="border-line space-y-3 border-t pt-4">
              <Skeleton className="h-2.5 w-24 rounded-none" />
              {Array.from({ length: 4 }).map((_, row) => (
                <Skeleton key={row} className="h-3.5 w-full rounded-none" />
              ))}
            </div>
          ))}
        </div>

        <div>
          <div className="flex flex-wrap items-end gap-x-6 gap-y-3">
            <Skeleton className="h-11 w-full max-w-sm rounded-none" />
            <Skeleton className="h-11 w-[200px] rounded-none" />
          </div>
          <AuctionGridSkeleton
            className="mt-10 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3"
            count={6}
          />
        </div>
      </div>
    </Container>
  );
}
