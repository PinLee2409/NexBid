import { Compass } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { EmptyState } from "@/components/common/empty-state";
import { Container } from "@/components/layout/container";
import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";

export default async function NotFound() {
  const t = await getTranslations("errors");

  return (
    <>
      <SiteHeader />
      <main id="main" className="flex-1">
        <Container className="py-24">
          <EmptyState
            icon={Compass}
            title={t("notFoundTitle")}
            description={t("notFoundBody")}
            action={{ label: t("browseAuctions"), href: "/auctions" }}
            secondaryAction={{ label: t("backHome"), href: "/" }}
          />
        </Container>
      </main>
      <SiteFooter />
    </>
  );
}
