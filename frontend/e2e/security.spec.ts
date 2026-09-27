import { PASSWORD, register } from "./support/stack";
import { expect, test, unique } from "./support/test";

/**
 * EN: The browser-side defences: a Content Security Policy with a fresh nonce on every page, the other safety
 *     headers, and sign-in slowing down after repeated wrong passwords. The rest of the suite, which fails on
 *     any console error, shows the policy blocks nothing the app itself needs.
 * VI: Các lớp phòng vệ phía trình duyệt: Content Security Policy với nonce mới trên mọi trang, các header an toàn
 *     khác, và đăng nhập chậm lại sau nhiều lần sai mật khẩu. Phần còn lại của bộ test, vốn trượt khi có bất kỳ
 *     lỗi console nào, cho thấy policy không chặn thứ gì mà chính app cần.
 */

const nonceOf = (csp: string) => /'nonce-([^']+)'/.exec(csp)?.[1];

test("every page gets a strict policy with a nonce of its own", async ({ request }) => {
  const first = await request.get("/");
  const second = await request.get("/");
  const csp = first.headers()["content-security-policy"];

  expect(csp).toContain("script-src 'self' 'nonce-");
  expect(csp).toContain("'strict-dynamic'");
  expect(csp).toContain("frame-ancestors 'none'");
  expect(csp).toContain("object-src 'none'");
  expect(nonceOf(csp)).not.toBe(nonceOf(second.headers()["content-security-policy"]));
  // EN: The scripts Next.js writes into the page carry the same nonce. / VI: Script mà Next.js ghi vào trang mang đúng nonce đó.
  expect(await first.text()).toContain(`nonce="${nonceOf(csp)}"`);

  expect(first.headers()).toMatchObject({
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "referrer-policy": "strict-origin-when-cross-origin",
  });
  expect(first.headers()["x-powered-by"]).toBeUndefined();
});

test("markup injected into a page cannot run script", async ({ page, consoleErrors }) => {
  await page.goto("/");

  // EN: The usual way an XSS gets in: HTML with an inline event handler. / VI: Cách XSS thường lọt vào: HTML có handler sự kiện inline.
  const violated = await page.evaluate(
    () =>
      new Promise<string>((resolve) => {
        document.addEventListener("securitypolicyviolation", (event) => resolve(event.violatedDirective), { once: true });
        document.body.insertAdjacentHTML(
          "beforeend",
          '<img src="/no-such-image.png" onerror="window.__injected = true" alt="">',
        );
      }),
  );

  expect(violated).toMatch(/^script-src/);
  expect(await page.evaluate(() => (window as { __injected?: boolean }).__injected)).toBeUndefined();
  // EN: The browser reports the blocked handler on the console; here that is the expected outcome.
  // VI: Trình duyệt báo handler bị chặn lên console; ở đây đó là kết quả mong đợi.
  consoleErrors.splice(0, consoleErrors.length, ...consoleErrors.filter((error) => !/Content Security Policy/.test(error)));
});

test("sign-in slows down after five wrong passwords", async ({ page }) => {
  const email = `${unique("e2e-guarded").replace(" ", "-")}@nexbid.test`;
  await register(email, "Guarded E2E");

  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  const password = page.getByRole("textbox", { name: "Password", exact: true });
  const signIn = page.getByRole("button", { name: "Sign in" });
  for (let attempt = 0; attempt < 5; attempt++) {
    await password.fill(`wrong-${attempt}`);
    await signIn.click();
    await expect(page.getByText("Incorrect email or password.")).toBeVisible();
  }

  // EN: Now even the right password waits, and the form says for how long.
  // VI: Giờ cả mật khẩu đúng cũng phải chờ, và form cho biết phải chờ bao lâu.
  await password.fill(PASSWORD);
  await signIn.click();
  await expect(page.getByText(/Too many failed sign-ins\. Try again in \d+ minutes?\./)).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});
