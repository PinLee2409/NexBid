import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/**
 * Placeholders hold the exact geometry of what replaces them, so nothing on
 * the page moves when the data lands. Square, like everything else here.
 */
export function AuctionCardSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("", className)}>
      <Skeleton className="aspect-[4/5] rounded-none" />
      <div className="mt-4 space-y-3">
        <Skeleton className="h-2.5 w-20 rounded-none" />
        <Skeleton className="h-5 w-4/5 rounded-none" />
        <div className="flex items-end justify-between pt-1">
          <Skeleton className="h-6 w-24 rounded-none" />
          <Skeleton className="h-3 w-14 rounded-none" />
        </div>
      </div>
    </div>
  );
}

export function AuctionGridSkeleton({
  count = 8,
  className,
}: {
  count?: number;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "grid gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4",
        className,
      )}
    >
      {Array.from({ length: count }).map((_, index) => (
        <AuctionCardSkeleton key={index} />
      ))}
    </div>
  );
}

export function AuctionListRowSkeleton() {
  return (
    <div className="border-line flex items-center gap-6 border-t py-5 last:border-b">
      <Skeleton className="hidden h-3 w-10 shrink-0 rounded-none sm:block" />
      <Skeleton className="size-16 shrink-0 rounded-none" />
      <div className="flex-1 space-y-2.5">
        <Skeleton className="h-2.5 w-20 rounded-none" />
        <Skeleton className="h-5 w-1/2 rounded-none" />
        <Skeleton className="h-2.5 w-1/4 rounded-none" />
      </div>
      <Skeleton className="h-6 w-24 shrink-0 rounded-none" />
    </div>
  );
}

export function TextBlockSkeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="space-y-2.5">
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton
          key={index}
          className={cn(
            "h-3 rounded-none",
            index === lines - 1 ? "w-2/3" : "w-full",
          )}
        />
      ))}
    </div>
  );
}

export function StatTileSkeleton() {
  return (
    <div className="border-line space-y-3 border-t pt-4">
      <Skeleton className="h-2.5 w-24 rounded-none" />
      <Skeleton className="h-8 w-16 rounded-none" />
    </div>
  );
}
