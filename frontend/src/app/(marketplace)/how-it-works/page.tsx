import {
  Bot,
  CreditCard,
  Gavel,
  Lock,
  Server,
  ShieldCheck,
  Timer,
} from "lucide-react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import Link from "next/link";

import { Container } from "@/components/layout/container";
import { Button } from "@/components/ui/button";
import { SITE } from "@/constants/site";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("howItWorks");
  return { title: t("title"), description: t("subtitle") };
}

const RULE_ICONS = [Gavel, Lock, ShieldCheck, Timer, Bot, Server] as const;

export default async function HowItWorksPage() {
  const t = await getTranslations("howItWorks");
  const th = await getTranslations("home");

  const rules = [1, 2, 3, 4, 5, 6] as const;
  const sellingSteps = [1, 2, 3, 4] as const;

  return (
    <>
      <section className="border-line border-b">
        <Container className="py-16 lg:py-24">
          <div className="max-w-3xl">
            <h1 className="display text-[clamp(2.5rem,7vw,6rem)]">{t("title")}</h1>
            <p className="text-muted-foreground mt-6 text-[17px] leading-relaxed">
              {t("subtitle")}
            </p>
          </div>
        </Container>
      </section>

      <Container className="py-16 lg:py-24">
        <section aria-labelledby="buying-heading">
          <h2 id="buying-heading" className="display text-[clamp(1.75rem,4vw,3rem)]">
            {t("buyingTitle")}
          </h2>

          <ol className="mt-10 grid gap-x-8 gap-y-10 md:grid-cols-3">
            {([1, 2, 3] as const).map((step) => (
              <li key={step} className="border-line border-t pt-5">
                <span className="mono-figure text-signal-text text-xs">
                  {String(step).padStart(2, "0")}
                </span>
                <h3 className="display mt-3 text-xl">{th(`step${step}Title`)}</h3>
                <p className="text-muted-foreground mt-3 text-sm leading-relaxed">
                  {th(`step${step}Body`)}
                </p>
              </li>
            ))}
          </ol>
        </section>

        <section id="rules" className="mt-20 scroll-mt-24" aria-labelledby="rules-heading">
          <h2 id="rules-heading" className="display text-[clamp(1.75rem,4vw,3rem)]">
            {t("rulesTitle")}
          </h2>

          <dl className="mt-10 grid gap-x-12 gap-y-10 sm:grid-cols-2">
            {rules.map((rule, index) => {
              const Icon = RULE_ICONS[index];
              return (
                <div key={rule}>
                  <dt className="label flex items-center gap-2.5">
                    <Icon className="text-signal-text size-4 shrink-0" aria-hidden="true" />
                    {t(`rule${rule}Title`)}
                  </dt>
                  <dd className="text-muted-foreground mt-3 text-sm leading-relaxed">
                    {t(`rule${rule}Body`)}
                  </dd>
                </div>
              );
            })}
          </dl>
        </section>

        <section
          id="payments"
          className="mt-20 scroll-mt-24"
          aria-labelledby="payments-heading"
        >
          <h2 id="payments-heading" className="display text-[clamp(1.75rem,4vw,3rem)]">
            {t("paymentsTitle")}
          </h2>
          <div className="border-line mt-8 flex gap-4 border-t pt-6">
            <CreditCard
              className="text-muted-foreground mt-0.5 size-5 shrink-0"
              aria-hidden="true"
            />
            <p className="text-muted-foreground max-w-2xl text-sm leading-relaxed">
              {t("paymentsBody")}
            </p>
          </div>
        </section>

        <section
          id="selling"
          className="mt-20 scroll-mt-24"
          aria-labelledby="selling-heading"
        >
          <h2 id="selling-heading" className="display text-[clamp(1.75rem,4vw,3rem)]">
            {t("sellingTitle")}
          </h2>

          <ol className="mt-8">
            {sellingSteps.map((step) => (
              <li
                key={step}
                className="border-line flex gap-5 border-t py-4 last:border-b"
              >
                <span className="mono-figure text-dim shrink-0 text-xs">
                  {String(step).padStart(2, "0")}
                </span>
                <p className="text-sm leading-relaxed">{t(`selling${step}`)}</p>
              </li>
            ))}
          </ol>
        </section>

        <section
          id="support"
          className="mt-20 scroll-mt-24"
          aria-labelledby="support-heading"
        >
          <h2 id="support-heading" className="display text-[clamp(1.75rem,4vw,3rem)]">
            {t("supportTitle")}
          </h2>
          <p className="text-muted-foreground mt-4 max-w-2xl text-sm leading-relaxed">
            {t("supportBody")}
          </p>
          <Button asChild variant="outline" className="mt-6">
            <a href={SITE.repositoryUrl} target="_blank" rel="noopener noreferrer">
              GitHub
            </a>
          </Button>
        </section>

        <section className="border-line mt-20 border-t pt-12">
          <h2 className="display text-[clamp(2rem,6vw,5rem)]">{t("ctaTitle")}</h2>
          <p className="text-muted-foreground mt-4 max-w-xl text-[15px] leading-relaxed">
            {t("ctaBody")}
          </p>
          <Button asChild size="lg" className="mt-8">
            <Link href="/auctions?sort=ENDING_SOON">{th("startBidding")}</Link>
          </Button>
        </section>
      </Container>
    </>
  );
}
