import { buyOutright, login, pay, payAndShip, register, sendOrderMessage } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/** EN: A buyer of its own. / VI: Một người mua riêng. */
async function freshBuyer(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Shipping Buyer");
  return email;
}

/**
 * EN: The seller picks the carrier from a list; one not on it is typed in.
 * VI: Người bán chọn hãng vận chuyển từ danh sách; hãng không có trong danh sách thì gõ tên.
 */
test("the seller picks the carrier when marking an order shipped", async ({ page }) => {
  const lot = await newLot("Ship lot", true, { buyNowPrice: 2_000_000 });
  const buyer = await login(await freshBuyer("ship-buyer"));
  await buyOutright(lot.id, buyer);
  await pay(lot.id, buyer, await login(data.users.seller));

  await signIn(page.context(), data.users.seller);
  await page.goto("/seller/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await row.getByRole("button", { name: "Mark as shipped" }).click();

  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox", { name: "Carrier" }).click();
  await expect(page.getByRole("option", { name: "GHN" })).toBeVisible();
  await page.getByRole("option", { name: "Another carrier" }).click();
  await dialog.getByLabel("Carrier name").fill("Ahamove");
  await dialog.getByLabel("Tracking number").fill("AH-778899");
  await dialog.getByRole("button", { name: "Mark as shipped" }).click();

  await expect(page.getByText("Marked as shipped")).toBeVisible();
  await expect(row.getByText("Ahamove · AH-778899")).toBeVisible();
});

/**
 * EN: An admin settling a dispute reads what the two sides wrote to each other.
 * VI: Admin xử lý khiếu nại đọc được hai bên đã nhắn gì cho nhau.
 */
test("an admin reads an order's messages", async ({ page }) => {
  const lot = await newLot("Read lot", true, { buyNowPrice: 2_000_000 });
  const buyer = await login(await freshBuyer("read-buyer"));
  const seller = await login(data.users.seller);
  await buyOutright(lot.id, buyer);
  const orderId = await payAndShip(lot.id, buyer, seller);
  const note = unique("Is it insured for the trip");
  await sendOrderMessage(orderId, buyer, note);
  await sendOrderMessage(orderId, seller, "Yes, fully insured.");

  await signIn(page.context(), data.users.admin);
  await page.goto("/admin/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await row.getByRole("button", { name: "Messages" }).click();

  const dialog = page.getByRole("dialog", { name: "Messages on this order" });
  await expect(dialog.getByText(note)).toBeVisible();
  await expect(dialog.getByText("Yes, fully insured.")).toBeVisible();
  await expect(dialog.getByText("buyer", { exact: false }).first()).toBeVisible();
});
