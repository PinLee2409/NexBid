import { register } from "./support/stack";
import { expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: A buyer saves a catalogue search, hears of the new lot that fits, follows its seller, and manages both on the
 *     alerts page.
 * VI: Người mua lưu một tìm kiếm, được báo về lô mới khớp, theo dõi người bán của lô, và quản lý cả hai ở trang báo lô mới.
 */
test("a saved search hears of a new lot, and its seller can be followed", async ({ page }) => {
  // EN: A buyer of its own, so the shared seller's other lots do not reach the shared accounts.
  // VI: Người mua riêng, để các lô khác của người bán dùng chung không tới các tài khoản dùng chung.
  const email = `${unique("alerts").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Alert Buyer");
  const tag = unique("Kestrel").split(" ")[1];

  // EN: A visitor is asked to sign in first. / VI: Khách được mời đăng nhập trước.
  await page.goto(`/auctions?search=${tag}`);
  await expect(page.getByRole("link", { name: "Sign in to save this search" })).toBeVisible();

  await signIn(page.context(), email);
  await page.reload();
  await page.getByRole("button", { name: "Save this search" }).click();
  await expect(page.getByText("Search saved")).toBeVisible();

  const lot = await newLot(`Kestrel ${tag}`, true);

  await page.goto("/notifications");
  await expect(page.getByText("New lot for your saved search")).toBeVisible();
  await expect(page.getByText(lot.name)).toBeVisible();

  await page.goto(`/auctions/${lot.id}`);
  await page.getByRole("button", { name: "Follow seller" }).click();
  await expect(page.getByText("You'll hear when this seller lists something new.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Following" })).toBeVisible();

  await page.goto("/alerts");
  const searches = page.getByRole("region", { name: "Saved searches" });
  await expect(searches.getByText(`“${tag}”`)).toBeVisible();
  const sellers = page.getByRole("region", { name: "Sellers you follow" });
  await expect(sellers.getByRole("button", { name: "Unfollow" })).toBeVisible();

  await searches.getByRole("link", { name: "View lots" }).click();
  await expect(page.getByText(lot.name).first()).toBeVisible();

  await page.goto("/alerts");
  await searches.getByRole("button", { name: "Delete search" }).click();
  await expect(page.getByText("Search deleted")).toBeVisible();
  await expect(searches.getByText("No saved searches yet", { exact: false })).toBeVisible();
  await sellers.getByRole("button", { name: "Unfollow" }).click();
  await expect(sellers.getByText("You don't follow anyone yet", { exact: false })).toBeVisible();
});
