import { API_URL } from "./support/stack";
import { data, expect, grouped, newLot, signIn, test } from "./support/test";

test("a bid reaches the other bidder's page without a reload", async ({ browser }) => {
  const lot = await newLot("Bidding lot", true);
  const alexContext = await browser.newContext();
  const saraContext = await browser.newContext();
  await signIn(alexContext, data.users.alex);
  await signIn(saraContext, data.users.sara);
  const alex = await alexContext.newPage();
  const sara = await saraContext.newPage();
  await Promise.all([alex.goto(`/auctions/${lot.id}`), sara.goto(`/auctions/${lot.id}`)]);

  const alexPanel = alex.locator("#bid-panel");
  const saraPanel = sara.locator("#bid-panel");
  // EN: Both pages are on the lot's live channel once the server counts two viewers.
  // VI: Cả hai trang đã vào kênh realtime của lô khi server đếm được hai người xem.
  await expect
    .poll(async () => (await (await fetch(`${API_URL}/api/auctions/${lot.id}`)).json()).data.viewerCount)
    .toBe(2);

  // EN: Alex bids the minimum; the confirmation step is part of the flow.
  // VI: Alex trả mức tối thiểu; bước xác nhận là một phần của luồng.
  const amount = Number(await alexPanel.locator("#terminal-bid").inputValue());
  await alexPanel.getByRole("button", { name: "Place bid" }).click();
  await alex.getByRole("button", { name: "Confirm bid" }).click();
  await expect(alexPanel).toContainText("You are currently the highest bidder");

  // EN: Sara's page moves on by itself: a new minimum, and Alex's bid in the feed.
  // VI: Trang của Sara tự cập nhật: mức tối thiểu mới, và bid của Alex trong danh sách.
  await expect(saraPanel).toContainText(grouped(amount + 100_000));
  await expect(saraPanel).toContainText("ale***");

  // EN: Sara outbids, and Alex is told at once. / VI: Sara trả cao hơn, và Alex được báo ngay.
  await saraPanel.getByRole("button", { name: "Place bid" }).click();
  await sara.getByRole("button", { name: "Confirm bid" }).click();
  await expect(alexPanel).toContainText("You've been outbid.");
  await expect(alexPanel.getByRole("button", { name: "Bid again" })).toBeVisible();

  await alexContext.close();
  await saraContext.close();
});

test("a bid under the minimum is stopped before it is sent", async ({ context, page }) => {
  await signIn(context, data.users.alex);
  await page.goto(`/auctions/${(await newLot("Minimum lot", true)).id}`);

  const panel = page.locator("#bid-panel");
  const input = panel.getByLabel("Your bid");
  await expect(input).toBeEnabled();
  await input.fill("500000");

  await expect(panel.getByRole("alert")).toBeVisible();
  await expect(panel.getByRole("button", { name: "Place bid" })).toBeDisabled();
});
