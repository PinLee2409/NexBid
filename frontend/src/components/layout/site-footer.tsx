import { useTranslations } from "next-intl";
import Link from "next/link";

import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { ThemeSwitcher } from "@/components/layout/theme-switcher";
import { Logo } from "@/components/layout/logo";
import { FOOTER_COLUMNS, SOCIAL_LINKS } from "@/constants/site";

export function SiteFooter() {
  const t = useTranslations("footer");
  const tc = useTranslations("common");
  const year = new Date().getFullYear();

  return (
    <footer className="border-line border-t">
      <div className="mx-auto max-w-[1680px] px-5 sm:px-8">
        {/* The mark, set large, closes the page. */}
        <div className="border-line flex items-end justify-between gap-8 border-b py-12 lg:py-16">
          <Logo variant="mark" size={44} />
          <p className="label text-dim max-w-[22ch] text-right">
            {tc("description")}
          </p>
        </div>

        <div className="grid gap-10 py-12 lg:grid-cols-5 lg:gap-8">
          {FOOTER_COLUMNS.map((column) => (
            <nav key={column.titleKey} aria-label={t(column.titleKey)}>
              <h2 className="label-sm text-dim">{t(column.titleKey)}</h2>
              <ul className="mt-4 space-y-2.5">
                {column.links.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-muted-foreground hover:text-signal-text text-sm transition-colors"
                    >
                      {t(link.labelKey)}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}

          <div>
            <h2 className="label-sm text-dim">{tc("language")}</h2>
            <div className="mt-3 flex flex-col items-start gap-1">
              <LocaleSwitcher variant="full" className="-ml-2" />
              <ThemeSwitcher variant="full" className="-ml-2" />
            </div>

            <ul className="mt-6 flex flex-wrap gap-x-4 gap-y-2">
              {SOCIAL_LINKS.map((social) => (
                <li key={social.label}>
                  <a
                    href={social.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="label-sm text-muted-foreground hover:text-signal-text transition-colors"
                  >
                    {social.label}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="border-line flex flex-col gap-2 border-t py-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="label-sm text-dim">{t("rights", { year })}</p>
          <p className="label-sm text-dim">{t("serverNote")}</p>
        </div>
      </div>
    </footer>
  );
}
