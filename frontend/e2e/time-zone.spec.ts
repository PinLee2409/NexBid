import { TIME_ZONE_COOKIE } from "../src/i18n/config";
import { login, placeBid } from "./support/stack";
import { data, expect, newLot, signIn, test } from "./support/test";

/**
 * EN: Every time on screen is in the reader's own zone: the live bid feed and the admin schedule alike.
 *     Two zones on opposite sides of UTC, so a page stuck in UTC fails both.
 * VI: Mọi mốc giờ trên màn hình theo múi giờ của người xem: cả danh sách bid realtime lẫn lịch trên trang
 *     admin. Hai múi giờ ở hai phía của UTC, nên trang nào kẹt ở UTC sẽ trượt cả hai.
 */
const ZONES = ["Asia/Ho_Chi_Minh", "America/New_York"];

// EN: Browsers put a narrow space before AM/PM; compare text without caring which space.
// VI: Trình duyệt chèn khoảng trắng hẹp trước AM/PM; so sánh chữ mà không quan tâm loại khoảng trắng.
const plain = (text: string | null) => (text ?? "").replace(/\s+/g, " ");

for (const timezoneId of ZONES) {
  test(`times are shown in ${timezoneId}`, async ({ openContext }) => {
    const clock = await newLot("Clock lot", true);
    const bid = await placeBid(clock.id, await login(data.users.sara));
    const queued = await newLot("Queued lot", false);
    const context = await openContext({ timezoneId });
    await signIn(context, data.users.admin);
    const page = await context.newPage();

    const bidTime = new Intl.DateTimeFormat("en-US", {
      timeZone: timezoneId,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
    }).format(new Date(bid.createdAt));
    await page.goto(`/auctions/${clock.id}`);
    await expect(page.locator("#bid-panel").getByText(bidTime)).toBeVisible();
    // EN: The cookie holds the zone as the browser names it (Chrome says Asia/Saigon for Asia/Ho_Chi_Minh).
    // VI: Cookie giữ múi giờ theo tên trình duyệt đặt (Chrome gọi Asia/Ho_Chi_Minh là Asia/Saigon).
    const reported = await page.evaluate(() => Intl.DateTimeFormat().resolvedOptions().timeZone);
    expect((await context.cookies()).find((cookie) => cookie.name === TIME_ZONE_COOKIE)?.value).toBe(reported);

    const opens = new Intl.DateTimeFormat("en-US", {
      timeZone: timezoneId,
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(queued.startTime));
    await page.goto("/admin/auctions");
    const row = page.locator("li").filter({ has: page.getByRole("link", { name: queued.name, exact: true }) });
    await expect.poll(async () => plain(await row.textContent())).toContain(plain(opens));
  });
}
