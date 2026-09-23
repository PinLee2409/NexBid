import { Info } from "lucide-react";
import type { Metadata } from "next";
import { getFormatter, getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";

import { Container } from "@/components/layout/container";
import { PageHeader } from "@/components/layout/page-header";

/** The four documents the footer links to. */
const LEGAL_PAGES = {
  terms: "terms",
  privacy: "privacy",
  "buyer-protection": "buyerProtection",
  cookies: "cookies",
} as const;

type LegalSlug = keyof typeof LEGAL_PAGES;

export function generateStaticParams() {
  return Object.keys(LEGAL_PAGES).map((slug) => ({ slug }));
}

export async function generateMetadata(
  props: PageProps<"/legal/[slug]">,
): Promise<Metadata> {
  const { slug } = await props.params;
  const key = LEGAL_PAGES[slug as LegalSlug];
  if (!key) return {};

  const t = await getTranslations("legal");
  return { title: t(key) };
}

export default async function LegalPage(props: PageProps<"/legal/[slug]">) {
  const { slug } = await props.params;
  const key = LEGAL_PAGES[slug as LegalSlug];

  if (!key) notFound();

  const [t, tn, format] = await Promise.all([
    getTranslations("legal"),
    getTranslations("nav"),
    getFormatter(),
  ]);

  return (
    <Container className="py-10 lg:py-16">
      <div className="max-w-2xl">
        <PageHeader
          title={t(key)}
          description={t("lastUpdated", {
            date: format.dateTime(new Date("2026-01-15"), { dateStyle: "long" }),
          })}
          breadcrumbs={[{ label: tn("home"), href: "/" }, { label: t(key) }]}
        />

        <div className="border-line mt-10 flex gap-4 border-t pt-6">
          <Info className="text-muted-foreground mt-0.5 size-5 shrink-0" aria-hidden="true" />
          <div>
            <p className="label">{t("demoNoticeTitle")}</p>
            <p className="text-muted-foreground mt-1 text-sm leading-relaxed">
              {t("demoNoticeBody")}
            </p>
          </div>
        </div>
      </div>
    </Container>
  );
}
