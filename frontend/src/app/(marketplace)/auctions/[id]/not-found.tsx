import { PackageX } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { EmptyState } from "@/components/common/empty-state";
import { Container } from "@/components/layout/container";

export default async function AuctionNotFound() {
  const t = await getTranslations("auction");
  const te = await getTranslations("errors");

  return (
    <Container className="py-20">
      <EmptyState
        icon={PackageX}
        title={t("notFoundTitle")}
        description={t("notFoundBody")}
        action={{ label: te("browseAuctions"), href: "/auctions" }}
        secondaryAction={{ label: te("backHome"), href: "/" }}
      />
    </Container>
  );
}
