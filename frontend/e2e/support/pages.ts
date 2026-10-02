import AxeBuilder from "@axe-core/playwright";
import type { BrowserContext, Page } from "@playwright/test";

import { data, newLot, signIn } from "./test";

/** EN: Where next-themes keeps the chosen theme. / VI: Nơi next-themes giữ giao diện đã chọn. */
const THEME_STORAGE_KEY = "nexbid-theme";

export type Role = "visitor" | "buyer" | "seller" | "admin";
export const ROLES: Role[] = ["visitor", "buyer", "seller", "admin"];

/**
 * EN: Every page, grouped by who can open it. A live lot is made first so the home page and the lot page
 *     have something real to show.
 * VI: Mọi trang, nhóm theo người được mở. Tạo trước một lô đang mở để trang chủ và trang lô có nội dung thật.
 */
export async function pagesFor(role: Role): Promise<string[]> {
  const lot = await newLot("Page check lot", true);
  switch (role) {
    case "visitor":
      return ["/", "/auctions", `/auctions/${lot.id}`, "/how-it-works", "/legal/terms", "/login", "/register"];
    case "buyer":
      return [`/auctions/${lot.id}`, "/my-bids", "/my-wins", "/watchlist", "/notifications", "/orders", "/payments", "/profile", "/become-seller"];
    case "seller":
      return ["/seller/dashboard", "/seller/products", "/seller/products/create", "/seller/auctions", "/seller/auctions/create", "/seller/orders"];
    case "admin":
      return ["/admin", "/admin/auctions", "/admin/users", "/admin/sellers", "/admin/orders", "/admin/audit-logs"];
  }
}

/** EN: Signs the context in as the role, in the given theme. / VI: Đăng nhập context theo vai trò, với giao diện đã cho. */
export async function prepare(context: BrowserContext, role: Role, theme: "dark" | "light"): Promise<void> {
  await context.addInitScript(
    ([key, value]) => {
      if (window.location.protocol.startsWith("http")) window.localStorage.setItem(key, value);
    },
    [THEME_STORAGE_KEY, theme] as const,
  );
  const email = { visitor: null, buyer: data.users.alex, seller: data.users.seller, admin: data.users.admin }[role];
  if (email) await signIn(context, email);
}

/** EN: Opens a page and waits until its data has arrived. / VI: Mở một trang và chờ dữ liệu của nó về đủ. */
export async function open(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
}

/**
 * EN: WCAG 2.1 A and AA problems axe finds on the open page, one line each. Text marked data-decorative (the
 *     faint lot-number watermarks) is skipped: decoration has no contrast requirement (WCAG 1.4.3).
 * VI: Các lỗi WCAG 2.1 mức A và AA mà axe tìm thấy trên trang đang mở, mỗi lỗi một dòng. Chữ đánh dấu
 *     data-decorative (số lô chìm làm nền) được bỏ qua: trang trí không có yêu cầu tương phản (WCAG 1.4.3).
 */
export async function accessibilityProblems(page: Page, path: string): Promise<string[]> {
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .exclude("[data-decorative]")
    .analyze();
  return result.violations.map(
    (violation) =>
      `${path} — ${violation.id} (${violation.impact}): ${violation.nodes
        .slice(0, 3)
        .map((node) => node.target.join(" "))
        .join(", ")}`,
  );
}
