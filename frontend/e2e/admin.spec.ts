import { data, expect, newLot, signIn, test } from "./support/test";

test("an admin approves a lot from the queue", async ({ context, page }) => {
  const lot = await newLot("Approval lot", false);
  await signIn(context, data.users.admin);
  await page.goto("/admin/auctions");

  const row = page.locator("li").filter({ has: page.getByRole("link", { name: lot.name, exact: true }) });
  await row.getByRole("button", { name: "Approve" }).click();

  await expect(page.getByText("Auction approved")).toBeVisible();
  await expect(row).toHaveCount(0);

  await page.getByRole("tab", { name: "Approved" }).click();
  await expect(page.getByRole("link", { name: lot.name, exact: true })).toBeVisible();
});
