import { data, expect, newLot, signIn, test } from "./support/test";

/**
 * EN: Typing without accents still finds an accented name, and the lot shows up in the seller's statistics.
 * VI: Gõ không dấu vẫn tìm ra tên có dấu, và lô hiện trong trang thống kê của người bán.
 */
test("a search without accents finds the lot, and the seller sees it in their statistics", async ({ page }) => {
  const lot = await newLot("Đồng hồ quả quýt", true);

  await page.goto(`/auctions?search=${encodeURIComponent(`dong ho qua quyt ${lot.name.split(" ").pop()}`)}`);
  await expect(page.getByRole("link", { name: lot.name }).first()).toBeVisible();

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/stats");
  await expect(page.getByRole("heading", { name: "Statistics", level: 1 })).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: lot.name })).toBeVisible();
});
