import { data, expect, newLot, signIn, test, unique } from "./support/test";

/**
 * EN: A buyer asks about a lot in public, the seller answers under the question, and the buyer hears of it.
 * VI: Người mua hỏi công khai về một lô, người bán trả lời ngay dưới câu hỏi, và người mua được báo.
 */
test("a buyer asks about a lot and the seller answers it in public", async ({ page, openContext }) => {
  const lot = await newLot("Lot with questions", true);
  const question = unique("Does it come with the original box");
  const answer = unique("Yes, box and papers");

  // EN: A visitor reads, but is asked to sign in to ask. / VI: Khách đọc được, nhưng phải đăng nhập mới hỏi được.
  await page.goto(`/auctions/${lot.id}`);
  const section = page.getByRole("region", { name: "Questions & answers" });
  await expect(section.getByRole("link", { name: "Sign in to ask the seller a question" })).toBeVisible();

  await signIn(page.context(), data.users.alex);
  await page.reload();
  await section.getByLabel("Your question").fill(question);
  await section.getByRole("button", { name: "Ask the seller" }).click();
  await expect(page.getByText("Question posted")).toBeVisible();
  const item = section.locator("li").filter({ hasText: question });
  await expect(item.getByText("Waiting for the seller")).toBeVisible();

  const seller = await openContext();
  await signIn(seller, data.users.seller);
  const lotPage = await seller.newPage();
  await lotPage.goto(`/auctions/${lot.id}`);
  const sellerSection = lotPage.getByRole("region", { name: "Questions & answers" });
  // EN: The seller cannot ask about their own lot. / VI: Người bán không tự hỏi về lô của mình.
  await expect(sellerSection.getByRole("button", { name: "Ask the seller" })).toHaveCount(0);
  const sellerItem = sellerSection.locator("li").filter({ hasText: question });
  await sellerItem.getByLabel("Your answer").fill(answer);
  await sellerItem.getByRole("button", { name: "Post answer" }).click();
  await expect(lotPage.getByText("Answer posted")).toBeVisible();
  await expect(sellerItem.getByText(answer)).toBeVisible();

  await page.reload();
  await expect(item.getByText(answer)).toBeVisible();
  await page.goto("/notifications");
  await expect(page.getByText("The seller answered").first()).toBeVisible();
});
