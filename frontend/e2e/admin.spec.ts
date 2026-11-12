import type { Page } from "@playwright/test";

import { data, expect, newLot, signIn, test, unique } from "./support/test";
import { PASSWORD } from "./support/stack";

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

test("an admin creates a category and archives it", async ({ context, page }) => {
  const name = unique("Category");
  await signIn(context, data.users.admin);
  await page.goto("/admin/categories");

  await page.getByRole("button", { name: "New category" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name").fill(name);
  await dialog.getByRole("button", { name: "Create" }).click();
  await expect(page.getByText("Category created")).toBeVisible();

  const row = page.locator("li").filter({ has: page.getByRole("heading", { name, exact: true }) });
  await row.getByRole("button", { name: `Archive ${name}` }).click();
  await expect(page.getByText("Category archived")).toBeVisible();
  await expect(row).toHaveCount(0);

  await page.getByRole("tab", { name: "Archived" }).click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
});

test.describe("the admin console's door", () => {
  async function signInWithForm(page: Page, email: string) {
    await page.getByLabel("Email").fill(email);
    await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
  }

  test("sends a signed-out visitor to the admin sign-in, then back to the page they asked for", async ({ page }) => {
    await page.goto("/admin/categories");
    await expect(page).toHaveURL(/\/admin\/login\?redirect=%2Fadmin%2Fcategories$/);
    await expect(page.getByRole("heading", { name: "Admin console" })).toBeVisible();

    await signInWithForm(page, data.users.admin);
    await expect(page).toHaveURL(/\/admin\/categories$/);
    await expect(page.getByRole("heading", { name: "Categories" })).toBeVisible();
  });

  test("turns a buyer away at the admin sign-in and leaves them signed out", async ({ page }) => {
    await page.goto("/admin/login");
    await signInWithForm(page, data.users.alex);

    await expect(page.getByText("This account is not an administrator.", { exact: false })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/login$/);
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test("shows a buyer that the console is not for them, without its sidebar", async ({ context, page }) => {
    await signIn(context, data.users.alex);
    await page.goto("/admin");

    await expect(page.getByText("This area is for administrators")).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Admin console" })).toHaveCount(0);
  });

  test("never follows a redirect to another site", async ({ page }) => {
    await page.goto("/login?redirect=https://evil.example");
    await signInWithForm(page, data.users.alex);

    await expect(page).toHaveURL((url) => url.hostname !== "evil.example" && url.pathname === "/");
  });
});
