"use client";

import { Store } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { hasRole, useSession } from "@/services/session-service";

/**
 * EN: The seller workspace for someone who cannot sell yet: say why and where to ask, instead of a page of refusals.
 * VI: Khu vực người bán với người chưa được bán: nói lý do và chỗ gửi yêu cầu, thay vì cả trang toàn lỗi từ chối.
 */
export function SellerGate({ children }: { children: ReactNode }) {
  const t = useTranslations("becomeSeller");
  const { user, hydrated } = useSession();

  if (hydrated && user && !hasRole(user, "SELLER")) {
    return (
      <EmptyState
        icon={Store}
        title={t("gateTitle")}
        description={t("gateBody")}
        action={{ label: t("title"), href: "/become-seller" }}
      />
    );
  }
  return children;
}
