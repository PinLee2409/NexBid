import { Gavel, Radio, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";

import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { Logo } from "@/components/layout/logo";

/**
 * Auth shell: the form on the left, a short statement of what the platform
 * guarantees on the right. No marketplace chrome — nothing to click away to.
 */
export default function AuthLayout({ children }: LayoutProps<"/">) {
  const t = useTranslations("auth");
  const th = useTranslations("home");

  const pillars = [
    { icon: ShieldCheck, key: "trust1" },
    { icon: Radio, key: "trust2" },
    { icon: Gavel, key: "trust4" },
  ] as const;

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col">
        <header className="flex items-center justify-between p-6 lg:p-8">
          <Logo />
          <LocaleSwitcher />
        </header>

        <main
          id="main"
          className="flex flex-1 items-center justify-center px-6 pb-12 lg:px-8"
        >
          <div className="w-full max-w-[380px]">{children}</div>
        </main>
      </div>

      {/* Brand panel — hidden on phones, where the form is the whole job. */}
      <aside className="bg-surface border-line relative hidden flex-col justify-center border-l p-12 lg:flex">
        <div className="max-w-md">
          <h2 className="display text-[clamp(2rem,3vw,3rem)]">
            {t("sideTitle")}
          </h2>
          <p className="text-muted-foreground mt-4 leading-relaxed">
            {t("sideBody")}
          </p>

          <dl className="mt-10 space-y-6">
            {pillars.map((pillar) => (
              <div key={pillar.key} className="flex gap-3.5">
                <span className="text-signal-text flex size-9 shrink-0 items-center justify-center">
                  <pillar.icon className="size-4" aria-hidden="true" />
                </span>
                <div>
                  <dt className="label">
                    {th(`${pillar.key}Title`)}
                  </dt>
                  <dd className="text-muted-foreground mt-1 text-sm leading-relaxed">
                    {th(`${pillar.key}Body`)}
                  </dd>
                </div>
              </div>
            ))}
          </dl>
        </div>
      </aside>
    </div>
  );
}
