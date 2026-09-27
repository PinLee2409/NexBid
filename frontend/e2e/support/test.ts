import type { BrowserContext } from "@playwright/test";

import { createLot, login, TOKEN_STORAGE_KEY } from "./stack";

export { expect, test } from "@playwright/test";

export interface Lot {
  id: string;
  name: string;
  startTime: string;
}

/** EN: What global setup created for this run. / VI: Những gì global setup đã tạo cho lần chạy này. */
export interface E2EData {
  run: string;
  users: { seller: string; admin: string; alex: string; sara: string };
}

export const data: E2EData = JSON.parse(process.env.E2E_DATA ?? "null");

/** EN: A name no other test or attempt uses. / VI: Một cái tên không test hay lần chạy nào khác dùng. */
export function unique(label: string): string {
  return `${label} ${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

/**
 * EN: A fresh lot for one test: live at once when `open`, otherwise waiting for approval. Tokens are taken
 *     after global setup granted the roles, so they carry them.
 * VI: Một lô mới cho một test: mở ngay nếu `open`, không thì chờ duyệt. Token được lấy sau khi global setup đã
 *     cấp vai trò, nên mang theo vai trò đó.
 */
export async function newLot(label: string, open: boolean): Promise<Lot> {
  const name = unique(`E2E ${data.run} ${label}`);
  const lot = await createLot(await login(data.users.seller), await login(data.users.admin), name, open);
  return { ...lot, name };
}

/**
 * EN: Signs a browser context in the way the app does after its login form: a session in localStorage,
 *     written before any page script runs.
 * VI: Đăng nhập một browser context theo cách app làm sau form đăng nhập: phiên nằm trong localStorage,
 *     được ghi trước khi bất kỳ script nào của trang chạy.
 */
export async function signIn(context: BrowserContext, email: string): Promise<void> {
  const session = await login(email);
  await context.addInitScript(
    ([key, value]) => window.localStorage.setItem(key, value),
    [TOKEN_STORAGE_KEY, JSON.stringify(session)] as const,
  );
}

/** EN: `1,100,000`, as the bid panel prints amounts. / VI: `1,100,000`, như bảng trả giá in số tiền. */
export function grouped(amount: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(amount);
}
