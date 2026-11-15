"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

import { AuctionCatalogueItem } from "@/components/nexbid/catalogue-item";
import { SectionHead } from "@/components/nexbid/section-head";
import { listLotCards, recordLotView } from "@/services/auction-service";
import type { AuctionSummary } from "@/types";

const KEY = "nexbid:recently-viewed";
const KEEP = 12;
const SHOWN = 4;

function remembered(): string[] {
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

/**
 * EN: Counts this visit for the seller's statistics, remembers the lot in this browser, and shows the last few other lots
 *     the visitor opened. Nothing is shown on a first visit.
 * VI: Đếm lượt xem này cho thống kê của người bán, nhớ lô trong trình duyệt này, và hiện vài lô khác người xem đã mở gần
 *     đây. Lần đầu ghé thì không hiện gì.
 */
export function RecentlyViewed({ auctionId, serverTime }: { auctionId: string; serverTime: string }) {
  const t = useTranslations("auction");
  const [lots, setLots] = useState<AuctionSummary[]>([]);

  useEffect(() => {
    void recordLotView(auctionId);
    const earlier = remembered().filter((id) => id !== auctionId);
    try {
      window.localStorage.setItem(KEY, JSON.stringify([auctionId, ...earlier].slice(0, KEEP)));
    } catch {
      // EN: Private windows may refuse storage; the list just stays empty. / VI: Cửa sổ ẩn danh có thể chặn lưu; danh sách chỉ để trống.
    }
    if (earlier.length === 0) return;
    let live = true;
    listLotCards(earlier).then(
      (found) => live && setLots(found.slice(0, SHOWN)),
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [auctionId]);

  if (lots.length === 0) return null;

  return (
    <section className="mt-20" aria-labelledby="recent-heading">
      <SectionHead id="recent-heading" title={t("recentlyViewed")} />
      <div className="mt-12 grid gap-x-8 gap-y-14 sm:grid-cols-2 lg:grid-cols-4">
        {lots.map((lot) => (
          <AuctionCatalogueItem key={lot.id} auction={lot} serverTime={serverTime} scale="standard" />
        ))}
      </div>
    </section>
  );
}
