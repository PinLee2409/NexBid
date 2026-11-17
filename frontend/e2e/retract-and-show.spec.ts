import { login, placeBid, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: Taking a bid back from the lot page and finding it in the staff log, the reference price under the current bid,
 *     and the opening slideshow on the home page.
 * VI: Rút lại lượt trả giá từ trang lô và thấy nó trong nhật ký nhân viên, giá tham khảo dưới giá hiện tại, và trình
 *     chiếu mở đầu trên trang chủ.
 */
async function freshBidder(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Retract Bidder");
  return email;
}

test("a bid taken back in its first minutes puts the price back and shows in the staff log", async ({ page, browser }) => {
  const lot = await newLot("Retract lot", true);
  await placeBid(lot.id, await login(data.users.sara));
  const email = await freshBidder("retracter");
  await placeBid(lot.id, await login(email));

  await signIn(page.context(), email);
  await page.goto(`/auctions/${lot.id}`);
  const panel = page.locator("#bid-panel");
  await expect(panel.getByText("₫1,100,000").first()).toBeVisible();
  await expect(panel.getByTestId("fx-hint")).toContainText("≈");

  await panel.getByRole("button", { name: "Take back my bids" }).click();
  const dialog = page.getByRole("dialog", { name: "Take back your bids?" });
  await expect(dialog.getByRole("radio", { name: /Changed my mind/ })).toBeChecked();
  await expect(dialog.getByTestId("retract-quota")).toHaveText("Used 0 of 3 in the last 30 days.");
  await dialog.getByRole("button", { name: "Take them back" }).click();
  await expect(page.getByText("Your bid was taken back.")).toBeVisible();
  await expect(panel.getByText("₫1,000,000").first()).toBeVisible();
  await expect(panel.getByRole("button", { name: "Take back my bids" })).toBeHidden();

  const staff = await browser.newContext();
  const admin = await staff.newPage();
  await signIn(staff, data.users.admin);
  await admin.goto("/admin/bid-retractions");
  const row = admin.getByTestId("retractions").locator("tr").filter({ hasText: lot.name });
  await expect(row).toContainText("Changed my mind");
  await expect(row).toContainText("₫1,100,000 → ₫1,000,000");
  await staff.close();
});

test("the opening stage takes turns and can be paused", async ({ page }) => {
  await page.goto("/");
  const stage = page.getByTestId("opening-stage");
  await expect(stage.getByRole("group", { name: /^Lot 1 of \d+$/ })).toBeVisible();
  const next = stage.getByRole("button", { name: "Next lot" });
  if ((await next.count()) === 0) return;

  await stage.getByTestId("stage-toggle").click();
  await expect(stage.getByTestId("stage-toggle")).toHaveAttribute("aria-pressed", "true");
  await next.click();
  await expect(stage.getByRole("group", { name: /^Lot 2 of \d+$/ })).toBeVisible();
  await stage.getByRole("button", { name: "Previous lot" }).click();
  await expect(stage.getByRole("group", { name: /^Lot 1 of \d+$/ })).toBeVisible();
});
