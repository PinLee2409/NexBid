import { register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: "Forgot password" answers the same for any email, and the profile changes the password.
 * VI: "Quên mật khẩu" trả lời giống nhau với mọi email, và trang hồ sơ đổi được mật khẩu.
 */
test("a person asks for a reset link and changes their password on the profile", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("link", { name: "Forgot password?" }).click();
  await expect(page.getByRole("heading", { name: "Forgot your password?" })).toBeVisible();
  await page.getByLabel("Email").fill("nobody@e2e.nexbid.test");
  await page.getByRole("button", { name: "Send the link" }).click();
  await expect(page.getByRole("status")).toContainText("If that email has a NexBid account");

  const email = `${unique("password").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Password Changer");
  await signIn(page.context(), email);
  await page.goto("/profile");
  const section = page.getByRole("region", { name: "Password" });
  await section.getByLabel("Current password").fill("e2e-password");
  await section.getByLabel(/^New password(?! again)/).fill("e2e-password-2");
  await section.getByLabel("New password again").fill("e2e-password-2");
  await section.getByRole("button", { name: "Change password" }).click();
  await expect(page.getByText("Password changed", { exact: true })).toBeVisible();
});

/**
 * EN: A seller's public page opens from their lot, and the seller's monthly statement opens.
 * VI: Trang công khai của người bán mở từ lô của họ, và bảng kê tháng của người bán mở được.
 */
test("a seller's public page opens from the lot, and their statement opens", async ({ page }) => {
  const lot = await newLot("Profile lot", true);
  await page.goto(`/auctions/${lot.id}`);
  await page.getByRole("link", { name: "E2E Seller" }).first().click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("E2E Seller");
  await expect(page.getByRole("heading", { name: "On sale now" })).toBeVisible();
  await expect(page.getByRole("main")).toContainText(lot.name);

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/statements");
  await expect(page.getByRole("heading", { name: "Statements", level: 1 })).toBeVisible();
  await expect(page.getByTestId("statement")).toContainText("Statement for");
});
