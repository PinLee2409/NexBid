/** EN: Sign-in pages; returning to one after signing in would loop. / VI: Các trang đăng nhập; quay lại đó sau khi đăng nhập sẽ thành vòng lặp. */
const SIGN_IN_PAGES = ["/login", "/register", "/admin/login"];

/**
 * EN: Where to go after signing in. Only a path on this site is accepted — never another origin, and never
 *     the sign-in pages themselves — so a crafted `?redirect=` link cannot send a reader elsewhere.
 * VI: Nơi cần tới sau khi đăng nhập. Chỉ chấp nhận đường dẫn trong chính site này — không bao giờ là origin
 *     khác, cũng không phải chính các trang đăng nhập — nên link `?redirect=` bị chế không đưa người đọc đi nơi khác.
 */
export function safeRedirect(value: string | null | undefined): string | null {
  if (!value || !value.startsWith("/")) return null;
  // EN: `//host` and `/\host` are read by browsers as another origin. / VI: Trình duyệt hiểu `//host` và `/\host` là origin khác.
  if (value.startsWith("//") || value.startsWith("/\\")) return null;
  const path = value.split(/[?#]/, 1)[0];
  if (SIGN_IN_PAGES.includes(path)) return null;
  return value;
}

/**
 * EN: A sign-in page — the marketplace's, or the admin console's — coming back to `path` afterwards.
 * VI: Một trang đăng nhập — của sàn, hoặc của trang quản trị — sau đó quay lại `path`.
 */
export function loginHref(path: string, signInPage: "/login" | "/admin/login" = "/login"): string {
  const back = safeRedirect(path);
  return back && back !== "/" ? `${signInPage}?redirect=${encodeURIComponent(back)}` : signInPage;
}
