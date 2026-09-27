import { data, expect, signIn, test } from "./support/test";

/**
 * EN: A reader who chose Vietnamese sees Vietnamese, errors included. The server always answers in English;
 *     the app shows its own message for the error's code instead.
 * VI: Người đọc đã chọn tiếng Việt thì thấy tiếng Việt, kể cả khi có lỗi. Server luôn trả lời bằng tiếng Anh;
 *     app hiển thị thông điệp của chính nó theo mã lỗi thay vào đó.
 */
test.beforeEach(async ({ context, baseURL }) => {
  await context.addCookies([{ name: "NEXBID_LOCALE", value: "vi", url: baseURL! }]);
});

test("a wrong password is explained in Vietnamese", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Email").fill(data.users.alex);
  await page.getByRole("textbox", { name: "Mật khẩu", exact: true }).fill("sai-mat-khau");
  await page.getByRole("button", { name: "Đăng nhập" }).click();

  await expect(page.getByText("Email hoặc mật khẩu không đúng.")).toBeVisible();
});

test("a list that fails to load says why in Vietnamese, not in the server's English", async ({ context, page }) => {
  await signIn(context, data.users.alex);
  await context.route("**/api/users/me/bids", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({ success: false, code: "ACCESS_DENIED", message: "You may not see these bids" }),
    }),
  );

  await page.goto("/my-bids");

  await expect(page.getByText("Bạn không có quyền thực hiện thao tác này.")).toBeVisible();
  await expect(page.getByText("You may not see these bids")).toHaveCount(0);
});
