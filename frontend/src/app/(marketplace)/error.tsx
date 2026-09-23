"use client";

import { AlertTriangle } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { Container } from "@/components/layout/container";
import { Button } from "@/components/ui/button";

export default function MarketplaceError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("errors");
  const tc = useTranslations("common");

  useEffect(() => {
    // Replace with the real error reporter when observability lands (spec §34).
    console.error(error);
  }, [error]);

  return (
    <Container className="py-20">
      <EmptyState
        tone="error"
        icon={AlertTriangle}
        title={t("auctionRoomTitle")}
        description={t("genericBody")}
      >
        <Button onClick={reset}>{tc("tryAgain")}</Button>
      </EmptyState>
    </Container>
  );
}
