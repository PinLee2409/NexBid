import { accessibilityProblems, open, pagesFor, prepare, ROLES } from "./support/pages";
import { data, expect, newLot, signIn, test } from "./support/test";

/** EN: A small phone. / VI: Một chiếc điện thoại màn hình nhỏ. */
const PHONE = { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 };

for (const role of ROLES) {
  test(`${role} pages fit a phone screen`, async ({ openContext }) => {
    test.setTimeout(120_000);
    const context = await openContext(PHONE);
    await prepare(context, role, "dark");
    const page = await context.newPage();

    const problems: string[] = [];
    for (const path of await pagesFor(role)) {
      await open(page, path);
      // EN: Nothing may push the page wider than the screen. innerWidth would grow with it (a phone zooms out to fit), clientWidth does not.
      // VI: Không gì được đẩy trang rộng hơn màn hình. innerWidth sẽ nở theo (điện thoại thu nhỏ cho vừa), clientWidth thì không.
      const width = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
      if (width[0] > width[1]) problems.push(`${path} — ${width[0]}px wide on a ${width[1]}px screen`);
      // EN: Phone-only problems too, such as a table that scrolls sideways. / VI: Cả lỗi chỉ có trên điện thoại, như bảng cuộn ngang.
      problems.push(...(await accessibilityProblems(page, path)));
    }
    expect(problems).toEqual([]);
  });
}

test("a buyer bids from a phone through the sticky bid bar", async ({ openContext }) => {
  const lot = await newLot("Phone lot", true);
  const context = await openContext(PHONE);
  await signIn(context, data.users.alex);
  const page = await context.newPage();
  await page.goto(`/auctions/${lot.id}`);

  // EN: The bar at the bottom of the screen leads to the bid panel. / VI: Thanh dưới đáy màn hình dẫn tới bảng trả giá.
  await page.getByRole("link", { name: "Place bid" }).tap();
  const panel = page.locator("#bid-panel");
  await expect(panel.getByRole("button", { name: "Place bid" })).toBeInViewport();

  await panel.getByRole("button", { name: "Place bid" }).tap();
  await page.getByRole("button", { name: "Confirm bid" }).tap();
  await expect(panel).toContainText("You are currently the highest bidder");
});
