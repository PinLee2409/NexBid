import { endLotNow, lapsePayment, login, placeBid } from "./support/stack";
import { data, expect, newLot, signIn, test } from "./support/test";

/**
 * EN: A second chance (spec §17): the winner never pays, the seller offers the lot to the runner-up, and the
 *     runner-up buys it at their own bid.
 * VI: Cơ hội thứ hai (spec §17): người thắng không bao giờ trả, người bán đề nghị lô cho người thứ hai, và người
 *     thứ hai mua nó với giá của chính mình.
 */
test("the runner-up buys a lot its winner left unpaid", async ({ page, openContext }) => {
  // EN: The expiry job runs every minute unless the stack was started with a shorter interval.
  // VI: Job hết hạn chạy mỗi phút, trừ khi stack được khởi động với chu kỳ ngắn hơn.
  test.setTimeout(180_000);

  const lot = await newLot("Second chance lot", true);
  await placeBid(lot.id, await login(data.users.alex));
  await placeBid(lot.id, await login(data.users.sara));

  endLotNow(lot.id);
  await expect.poll(() => lapsePayment(lot.id), { timeout: 30_000, intervals: [1_000] }).toBe(1);

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/orders");
  const sale = page.locator("li").filter({ hasText: lot.name });
  const offer = sale.getByRole("button", { name: "Offer to next bidder" });
  await expect(async () => {
    await page.reload();
    await expect(offer).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 90_000, intervals: [3_000] });

  await offer.click();
  await page.getByRole("dialog").getByRole("button", { name: "Send offer" }).click();
  await expect(page.getByText("Offer sent")).toBeVisible();
  await expect(sale.getByText(/^Offered at/)).toBeVisible();

  const alex = await openContext();
  await signIn(alex, data.users.alex);
  const wins = await alex.newPage();
  await wins.goto("/my-wins");
  const offered = wins
    .getByRole("region", { name: "Second-chance offers" })
    .locator("li")
    .filter({ hasText: lot.name });
  await offered.getByRole("button", { name: "Buy at my bid" }).click();
  await wins.getByRole("dialog").getByRole("button", { name: "Buy at my bid" }).click();
  await expect(wins.getByText("Offer accepted").first()).toBeVisible();

  // EN: The lot is theirs now, waiting for their payment. / VI: Lô giờ là của họ, chờ họ thanh toán.
  const won = wins.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(won.getByRole("link", { name: "Pay Now" })).toBeVisible();

  await page.reload();
  await expect(sale.getByText(/^Next bidder accepted at/)).toBeVisible();
});
