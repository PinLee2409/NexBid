"use client";

import { Truck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useState } from "react";

import { formatCurrency } from "@/lib/format";
import { useSession } from "@/services/session-service";
import { getShippingEstimate, type ShippingEstimate } from "@/services/shipping-service";
import type { Locale } from "@/i18n/config";

/**
 * EN: "Shipping to Ba Đình, Hà Nội: about 96,900₫" on a lot page, under the specs. GHN's fee, charged with the item at checkout.
 *     Shows nothing when GHN is off, the seller has no GHN address, or GHN did not answer; asks for an address when the
 *     viewer has none. Asked again when the viewer signs in or out.
 * VI: "Phí ship về Ba Đình, Hà Nội: khoảng 96.900₫" trên trang lô, dưới phần thông số. Phí của GHN, thu cùng tiền hàng khi thanh toán.
 *     Không hiện gì khi GHN tắt, người bán chưa có địa chỉ GHN, hoặc GHN không trả lời; mời thêm địa chỉ khi người xem
 *     chưa có. Hỏi lại khi người xem đăng nhập hoặc đăng xuất.
 */
export function ShippingEstimateRow({ auctionId }: { auctionId: string }) {
  const t = useTranslations("auction");
  const locale = useLocale() as Locale;
  const { user, hydrated } = useSession();
  const [estimate, setEstimate] = useState<ShippingEstimate | null>(null);
  const viewer = user?.id ?? null;

  useEffect(() => {
    if (!hydrated) return;
    let live = true;
    getShippingEstimate(auctionId).then(
      (found) => live && setEstimate(found),
      () => live && setEstimate(null),
    );
    return () => {
      live = false;
    };
  }, [auctionId, hydrated, viewer]);

  if (!estimate || (estimate.state !== "READY" && estimate.state !== "NEEDS_ADDRESS")) return null;

  return (
    <div className="border-line flex items-baseline justify-between gap-8 border-b py-3.5">
      <dt className="label-sm text-dim inline-flex items-center gap-1.5">
        <Truck className="size-3.5" aria-hidden="true" />
        {t("shippingEstimate")}
      </dt>
      <dd className="text-right text-sm">
        {estimate.state === "READY" && estimate.fee !== null ? (
          <>
            <span>
              {t("shippingEstimateTo", { place: [estimate.district, estimate.province].filter(Boolean).join(", ") })}{" "}
              <span className="mono-figure text-foreground">{formatCurrency(estimate.fee, locale)}</span>
            </span>
            <span className="text-dim mt-0.5 block text-[11px]">
              {estimate.defaultParcel ? t("shippingEstimateDefault") : t("shippingEstimateNote")}
            </span>
          </>
        ) : (
          <Link
            href={user ? "/profile#address-heading" : `/login?redirect=${encodeURIComponent(`/auctions/${auctionId}`)}`}
            className="text-signal-text underline-offset-4 hover:underline"
          >
            {user ? t("shippingEstimateAddAddress") : t("shippingEstimateSignIn")}
          </Link>
        )}
      </dd>
    </div>
  );
}
