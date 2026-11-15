import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { Container } from "@/components/layout/container";
import { AuctionCalendar } from "@/components/auction/auction-calendar";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("calendar");
  return { title: t("title"), description: t("subtitle") };
}

/**
 * EN: The auction calendar: lots opening in the coming weeks, day by day.
 * VI: Lịch đấu giá: các lô mở trong những tuần tới, theo từng ngày.
 */
export default async function CalendarPage() {
  const t = await getTranslations("calendar");
  return (
    <section>
      <Container className="py-16 lg:py-20">
        <h1 className="display text-[clamp(2.5rem,7vw,5rem)]">{t("title")}</h1>
        <p className="text-muted-foreground mt-4 max-w-2xl text-[17px] leading-relaxed">{t("subtitle")}</p>
        <AuctionCalendar />
      </Container>
    </section>
  );
}
