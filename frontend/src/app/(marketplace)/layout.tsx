import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";
import { getHomeFeed } from "@/services/auction-service";

/**
 * Public shell. The header is transparent over the opening stage, so pages
 * that begin with a full-bleed lot pull themselves up underneath it.
 */
export default async function MarketplaceLayout({ children }: LayoutProps<"/">) {
  const { stats } = await getHomeFeed();

  return (
    <>
      <SiteHeader liveCount={stats.liveCount} />
      <main id="main" className="flex-1">
        {children}
      </main>
      <SiteFooter />
    </>
  );
}
