import { createLot, grantRoles, login, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/** EN: An account of its own, so a test that changes it never disturbs the shared ones. / VI: Tài khoản riêng, để test thay đổi nó không ảnh hưởng tài khoản dùng chung. */
async function freshAccount(label: string, role?: string): Promise<string> {
  const email = `${unique(label).replace(/\s+/g, ".").toLowerCase()}@nexbid.test`;
  await register(email, label);
  if (role) grantRoles([[email, role]]);
  return email;
}

/**
 * EN: Offers on a buy-now lot from both sides, search suggestions, the comparison page, a seller's vacation, and a
 *     narrower staff role in the admin console.
 * VI: Đề nghị trên lô có giá mua ngay từ cả hai phía, gợi ý tìm kiếm, trang so sánh, kỳ nghỉ của người bán, và một
 *     vai trò nhân viên hẹp hơn trong trang quản trị.
 */
test("a buyer's offer is countered by the seller and taken", async ({ page, openContext }) => {
  test.setTimeout(90_000);
  const lot = await newLot("Offer lot", true, { buyNowPrice: 3_000_000 });

  await signIn(page.context(), data.users.alex);
  await page.goto(`/auctions/${lot.id}`);
  const panel = page.getByTestId("offer-panel");
  await panel.getByLabel("Your offer (VND)").fill("1500000");
  await panel.getByRole("button", { name: "Send offer" }).click();
  await expect(panel.getByTestId("my-offer")).toContainText("is waiting for the seller");

  const seller = await openContext();
  await signIn(seller, data.users.seller);
  const offers = await seller.newPage();
  await offers.goto("/seller/offers");
  const row = offers.getByTestId("offer-row").filter({ hasText: lot.name });
  await row.getByRole("button", { name: "Counter" }).click();
  await row.getByLabel("Counter price (VND)").fill("2000000");
  await row.getByRole("button", { name: "Send" }).click();
  await expect(row).toContainText("Countered");

  await page.goto("/offers");
  const mine = page.getByTestId("offer-row").filter({ hasText: lot.name });
  await mine.getByRole("button", { name: "Buy at this price" }).click();
  await expect(mine).toContainText("Accepted");

  await page.goto("/my-wins");
  const won = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(won.getByRole("link", { name: "Pay Now" })).toBeVisible();
});

test("search suggests lots as you type and lots can be compared", async ({ page }) => {
  const first = await newLot("Compare camera", true);
  const second = await newLot("Compare lens", true);

  await page.goto("/");
  await page.getByRole("button", { name: "Search" }).first().click();
  await page.getByRole("dialog").locator("input").fill(first.name.slice(0, 18));
  await expect(page.getByRole("dialog").getByRole("link", { name: new RegExp(first.name) })).toBeVisible();
  await page.keyboard.press("Escape");

  for (const lot of [first, second]) {
    await page.goto(`/auctions/${lot.id}`);
    await page.getByRole("button", { name: "Compare", exact: true }).click();
  }
  await page.getByTestId("compare-tray").getByRole("link").click();
  const table = page.getByTestId("comparison");
  await expect(table).toContainText(first.name);
  await expect(table).toContainText(second.name);
});

test("a seller on vacation is shown as away", async ({ page }) => {
  // EN: A seller of its own: a vacation stops submissions, which the shared seller needs. / VI: Người bán riêng: kỳ nghỉ chặn việc gửi duyệt, thứ người bán dùng chung cần.
  const email = await freshAccount("Away Seller", "SELLER");
  const lot = await createLot(await login(email), await login(data.users.admin), unique(`E2E ${data.run} Away lot`), true);
  await signIn(page.context(), email);
  await page.goto("/seller/settings");
  const vacation = page.getByTestId("vacation");
  await vacation.getByLabel("Note for buyers").fill(`Back soon ${data.run}`);
  await vacation.getByRole("button", { name: "Start vacation" }).click();
  await expect(vacation.getByText(/You are on vacation until/)).toBeVisible();

  await page.goto(`/auctions/${lot.id}`);
  await expect(page.getByTestId("seller-away")).toContainText(`Back soon ${data.run}`);

  await page.goto("/seller/settings");
  await page.getByTestId("vacation").getByRole("button", { name: "I'm back" }).click();
  await expect(page.getByTestId("vacation").getByText(/You are on vacation until/)).toHaveCount(0);
});

test("an admin gives a buyer the finance role, which opens only the money pages", async ({ page, openContext }) => {
  const email = await freshAccount("Finance Clerk");
  await signIn(page.context(), data.users.admin);
  await page.goto("/admin/staff");
  await page.getByLabel("Account email").fill(email);
  await page.getByRole("checkbox", { name: "Finance", exact: true }).click();
  await page.getByRole("checkbox", { name: "Support", exact: true }).click();
  await page.getByRole("button", { name: "Add to staff" }).click();
  await expect(page.getByTestId("staff")).toContainText(email);

  const staff = await openContext();
  await signIn(staff, email);
  const console = await staff.newPage();
  await console.goto("/admin");
  const links = console.getByTestId("staff-links");
  await expect(links.getByRole("link", { name: "Payouts" })).toBeVisible();
  await expect(links.getByRole("link", { name: "Auctions", exact: true })).toHaveCount(0);
  await expect(console.getByTestId("funnel")).toBeVisible();
});
