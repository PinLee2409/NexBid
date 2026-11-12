import { ShieldOff } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { RoleGate } from "@/components/auth/role-gate";
import { EmptyState } from "@/components/common/empty-state";
import { WorkspaceSidebar } from "@/components/layout/workspace-sidebar";

/** Admin console shell. Only admins see it — the sidebar included. */
export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const t = await getTranslations("admin");

  const denied = (
    <main id="main" className="mx-auto flex min-h-dvh w-full max-w-[1100px] items-center px-4 sm:px-6">
      <EmptyState
        icon={ShieldOff}
        title={t("deniedTitle")}
        description={t("deniedBody")}
        action={{ label: t("deniedAction"), href: "/" }}
        className="w-full"
      />
    </main>
  );

  return (
    <RoleGate role="ADMIN" signInPage="/admin/login" denied={denied}>
      <div className="flex min-h-dvh flex-col lg:flex-row">
        <WorkspaceSidebar variant="admin" title={t("console")} />
        <main id="main" className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">
          <div className="mx-auto max-w-[1100px]">{children}</div>
        </main>
      </div>
    </RoleGate>
  );
}
