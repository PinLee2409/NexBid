"use client";

import { Store } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

import { RoleGate } from "@/components/auth/role-gate";
import { EmptyState } from "@/components/common/empty-state";

/**
 * EN: The seller workspace for someone who cannot sell yet: say why and where to ask, instead of a page of refusals.
 *     Signed out, the reader is sent to sign in and brought back.
 * VI: Khu vực người bán với người chưa được bán: nói lý do và chỗ gửi yêu cầu, thay vì cả trang toàn lỗi từ chối.
 *     Chưa đăng nhập thì được đưa đi đăng nhập rồi quay lại.
 */
export function SellerGate({ children }: { children: ReactNode }) {
  const t = useTranslations("becomeSeller");

  return (
    <RoleGate
      role="SELLER"
      denied={
        <EmptyState
          icon={Store}
          title={t("gateTitle")}
          description={t("gateBody")}
          action={{ label: t("title"), href: "/become-seller" }}
        />
      }
    >
      {children}
    </RoleGate>
  );
}
