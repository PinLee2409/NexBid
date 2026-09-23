import { getTranslations } from "next-intl/server";

import { WorkspaceSidebar } from "@/components/layout/workspace-sidebar";

/** Seller workspace shell — sidebar tool, not marketplace chrome. */
export default async function SellerLayout({ children }: LayoutProps<"/seller">) {
  const t = await getTranslations("seller");

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <WorkspaceSidebar variant="seller" title={t("workspace")} />
      <main id="main" className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">
        <div className="mx-auto max-w-[1100px]">{children}</div>
      </main>
    </div>
  );
}
