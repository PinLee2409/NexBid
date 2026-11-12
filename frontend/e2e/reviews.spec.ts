import { buyOutright, completeSale, login } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: Once the goods arrive, the buyer rates the seller, and every lot of that seller shows it.
 * VI: Khi hàng đã tới, người mua đánh giá người bán, và mọi lô của người bán đó đều hiện đánh giá.
 */
test("a buyer rates the seller once the goods arrive, and the seller's lots show it", async ({ page }) => {
  const lot = await newLot("Reviewed lot", true, { buyNowPrice: 2_000_000 });
  const alex = await login(data.users.alex);
  await buyOutright(lot.id, alex);
  await completeSale(lot.id, alex, await login(data.users.seller));
  const comment = unique("Well packed and exactly as described");

  await signIn(page.context(), data.users.alex);
  await page.goto("/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await row.getByRole("button", { name: "Rate seller" }).click();

  const dialog = page.getByRole("dialog");
  // EN: The radio is visually hidden; a person clicks the star, which is its label. / VI: Nút radio bị ẩn; người dùng bấm vào ngôi sao, chính là label của nó.
  const fourStars = dialog.getByRole("radio", { name: "4 out of 5 stars" });
  await dialog.locator("label", { has: fourStars }).click();
  await expect(fourStars).toBeChecked();
  await dialog.getByLabel("Comment").fill(comment);
  await dialog.getByRole("button", { name: "Post review" }).click();
  await expect(page.getByText("Review posted")).toBeVisible();
  await expect(row.getByText("Your review")).toBeVisible();

  // EN: Any other lot of the same seller carries the review. / VI: Bất kỳ lô nào khác của cùng người bán đều mang đánh giá đó.
  const another = await newLot("Seller reputation lot", true);
  await page.goto(`/auctions/${another.id}`);
  await expect(page.getByRole("heading", { name: "Latest reviews" })).toBeVisible();
  await expect(page.getByText(comment)).toBeVisible();
});
