/**
 * EN: The signed-in user's JWT, kept in localStorage so a reload stays signed in. The one place that
 *     reads or writes it; the HTTP client and the session both listen here.
 * VI: JWT của người đang đăng nhập, lưu trong localStorage để tải lại trang vẫn còn đăng nhập. Chỗ duy nhất
 *     đọc hoặc ghi nó; HTTP client và session đều lắng nghe ở đây.
 */

const STORAGE_KEY = "nexbid.token";

interface StoredToken {
  token: string;
  expiresAt: string;
}

let current: StoredToken | null = null;
let loaded = false;
const listeners = new Set<() => void>();

function load(): void {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    current = raw ? (JSON.parse(raw) as StoredToken) : null;
  } catch {
    current = null;
  }
}

/** EN: The token, or null when absent or expired. / VI: Token, hoặc null khi không có hay đã hết hạn. */
export function getToken(): string | null {
  load();
  if (!current) return null;
  if (new Date(current.expiresAt).getTime() <= Date.now()) {
    clearToken();
    return null;
  }
  return current.token;
}

export function setToken(token: string, expiresAt: string): void {
  current = { token, expiresAt };
  loaded = true;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {
    // EN: Private mode: the token lives for this page only. / VI: Chế độ riêng tư: token chỉ sống trong trang này.
  }
  for (const listener of listeners) listener();
}

export function clearToken(): void {
  const had = current !== null;
  current = null;
  loaded = true;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // EN: Nothing stored to remove. / VI: Không có gì để xoá.
  }
  if (had) for (const listener of listeners) listener();
}

/** EN: Called whenever the token appears or goes away. / VI: Được gọi mỗi khi token xuất hiện hoặc mất đi. */
export function onTokenChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
