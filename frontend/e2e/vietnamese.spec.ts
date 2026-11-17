import { data, expect, newLot, signIn, test } from "./support/test";

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

test("the bid field groups thousands the Vietnamese way while typing", async ({ context, page }) => {
  const lot = await newLot("Grouped input", true);
  await signIn(context, data.users.alex);
  await page.goto(`/auctions/${lot.id}`);
  const field = page.locator("#terminal-bid");
  await expect(field).toHaveValue("1.000.000");

  await field.fill("");
  await field.pressSequentially("12600000");
  await expect(field).toHaveValue("12.600.000");
  // EN: Deleting in the middle keeps the caret where it was. / VI: Xoá ở giữa vẫn giữ con trỏ đúng chỗ.
  await field.press("Home");
  await field.press("ArrowRight");
  await field.press("ArrowRight");
  await field.press("Backspace");
  await field.type("3");
  await expect(field).toHaveValue("13.600.000");
});
