import { buyOutright, createLot, grantRoles, login, payAndShip, register } from "./support/stack";
import { data, expect, newLot, signIn, test, unique } from "./support/test";

/** EN: A buyer of its own. / VI: Một người mua riêng. */
async function freshBuyer(label: string): Promise<string> {
  const email = `${unique(label).toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Chat Buyer");
  return email;
}

/**
 * EN: The buyer writes to the seller about an order; contact details are refused; the seller's reply arrives live.
 * VI: Người mua nhắn người bán về một đơn; thông tin liên lạc bị từ chối; câu trả lời của người bán tới ngay.
 */
test("the buyer and the seller talk about their order", async ({ page, openContext }) => {
  const lot = await newLot("Chat lot", true, { buyNowPrice: 2_000_000 });
  const buyerEmail = await freshBuyer("chat-buyer");
  const buyer = await login(buyerEmail);
  await buyOutright(lot.id, buyer);
  await payAndShip(lot.id, buyer, await login(data.users.seller));

  await signIn(page.context(), buyerEmail);
  await page.goto("/orders");
  const row = page.getByRole("main").locator("li").filter({ hasText: lot.name });
  await row.getByRole("link", { name: "Messages" }).click();
  await expect(page).toHaveURL(/\/messages\?order=/);

  const draft = page.getByLabel("Your message");
  await draft.fill("Call me on 0912 345 678 please");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Messages cannot contain phone numbers", { exact: false })).toBeVisible();

  await draft.fill("Hello, could you pack it carefully?");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Hello, could you pack it carefully?")).toBeVisible();

  const seller = await openContext();
  await signIn(seller, data.users.seller);
  const inbox = await seller.newPage();
  await inbox.goto("/messages");
  await inbox.getByRole("link", { name: new RegExp(lot.name) }).click();
  await expect(inbox.getByText("Hello, could you pack it carefully?")).toBeVisible();
  await inbox.getByLabel("Your message").fill("Of course, double boxed.");
  await inbox.getByRole("button", { name: "Send" }).click();

  // EN: The reply arrives on the buyer's open thread without a reload. / VI: Câu trả lời tới cuộc trò chuyện đang mở của người mua mà không cần tải lại.
  await expect(page.getByText("Of course, double boxed.")).toBeVisible();
});

/**
 * EN: An admin verifies the seller; the badge shows on their lot.
 * VI: Admin xác minh người bán; huy hiệu hiện trên lô của họ.
 */
test("an admin verifies a seller and the badge shows on the lot", async ({ page, openContext }) => {
  const email = `${unique("verified-seller").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Verified Seller");
  grantRoles([[email, "SELLER"]]);
  const lot = await createLot(await login(email), await login(data.users.admin), unique("Badge lot"), true);

  await page.goto(`/auctions/${lot.id}`);
  await expect(page.getByLabel("Verified seller")).toHaveCount(0);

  const admin = await openContext();
  await signIn(admin, data.users.admin);
  const users = await admin.newPage();
  await users.goto("/admin/users");
  await users.getByRole("searchbox").fill(email);
  await users.getByRole("searchbox").press("Enter");
  const row = users.getByRole("main").locator("li").filter({ hasText: email });
  await row.getByRole("button", { name: "Verify seller" }).click();
  await expect(users.getByText("Seller verified")).toBeVisible();

  await page.reload();
  await expect(page.getByLabel("Verified seller")).toBeVisible();
});
