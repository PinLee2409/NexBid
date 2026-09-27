import type { BrowserContext, Page } from "@playwright/test";

import { API_URL, PASSWORD, TOKEN_STORAGE_KEY } from "./support/stack";
import { data, expect, test } from "./support/test";

/**
 * EN: Refresh tokens from the browser's side: the session outlives the 15-minute access token, "Remember me"
 *     decides whether it outlives the browser, and signing out ends it on the server too.
 * VI: Refresh token nhìn từ phía trình duyệt: phiên sống lâu hơn access token 15 phút, "Remember me" quyết định
 *     nó có sống qua lần đóng trình duyệt không, và đăng xuất kết thúc nó cả trên server.
 */
const REFRESH_COOKIE = "nexbid_refresh";

async function signInWithTheForm(page: Page, remember = true): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Email").fill(data.users.sara);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  if (!remember) await page.getByRole("checkbox", { name: "Remember me" }).uncheck();
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Welcome back, Sara E2E.")).toBeVisible();
}

/** EN: As if fifteen minutes had passed. / VI: Như thể đã qua mười lăm phút. */
async function expireTheAccessToken(page: Page): Promise<void> {
  await page.evaluate((key) => {
    const stored = JSON.parse(window.localStorage.getItem(key)!);
    stored.expiresAt = new Date(Date.now() - 1000).toISOString();
    window.localStorage.setItem(key, JSON.stringify(stored));
  }, TOKEN_STORAGE_KEY);
}

async function refreshCookie(context: BrowserContext) {
  return (await context.cookies()).find((cookie) => cookie.name === REFRESH_COOKIE);
}

test("the session outlives the 15-minute access token", async ({ context, page }) => {
  await signInWithTheForm(page);
  const before = await refreshCookie(context);
  expect(before).toMatchObject({ httpOnly: true, sameSite: "Strict", path: "/api/auth" });
  // EN: "Remember me" (the default): the cookie survives closing the browser, for a week.
  // VI: "Remember me" (mặc định): cookie sống qua lần đóng trình duyệt, trong một tuần.
  expect(before!.expires * 1000).toBeGreaterThan(Date.now() + 6 * 24 * 3600_000);

  await expireTheAccessToken(page);
  await page.goto("/profile");

  await expect(page.locator("main input").first()).toHaveValue("Sara E2E");
  const renewed = await page.evaluate((key) => JSON.parse(window.localStorage.getItem(key)!), TOKEN_STORAGE_KEY);
  expect(new Date(renewed.expiresAt).getTime()).toBeGreaterThan(Date.now() + 10 * 60_000);
  // EN: The cookie was used once and replaced. / VI: Cookie đã được dùng một lần và bị thay.
  expect((await refreshCookie(context))!.value).not.toBe(before!.value);
});

test("tabs renewing at the same moment stay signed in", async ({ context, page }) => {
  await signInWithTheForm(page);
  const tabs = [page, ...(await Promise.all([1, 2, 3].map(() => context.newPage())))];
  await expireTheAccessToken(page);

  // EN: Every tab finds the token expired at once. Two tabs sending the same cookie would look like a replay
  //     and end the session, so only one may renew: the Web Lock makes the others wait and use its result.
  //     The renewal is held for a second, so every tab is sure to ask while it is under way.
  // VI: Mọi tab cùng lúc thấy token hết hạn. Hai tab gửi cùng một cookie sẽ bị coi là dùng lại và kết thúc
  //     phiên, nên chỉ một tab được gia hạn: Web Lock bắt các tab khác chờ rồi dùng kết quả của nó. Lần gia
  //     hạn bị giữ lại một giây, để tab nào cũng chắc chắn hỏi trong lúc nó đang diễn ra.
  let renewals = 0;
  await context.route("**/api/auth/refresh", async (route) => {
    renewals += 1;
    await new Promise((resolve) => setTimeout(resolve, 1000));
    await route.continue();
  });
  await Promise.all(tabs.map((tab) => tab.goto("/profile")));
  for (const tab of tabs) await expect(tab.locator("main input").first()).toHaveValue("Sara E2E");
  await context.unrouteAll();
  expect(renewals).toBe(1);

  // EN: And the session is still alive for the next renewal. / VI: Và phiên vẫn còn sống cho lần gia hạn kế tiếp.
  await expireTheAccessToken(page);
  await page.reload();
  await expect(page.locator("main input").first()).toHaveValue("Sara E2E");
});

test("without Remember me the session ends with the browser", async ({ context, page }) => {
  await signInWithTheForm(page, false);

  expect(await refreshCookie(context)).toMatchObject({ httpOnly: true, expires: -1 });
});

test("signing out ends the session on the server as well", async ({ context, page }) => {
  await signInWithTheForm(page);
  const cookie = (await refreshCookie(context))!.value;

  await page.goto("/profile");
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible();
  expect(await refreshCookie(context)).toBeUndefined();

  // EN: A copy of the old cookie no longer renews anything. / VI: Bản sao cookie cũ không còn gia hạn được gì.
  const replay = await fetch(`${API_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { Cookie: `${REFRESH_COOKIE}=${cookie}` },
  });
  expect(replay.status).toBe(401);
});
