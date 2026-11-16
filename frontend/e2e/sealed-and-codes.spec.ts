import { API_URL, buyOutright, login, register, saveAddress } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: A sealed-bid lot from the bidder's side, and a seller's own code taken off the goods at checkout.
 * VI: Lô đấu giá kín nhìn từ phía người trả giá, và mã riêng của người bán được trừ vào tiền hàng lúc thanh toán.
 */
async function freshBuyer(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Code Buyer");
  return email;
}

test("a sealed bid is placed without showing anyone's price", async ({ page }) => {
  const lot = await newLot("Sealed lot", true, { sealed: true });
  await signIn(page.context(), data.users.alex);
  await page.goto(`/auctions/${lot.id}`);

  await expect(page.getByText("Sealed-bid lot")).toBeVisible();
  const panel = page.locator("#bid-panel");
  await panel.locator("#terminal-bid").fill("1300000");
  await panel.getByRole("button", { name: "Place bid" }).click();
  await page.getByRole("button", { name: "Confirm bid" }).click();
  await expect(page.getByText("Your sealed bid is in")).toBeVisible();
  // EN: The price on show is still the start. / VI: Giá hiển thị vẫn là giá khởi điểm.
  await expect(page.getByText("₫1,000,000").first()).toBeVisible();

  await page.goto("/my-bids");
  await expect(page.getByRole("main").locator("li").filter({ hasText: lot.name })).toContainText("Sealed bid placed");
});

test("a seller's code comes off the goods at checkout", async ({ page }) => {
  const seller = await login(data.users.seller);
  const code = `E2E-${Date.now().toString(36).toUpperCase()}`;
  const created = await fetch(`${API_URL}/api/seller/coupons`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${seller.token}` },
    body: JSON.stringify({ code, amountOff: 200000, maxUses: 5 }),
  });
  expect(created.ok).toBe(true);

  const lot = await newLot("Code lot", true, { buyNowPrice: 2_000_000 });
  const email = await freshBuyer("code-buyer");
  const buyer = await login(email);
  await buyOutright(lot.id, buyer);
  await saveAddress(buyer);

  await signIn(page.context(), email);
  await page.goto("/payments");
  await page.getByRole("main").locator("li").filter({ hasText: lot.name })
    .getByRole("button", { name: "Pay with VNPay" }).click();
  const dialog = page.getByRole("dialog", { name: "Check the total" });
  await dialog.getByLabel("Seller's discount code").fill(code);
  await dialog.getByLabel("Seller's discount code").press("Enter");
  await expect(dialog.getByTestId("payment-coupon")).toHaveText("−₫200,000");
  await expect(dialog.getByTestId("payment-total")).toHaveText("₫1,850,000");
});
