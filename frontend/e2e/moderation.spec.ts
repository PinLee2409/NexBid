import { buyOutright, completeSale, login, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/** EN: A buyer of its own. / VI: Một người mua riêng. */
async function freshBuyer(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Fresh Buyer");
  return email;
}

/**
 * EN: A buyer reports a lot; an admin takes it down; the lot leaves the marketplace and the reporter hears of it.
 * VI: Người mua báo cáo một lô; admin gỡ lô; lô rời khỏi sàn và người báo cáo được báo.
 */
test("a reported lot is taken down by an admin", async ({ page, openContext }) => {
  const lot = await newLot("Reported lot", true);
  const buyer = await freshBuyer("reporter");

  await signIn(page.context(), buyer);
  await page.goto(`/auctions/${lot.id}`);
  await page.getByRole("button", { name: "Report this lot" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox").click();
  await page.getByRole("option", { name: "Counterfeit or fake" }).click();
  await dialog.getByLabel("What you noticed").fill("The serial number does not exist.");
  await dialog.getByRole("button", { name: "Send report" }).click();
  await expect(page.getByText("Report sent")).toBeVisible();

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const queue = await admin.newPage();
  await queue.goto("/admin/reports");
  const report = queue.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(report.getByText("The serial number does not exist.")).toBeVisible();
  await report.getByRole("button", { name: "Take lot down" }).click();
  const decide = queue.getByRole("dialog");
  await decide.getByLabel("Reason for the seller").fill("Counterfeit confirmed.");
  await decide.getByRole("button", { name: "Take lot down" }).click();
  await expect(queue.getByText("Lot taken down. The seller and bidders have been told.")).toBeVisible();
  await expect(report).toHaveCount(0);

  await page.goto("/notifications");
  await expect(page.getByText("Report upheld")).toBeVisible();
  // EN: The page streams, so the status stays 200; the content is the not-found page. / VI: Trang được stream nên mã vẫn là 200; nội dung là trang không tìm thấy.
  await page.goto(`/auctions/${lot.id}`);
  await expect(page.getByRole("heading", { name: "Auction not found" })).toBeVisible();
});

/**
 * EN: The seller rates the buyer of a completed order, and sees the buyer's record beside it.
 * VI: Người bán đánh giá người mua của một đơn đã hoàn tất, và thấy hồ sơ người mua bên cạnh.
 */
test("a seller rates the buyer of a completed order", async ({ page }) => {
  const lot = await newLot("Rated buyer", true, { buyNowPrice: 2_000_000 });
  const buyer = await login(await freshBuyer("rated-buyer"));
  await buyOutright(lot.id, buyer);
  await completeSale(lot.id, buyer, await login(data.users.seller));

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(row.getByText("1 completed order")).toBeVisible();
  await expect(row.getByText("never left a win unpaid")).toBeVisible();
  await row.getByRole("button", { name: "Rate buyer" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("radio", { name: /4/ }).check({ force: true });
  await dialog.getByLabel("Comment (optional)").fill("Paid at once.");
  await dialog.getByRole("button", { name: "Save rating" }).click();
  await expect(page.getByText("Rating saved")).toBeVisible();
  await expect(row.getByText("Your rating of the buyer")).toBeVisible();
  await expect(row.getByText("4.0★ from 1 seller rating")).toBeVisible();
});

/**
 * EN: VNPay's return page works without a session, and the email outbox is there for admins.
 * VI: Trang VNPay trả về chạy được khi không có phiên đăng nhập, và admin xem được hàng chờ email.
 */
test("the VNPay return page needs no sign-in, and admins see the email outbox", async ({ page, openContext }) => {
  await page.goto("/payments/vnpay-return?vnp_TxnRef=unknown&vnp_SecureHash=bad");
  await expect(page).toHaveURL(/\/payments\/vnpay-return/);
  await expect(page.getByRole("button", { name: "Sign in" })).toHaveCount(0);

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const emails = await admin.newPage();
  await emails.goto("/admin/emails");
  await expect(emails.getByRole("heading", { name: "Emails", level: 1 })).toBeVisible();
  await expect(emails.getByRole("tab", { name: /Failed/ })).toBeVisible();
});
