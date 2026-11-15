import { buyOutright, login, pay, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: A paid order has a printable receipt for the buyer and a statement for the seller.
 * VI: Đơn đã thanh toán có biên nhận in được cho người mua và bảng kê cho người bán.
 */
test("a paid order has a receipt for the buyer and a statement for the seller", async ({ page, openContext }) => {
  const email = `${unique("receipt").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Receipt Buyer");
  const lot = await newLot("Receipt", true, { buyNowPrice: 2_000_000 });
  const buyer = await login(email);
  await buyOutright(lot.id, buyer);
  const orderId = await pay(lot.id, buyer, await login(data.users.seller));

  await signIn(page.context(), email);
  await page.goto("/orders");
  await page.getByRole("main").locator("li").filter({ hasText: lot.name }).getByRole("link", { name: "Receipt", exact: true }).click();
  const receipt = page.getByTestId("receipt");
  await expect(receipt.getByRole("heading", { name: "Receipt" })).toBeVisible();
  await expect(receipt).toContainText(lot.name);
  await expect(receipt).toContainText("Paid through VNPay");
  await expect(page.getByRole("button", { name: "Print or save as PDF" })).toBeVisible();

  const seller = await openContext();
  await signIn(seller, data.users.seller);
  const statement = await seller.newPage();
  await statement.goto(`/receipts/${orderId}?as=seller`);
  await expect(statement.getByRole("heading", { name: "Sale statement" })).toBeVisible();
  await expect(statement.getByTestId("receipt")).toContainText("The amount owed appears once the buyer confirms receipt.");
});

/**
 * EN: An admin creates a shipping voucher; the calendar and the browser-notification setting are there for everyone.
 * VI: Admin tạo voucher phí ship; lịch đấu giá và phần cài đặt thông báo trình duyệt có sẵn cho mọi người.
 */
test("an admin creates a voucher, and the calendar and push settings open", async ({ page }) => {
  const code = `E2E-${Date.now().toString(36).toUpperCase()}`;
  await signIn(page.context(), data.users.admin);
  await page.goto("/admin/promos");
  await expect(page.getByRole("heading", { name: "Vouchers", level: 1 })).toBeVisible();
  await page.getByRole("button", { name: "New voucher" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Code").fill(code);
  await dialog.getByRole("button", { name: "Create" }).click();
  await expect(page.getByTestId("promo-list")).toContainText(code);

  await page.goto("/calendar");
  await expect(page.getByRole("heading", { name: "Auction calendar", level: 1 })).toBeVisible();
  await expect(page.getByTestId("calendar-range")).toBeVisible();

  await page.goto("/profile");
  await expect(page.getByRole("heading", { name: "Browser notifications" })).toBeVisible();
  await expect(page.getByTestId("push-settings")).toBeVisible();
});
