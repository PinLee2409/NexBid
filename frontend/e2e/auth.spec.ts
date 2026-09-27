import { PASSWORD, TOKEN_STORAGE_KEY } from "./support/stack";
import { data, expect, test, unique } from "./support/test";

test.describe("signing in", () => {
  test("the right password signs the buyer in", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(data.users.alex);
    await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Welcome back, Alex E2E.")).toBeVisible();
    await expect(page).toHaveURL(/\/$/);
    // EN: The session survives a reload. / VI: Phiên vẫn còn sau khi tải lại trang.
    await page.reload();
    expect(await page.evaluate((key) => window.localStorage.getItem(key), TOKEN_STORAGE_KEY)).not.toBeNull();
  });

  test("a wrong password is refused and says so", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(data.users.alex);
    await page.getByRole("textbox", { name: "Password", exact: true }).fill("not-the-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Incorrect email or password.")).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});

test("a new buyer can create an account", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("Full name").fill("Mika E2E");
  await page.getByLabel("Email").fill(`${unique(`e2e-${data.run}-mika`).replace(" ", "-")}@nexbid.test`);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByLabel("Confirm password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page.getByText("Account created")).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
});
