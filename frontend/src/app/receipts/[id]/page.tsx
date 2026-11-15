import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { Receipt } from "@/components/account/receipt";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("receipt");
  return { title: t("title"), robots: { index: false } };
}

/**
 * EN: A printable receipt for one order — the buyer's (what they paid) or, with ?as=seller, the seller's statement
 *     (what they are paid). Outside the site's frame so it prints clean; "Save as PDF" in the print dialog makes the PDF.
 * VI: Biên nhận in được cho một đơn — của người mua (số đã trả) hoặc, với ?as=seller, bảng kê của người bán (số được
 *     nhận). Nằm ngoài khung trang để in gọn; "Lưu dưới dạng PDF" trong hộp thoại in tạo ra file PDF.
 */
export default async function ReceiptPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ as?: string }>;
}) {
  const { id } = await params;
  const { as } = await searchParams;
  return <Receipt orderId={id} side={as === "seller" ? "seller" : "buyer"} />;
}
