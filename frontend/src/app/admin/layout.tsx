import { getTranslations } from "next-intl/server";

import { WorkspaceSidebar } from "@/components/layout/workspace-sidebar";

/** Admin console shell. */
export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const t = await getTranslations("admin");

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <WorkspaceSidebar variant="admin" title={t("console")} />
      <main id="main" className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">
        <div className="mx-auto max-w-[1100px]">{children}</div>
      </main>
    </div>
  );
}
