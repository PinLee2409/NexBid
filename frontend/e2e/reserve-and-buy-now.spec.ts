import { endLotNow, login, placeBid } from "./support/stack";
import { data, expect, newLot, signIn, test } from "./support/test";

/**
 * EN: Buy-now and the hidden reserve, from the bidder's and the seller's side.
 * VI: Mua ngay và giá sàn ẩn, nhìn từ phía người trả giá và phía người bán.
 */
test("a buyer takes a lot outright before anyone bids", async ({ page }) => {
  const lot = await newLot("Buy now lot", true, { buyNowPrice: 3_000_000 });
  await signIn(page.context(), data.users.alex);
  await page.goto(`/auctions/${lot.id}`);

  await page.getByRole("button", { name: /^Buy now · / }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Buy now", exact: true }).click();
  await expect(page.getByText("It's yours")).toBeVisible();

  // EN: The lot is theirs, waiting for payment like any win. / VI: Lô là của họ, chờ thanh toán như mọi lần thắng.
  await page.goto("/my-wins");
  const won = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(won.getByRole("link", { name: "Pay Now" })).toBeVisible();
});

test("a lot that ends below its reserve waits for the seller, who may sell it anyway", async ({ page, openContext }) => {
  test.setTimeout(90_000);
  const lot = await newLot("Reserve lot", true, { reservePrice: 5_000_000 });

  // EN: Visitors learn only that there is a reserve and that it is not met. / VI: Khách chỉ biết có giá sàn và chưa đạt.
  await page.goto(`/auctions/${lot.id}`);
  await expect(page.getByText("Reserve not met").first()).toBeVisible();
  await expect(page.getByText("5,000,000")).toHaveCount(0);

  await placeBid(lot.id, await login(data.users.alex));
  endLotNow(lot.id);

  const seller = await openContext();
  await signIn(seller, data.users.seller);
  const mine = await seller.newPage();
  const decision = mine
    .locator("li")
    .filter({ hasText: lot.name })
    .locator("xpath=following-sibling::li[1]");
  // EN: The scheduler closes the lot within seconds; the shared seller's list takes a while to load.
  // VI: Scheduler đóng lô trong vài giây; danh sách của người bán dùng chung cần một lúc để tải.
  await expect(async () => {
    await mine.goto("/seller/auctions");
    await expect(decision.getByText("Below your reserve", { exact: true })).toBeVisible({ timeout: 15_000 });
  }).toPass({ timeout: 60_000 });

  await decision.getByRole("button", { name: /^Sell at / }).click();
  await expect(mine.getByText("Sold to the highest bidder")).toBeVisible();

  await signIn(page.context(), data.users.alex);
  await page.goto("/my-wins");
  const won = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(won.getByRole("link", { name: "Pay Now" })).toBeVisible();
});
