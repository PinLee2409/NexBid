import { data, expect, signIn, test, unique } from "./support/test";

/**
 * EN: A seller lists lots from a CSV file: good rows become drafts, bad rows say what is wrong.
 * VI: Người bán đăng lô từ file CSV: dòng đúng thành bản nháp, dòng sai báo sai chỗ nào.
 */
test("a seller imports lots from a CSV file", async ({ page }) => {
  const name = unique("Imported camera");
  const csv = [
    "name,description,category,condition,starting_price,increment,start,end",
    `"${name}","A long description of the imported item.",cameras,GOOD,1.200.000,100000,2030-03-01 09:00,2030-03-03 21:00`,
    `"Broken row","A long description of the item.",no-such-category,GOOD,1000000,100000,2030-03-01 09:00,2030-03-03 21:00`,
  ].join("\r\n");

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/auctions");
  await page.getByRole("button", { name: "Import CSV" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("CSV file").setInputFiles({ name: "lots.csv", mimeType: "text/csv", buffer: Buffer.from(csv) });
  await dialog.getByRole("button", { name: "Import" }).click();

  await expect(dialog.getByText("1 created, 1 with problems")).toBeVisible();
  await expect(dialog.getByTestId("import-results")).toContainText("no category with slug");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("main").getByText(name)).toBeVisible();
});

/**
 * EN: The admin's finance page reconciles the last 30 days and shows revenue.
 * VI: Trang tài chính của admin đối soát 30 ngày gần nhất và hiện doanh thu.
 */
test("an admin opens reconciliation and revenue", async ({ page }) => {
  await signIn(page.context(), data.users.admin);
  await page.goto("/admin/finance");
  await expect(page.getByRole("heading", { name: "Finance", level: 1 })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "Applied" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "Total" })).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "VNPay charges (CSV)" }).click();
  expect((await download).suggestedFilename()).toMatch(/^nexbid-vnpay-.*\.csv$/);

  await page.getByRole("tab", { name: "Revenue" }).click();
  await expect(page.getByRole("heading", { name: "By month", exact: true })).toBeVisible();
});
