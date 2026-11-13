import type { Page } from "@playwright/test";

import { buyOutright, completeSale, createLot, grantRoles, login, payAndShip, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique, type Lot } from "./support/test";

/**
 * EN: After the money: a buyer reports a problem and an admin decides — letting the sale stand, or refunding by bank
 *     transfer to the account the buyer gave; a seller adds a bank account and an admin records the transfer of what
 *     they are owed.
 * VI: Sau khi trả tiền: người mua báo có vấn đề và admin quyết định — giữ giao dịch, hoặc hoàn tiền bằng chuyển khoản
 *     vào tài khoản người mua đã cho; người bán thêm tài khoản ngân hàng và admin ghi nhận lần chuyển số tiền sàn nợ họ.
 */

/** EN: A buyer of its own, who has never given a bank account. / VI: Người mua riêng, chưa từng cho tài khoản ngân hàng. */
async function freshBuyer(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Fresh Buyer");
  return email;
}

/** EN: A lot the buyer bought outright, paid for and shipped. / VI: Một lô người mua đã mua đứt, đã trả và đã gửi. */
async function shippedTo(buyer: string, label: string): Promise<Lot> {
  const lot = await newLot(label, true, { buyNowPrice: 2_000_000 });
  const session = await login(buyer);
  await buyOutright(lot.id, session);
  await payAndShip(lot.id, session, await login(data.users.seller));
  return lot;
}

/**
 * EN: The buyer reports the problem; with no bank account saved yet, the dialog asks for one.
 * VI: Người mua báo sự cố; chưa lưu tài khoản ngân hàng thì hộp thoại hỏi luôn.
 */
async function reportProblem(page: Page, lot: Lot, details: string) {
  await page.goto("/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await row.getByRole("button", { name: "Report a problem" }).click();

  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox").click();
  await page.getByRole("option", { name: "Not received" }).click();
  await dialog.getByLabel("What happened").fill(details);
  await dialog.getByRole("button", { name: "Send" }).click();
  // EN: Sending without an account is stopped at the account fields. / VI: Gửi khi chưa có tài khoản bị chặn ở các ô tài khoản.
  await expect(dialog.getByText("An account number has 6 to 30 digits.")).toBeVisible();
  await dialog.getByLabel("Bank").fill("Techcombank");
  await dialog.getByLabel("Account number").fill("1903 5555 1234");
  await dialog.getByLabel("Account holder").fill("Fresh Buyer");
  await dialog.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Problem reported")).toBeVisible();
  await expect(row.getByText("Under review")).toBeVisible();
  await expect(row.getByRole("button", { name: "Report a problem" })).toHaveCount(0);
  return row;
}

test("a buyer disputes a shipped order and an admin lets the sale stand with a note", async ({ page, openContext }) => {
  const buyer = await freshBuyer("dispute-buyer");
  const lot = await shippedTo(buyer, "Disputed lot");
  const details = unique("Tracking has not moved for a week");
  const note = unique("The carrier confirms delivery");

  await signIn(page.context(), buyer);
  const row = await reportProblem(page, lot, details);

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const queue = await admin.newPage();
  await queue.goto("/admin/disputes");
  const dispute = queue.getByRole("main").locator("li").filter({ hasText: lot.name });
  await expect(dispute.getByText(details)).toBeVisible();
  await dispute.getByRole("button", { name: "Let the sale stand" }).click();
  const decide = queue.getByRole("dialog");
  await decide.getByLabel("Note to both sides").fill(note);
  await decide.getByRole("button", { name: "Let the sale stand" }).click();
  await expect(queue.getByText("Sale stands")).toBeVisible();
  await expect(dispute).toHaveCount(0);

  // EN: The buyer sees the outcome on the order and in the bell. / VI: Người mua thấy kết quả trên đơn và trong chuông.
  await page.reload();
  await expect(row.getByText("Not upheld")).toBeVisible();
  await expect(row.getByText(note)).toBeVisible();
  await page.goto("/notifications");
  await expect(page.getByText("Dispute not upheld").first()).toBeVisible();
});

test("an admin refunds a disputed order by bank transfer to the account the buyer gave", async ({
  page,
  openContext,
}) => {
  const buyer = await freshBuyer("refund-buyer");
  const lot = await shippedTo(buyer, "Refunded lot");

  await signIn(page.context(), buyer);
  const row = await reportProblem(page, lot, unique("The box never arrived"));

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const queue = await admin.newPage();
  await queue.goto("/admin/disputes");
  const dispute = queue.getByRole("main").locator("li").filter({ hasText: lot.name });
  await dispute.getByRole("button", { name: "Refund" }).click();
  const decide = queue.getByRole("dialog");
  // EN: The account the buyer gave, ready to copy into the bank app. / VI: Tài khoản người mua đã cho, sẵn để chép sang ứng dụng ngân hàng.
  await expect(decide.getByText("Techcombank")).toBeVisible();
  await expect(decide.getByText("190355551234")).toBeVisible();
  await expect(decide.getByText("FRESH BUYER")).toBeVisible();
  const reference = `FT${Date.now()}`;
  await decide.getByLabel("Bank reference").fill(reference);
  await decide.getByRole("button", { name: "Refund" }).click();
  await expect(queue.getByText("Buyer refunded")).toBeVisible();
  await expect(dispute).toHaveCount(0);

  await page.reload();
  await expect(row.getByText("Refunded").first()).toBeVisible();
  await page.goto("/notifications");
  await expect(page.getByText("Refund sent").first()).toBeVisible();
  await expect(page.getByText(reference)).toBeVisible();
});

test("a seller adds a payout account and an admin records the transfer", async ({ page, openContext }) => {
  // EN: A seller of its own, so other tests' sales do not change the balance. / VI: Người bán riêng, để giao dịch của test khác không đổi số dư.
  const email = `${unique("payout-seller").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Payout Seller");
  grantRoles([[email, "SELLER"]]);
  const seller = await login(email);
  const lot = await createLot(seller, await login(data.users.admin), unique("Payout lot"), true, {
    buyNowPrice: 2_000_000,
  });
  const alex = await login(data.users.alex);
  await buyOutright(lot.id, alex);
  await completeSale(lot.id, alex, seller);

  await signIn(page.context(), email);
  await page.goto("/seller/earnings");
  // EN: 2,000,000 less the 5% fee. / VI: 2.000.000 trừ phí 5%.
  await expect(page.getByText(/1,900,000/).first()).toBeVisible();
  await page.getByLabel("Bank").fill("Vietcombank");
  await page.getByLabel("Account number").fill("0123456789");
  await page.getByLabel("Account holder").fill("Payout Seller");
  await page.getByRole("button", { name: "Save account" }).click();
  await expect(page.getByText("Payout account saved")).toBeVisible();
  await expect(page.getByText("PAYOUT SELLER")).toBeVisible();

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const payouts = await admin.newPage();
  await payouts.goto("/admin/payouts");
  const due = payouts.getByRole("main").locator("li").filter({ hasText: email });
  await expect(due.getByText("Vietcombank")).toBeVisible();
  await due.getByRole("button", { name: "Mark as paid" }).click();
  const dialog = payouts.getByRole("dialog");
  const reference = `FT${Date.now()}`;
  await dialog.getByLabel("Bank reference").fill(reference);
  await dialog.getByRole("button", { name: "Confirm transfer" }).click();
  await expect(payouts.getByText("Payout recorded").first()).toBeVisible();
  await expect(due).toHaveCount(0);

  await page.reload();
  await expect(page.getByText(reference)).toBeVisible();
  await page.goto("/notifications");
  await expect(page.getByText("Payout sent").first()).toBeVisible();
});
