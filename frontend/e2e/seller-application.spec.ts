import { PASSWORD, register } from "./support/stack";
import { data, expect, signIn, test, unique } from "./support/test";

/**
 * EN: Becoming a seller (spec §7.1): a buyer asks, an admin approves, and the seller tools open in the buyer's
 *     open tab without signing in again — the role arrives with a renewed token.
 * VI: Trở thành người bán (spec §7.1): người mua gửi yêu cầu, admin duyệt, và công cụ người bán mở ra ngay trên
 *     tab đang mở của người mua mà không cần đăng nhập lại — vai trò tới cùng token được gia hạn.
 */
test("an approved buyer can sell without signing in again", async ({ page, openContext }) => {
  const name = unique("Applicant");
  const email = `e2e-${data.run}-${Date.now().toString(36)}@nexbid.test`;
  await register(email, name);

  // EN: Through the form, so the browser holds a refresh cookie like a real visitor. / VI: Qua form, để trình duyệt có cookie refresh như người dùng thật.
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText(`Welcome back, ${name}.`)).toBeVisible();

  // EN: The seller workspace explains itself instead of failing. / VI: Khu vực người bán tự giải thích thay vì báo lỗi.
  await page.goto("/seller/products");
  await expect(page.getByRole("heading", { name: "Seller tools need approval" })).toBeVisible();
  await page.getByRole("main").getByRole("link", { name: "Become a seller" }).click();

  await page.getByLabel("What will you sell?").fill("Mechanical watches from the 1960s, serviced and boxed.");
  await page.getByRole("button", { name: "Send request" }).click();
  await expect(page.getByRole("heading", { name: "Your request is under review" })).toBeVisible();

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const queue = await admin.newPage();
  await queue.goto("/admin/sellers");
  const row = queue.locator("li").filter({ has: queue.getByRole("heading", { name, exact: true }) });
  await expect(row.getByText("Mechanical watches from the 1960s")).toBeVisible();
  await row.getByRole("button", { name: "Approve" }).click();
  await expect(queue.getByText("Seller approved").first()).toBeVisible();
  await expect(row).toHaveCount(0);

  // EN: The notice arrives live, the token is renewed, and the page follows. / VI: Thông báo tới trực tiếp, token được gia hạn, và trang cập nhật theo.
  await expect(page.getByRole("heading", { name: "You can sell" })).toBeVisible();
  await page.getByRole("link", { name: "List an item" }).click();
  await expect(page).toHaveURL(/\/seller\/products\/create$/);
  await page.goto("/seller/products");
  await expect(page.getByRole("heading", { name: "No products yet" })).toBeVisible();
});
