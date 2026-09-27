import { expect, grouped, newLot, test } from "./support/test";

test("a visitor finds a live lot and is asked to sign in to bid", async ({ page }) => {
  const lot = await newLot("Browse lot", true);

  await page.goto(`/auctions?search=${encodeURIComponent(lot.name)}`);
  await page.getByRole("link", { name: lot.name }).first().click();

  await expect(page).toHaveURL(new RegExp(`/auctions/${lot.id}$`));
  const panel = page.locator("#bid-panel");
  await expect(panel.getByRole("heading", { name: lot.name })).toBeVisible();
  await expect(panel).toContainText(grouped(1_000_000));

  await panel.getByRole("link", { name: "Sign in to bid" }).click();
  await expect(page).toHaveURL(/\/login$/);
});
