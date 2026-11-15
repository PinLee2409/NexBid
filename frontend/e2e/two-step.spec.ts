import { createHmac } from "node:crypto";

import { PASSWORD, login, register } from "./support/stack";
import { data, expect, signIn, test, unique } from "./support/test";

const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080";
const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/** EN: The 6-digit code an authenticator app shows for a base32 secret. / VI: Mã 6 số ứng dụng xác thực hiện cho một secret base32. */
function totp(secret: string, offsetSteps = 0): string {
  let bits = "";
  for (const char of secret) bits += ALPHABET.indexOf(char).toString(2).padStart(5, "0");
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((byte) => parseInt(byte, 2)));
  const step = Buffer.alloc(8);
  step.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000) + offsetSteps));
  const hash = createHmac("sha1", key).update(step).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}

async function call<T>(path: string, token: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${path} ${response.status} ${await response.text()}`);
  return (await response.json()).data as T;
}

/**
 * EN: With two-step sign-in on, the password is followed by a code from the app.
 * VI: Khi bật đăng nhập hai bước, sau mật khẩu là mã từ ứng dụng.
 */
test("a buyer with two-step sign-in enters the app's code after the password", async ({ page }) => {
  const email = `${unique("two-step").toLowerCase().replace(/[^a-z0-9-]/g, "-")}@e2e.nexbid.test`;
  await register(email, "Two Step Buyer");
  const session = await login(email);
  const { secret } = await call<{ secret: string }>("/api/users/me/2fa/setup", session.token);
  await call("/api/users/me/2fa/enable", session.token, { code: totp(secret) });

  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page.getByRole("heading", { name: "Enter your code" })).toBeVisible();
  await page.getByLabel("Code").fill("000000");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("That code does not match.")).toBeVisible();
  // EN: The code that turned it on is spent, so the next step's code is used. / VI: Mã dùng để bật đã hết hiệu lực, nên dùng mã của bước kế tiếp.
  await page.getByLabel("Code").fill(totp(secret, 1));
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("Signed in").first()).toBeVisible();

  await page.goto("/profile");
  await expect(page.getByText("Two-step sign-in is on")).toBeVisible();
});

/**
 * EN: The admin's shill-bidding queue opens and explains itself.
 * VI: Hàng chờ nghi vấn tự trả giá của admin mở được và tự giải thích.
 */
test("an admin opens the shill bidding queue", async ({ page }) => {
  await signIn(page.context(), data.users.admin);
  await page.goto("/admin/trust");
  await expect(page.getByRole("heading", { name: "Shill bidding", level: 1 })).toBeVisible();
});
