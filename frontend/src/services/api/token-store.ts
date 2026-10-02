/**
 * EN: The signed-in user's access token (a JWT, valid 15 minutes), kept in localStorage so a reload stays
 *     signed in. The refresh token that renews it is an HttpOnly cookie scripts never see. The one place
 *     that reads or writes the access token; the HTTP client, the session and the socket all listen here.
 * VI: Access token của người đang đăng nhập (một JWT, hạn 15 phút), lưu trong localStorage để tải lại trang
 *     vẫn còn đăng nhập. Refresh token dùng để gia hạn nó là cookie HttpOnly mà script không bao giờ thấy.
 *     Chỗ duy nhất đọc hoặc ghi access token; HTTP client, session và socket đều lắng nghe ở đây.
 */

const STORAGE_KEY = "nexbid.token";

interface StoredToken {
  token: string;
  expiresAt: string;
}

let current: StoredToken | null = null;
let loaded = false;
let watchingOtherTabs = false;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

function read(): StoredToken | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredToken) : null;
  } catch {
    return null;
  }
}

/**
 * EN: Another tab renewed, signed in or signed out: follow it. Started by whichever function this tab calls
 *     first — a tab that begins by signing in must follow the others too.
 * VI: Tab khác vừa gia hạn, đăng nhập hay đăng xuất: làm theo. Được bật bởi bất kỳ hàm nào tab này gọi đầu
 *     tiên — tab bắt đầu bằng việc đăng nhập cũng phải theo được các tab khác.
 */
function watchOtherTabs(): void {
  if (watchingOtherTabs || typeof window === "undefined") return;
  watchingOtherTabs = true;
  window.addEventListener("storage", (event) => {
    if (event.key !== STORAGE_KEY && event.key !== null) return;
    current = read();
    notify();
  });
}

function load(): void {
  watchOtherTabs();
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  current = read();
}

/** EN: The token, or null when absent or expired. / VI: Token, hoặc null khi không có hay đã hết hạn. */
export function getToken(): string | null {
  load();
  if (!current || new Date(current.expiresAt).getTime() <= Date.now()) return null;
  return current.token;
}

/**
 * EN: Whether a session may still exist: a token is stored, even an expired one, so the refresh cookie is
 *     worth trying. Signing out or a refused refresh removes it.
 * VI: Có thể còn phiên đăng nhập hay không: vẫn còn token được lưu, kể cả đã hết hạn, nên đáng thử cookie
 *     refresh. Đăng xuất hoặc refresh bị từ chối sẽ xoá nó.
 */
export function hasSession(): boolean {
  load();
  return current !== null;
}

/** EN: Milliseconds until the token runs out; negative once it has. / VI: Số mili giây tới khi token hết hạn; âm khi đã hết. */
export function tokenExpiresIn(): number {
  load();
  return current ? new Date(current.expiresAt).getTime() - Date.now() : -Infinity;
}

/** EN: Re-reads storage, where another tab may have put a newer token. / VI: Đọc lại bộ nhớ, nơi tab khác có thể đã để token mới hơn. */
export function reloadToken(): void {
  if (typeof window === "undefined") return;
  watchOtherTabs();
  loaded = true;
  const stored = read();
  if (stored?.token === current?.token) return;
  current = stored;
  notify();
}

/** EN: Who the token belongs to (its `sub`), or null. / VI: Token thuộc về ai (trường `sub`), hoặc null. */
export function subjectOf(token: string | null): string | null {
  if (!token) return null;
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return (JSON.parse(atob(payload)) as { sub?: string }).sub ?? null;
  } catch {
    return null;
  }
}

/**
 * EN: The roles the token was issued with. A role granted later is only honoured by a renewed token.
 * VI: Các vai trò có trong token lúc được cấp. Vai trò được cấp sau đó chỉ có hiệu lực với token đã gia hạn.
 */
export function rolesOf(token: string | null): string[] {
  if (!token) return [];
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const roles = (JSON.parse(atob(payload)) as { roles?: unknown }).roles;
    return Array.isArray(roles) ? roles.filter((role): role is string => typeof role === "string") : [];
  } catch {
    return [];
  }
}

export function setToken(token: string, expiresAt: string): void {
  watchOtherTabs();
  current = { token, expiresAt };
  loaded = true;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {
    // EN: Private mode: the token lives for this page only. / VI: Chế độ riêng tư: token chỉ sống trong trang này.
  }
  notify();
}

export function clearToken(): void {
  watchOtherTabs();
  const had = current !== null;
  current = null;
  loaded = true;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // EN: Nothing stored to remove. / VI: Không có gì để xoá.
  }
  if (had) notify();
}

/** EN: Called whenever the token appears, changes or goes away. / VI: Được gọi mỗi khi token xuất hiện, đổi hoặc mất đi. */
export function onTokenChange(listener: () => void): () => void {
  watchOtherTabs();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
