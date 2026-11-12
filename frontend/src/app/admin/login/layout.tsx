import { ShieldCheck } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { Logo } from "@/components/layout/logo";

/**
 * EN: The admin console's own door: plainer than the marketplace sign-in, with nothing to browse to.
 * VI: Cửa riêng của trang quản trị: đơn giản hơn trang đăng nhập của sàn, không có gì để lướt sang.
 */
export default async function AdminLoginLayout({ children }: LayoutProps<"/admin/login">) {
  const t = await getTranslations("admin");

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="border-line flex items-center justify-between border-b px-6 py-5 lg:px-8">
        <div className="flex items-center gap-4">
          <Logo />
          <span className="label-sm text-dim border-line flex items-center gap-1.5 border-l pl-4">
            <ShieldCheck className="text-signal-text size-3.5" aria-hidden="true" />
            {t("console")}
          </span>
        </div>
        <LocaleSwitcher />
      </header>

      <main id="main" className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-[380px]">{children}</div>
      </main>
    </div>
  );
}
