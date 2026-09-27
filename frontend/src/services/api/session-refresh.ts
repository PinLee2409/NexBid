import type { ApiLogin } from "./dto";
import { clearToken, getToken, hasSession, onTokenChange, reloadToken, setToken, tokenExpiresIn } from "./token-store";

/**
 * EN: Renews the 15-minute access token with the refresh cookie (`POST /api/auth/refresh`). The server
 *     replaces the cookie on every renewal and treats an old cookie coming back as theft, so two renewals
 *     must never race: one at a time per tab, and one at a time across tabs through a Web Lock.
 * VI: Gia hạn access token 15 phút bằng cookie refresh (`POST /api/auth/refresh`). Server thay cookie sau mỗi
 *     lần gia hạn và coi cookie cũ quay lại là bị đánh cắp, nên hai lần gia hạn không bao giờ được chạy đua:
 *     mỗi tab một lần một, và giữa các tab cũng một lần một nhờ Web Lock.
 */

/** EN: Renew this long before the token runs out. / VI: Gia hạn trước khi token hết hạn chừng này. */
export const RENEW_BEFORE_MS = 60_000;

const LOCK_NAME = "nexbid-session-refresh";

let inFlight: Promise<boolean> | null = null;

/**
 * EN: True once a fresh token is stored; false when there is no session to renew. `refused` is a token the
 *     server just turned down: it must be replaced even though its clock has not run out.
 * VI: True khi đã có token mới; false khi không còn phiên để gia hạn. `refused` là token server vừa từ chối:
 *     nó phải được thay dù chưa tới giờ hết hạn.
 */
export function refreshSession(refused?: string): Promise<boolean> {
  inFlight ??= withLock(refused).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function withLock(refused?: string): Promise<boolean> {
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
  const renew = () => renewUnlessDone(refused);
  return locks ? await locks.request(LOCK_NAME, renew) : renew();
}

async function renewUnlessDone(refused?: string): Promise<boolean> {
  // EN: A tab that held the lock before this one may already have renewed. / VI: Tab giữ khoá trước tab này có thể đã gia hạn rồi.
  reloadToken();
  if (tokenExpiresIn() > RENEW_BEFORE_MS && getToken() !== refused) return true;

  let response: Response;
  try {
    response = await fetch("/api/auth/refresh", {
      method: "POST",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    // EN: Offline: keep what we have and try again later. / VI: Mất mạng: giữ nguyên và thử lại sau.
    return false;
  }

  if (!response.ok) {
    // EN: The session is over (signed out, expired, replayed, blocked). / VI: Phiên đã kết thúc (đăng xuất, hết hạn, bị dùng lại, bị khoá).
    if (response.status === 401 || response.status === 403) clearToken();
    return false;
  }
  const payload = (await response.json().catch(() => null)) as { data?: ApiLogin } | null;
  if (!payload?.data) return false;
  setToken(payload.data.accessToken, payload.data.expiresAt);
  return true;
}

let keepingAlive = false;
let timer: ReturnType<typeof setTimeout> | undefined;

function schedule(): void {
  clearTimeout(timer);
  if (!hasSession()) return;
  const wait = Math.max(0, tokenExpiresIn() - RENEW_BEFORE_MS);
  timer = setTimeout(() => void refreshSession(), wait);
}

/**
 * EN: Keeps an open page's token fresh, so a socket reconnecting after a network blip still has one to
 *     present. A background tab's timer may be delayed; coming back to the tab catches up.
 * VI: Giữ token của trang đang mở luôn còn hạn, để socket kết nối lại sau khi rớt mạng vẫn có token để trình.
 *     Hẹn giờ ở tab nền có thể bị trễ; quay lại tab thì bù lại ngay.
 */
export function keepSessionFresh(): void {
  if (keepingAlive || typeof window === "undefined") return;
  keepingAlive = true;
  onTokenChange(schedule);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && hasSession() && tokenExpiresIn() <= RENEW_BEFORE_MS) {
      void refreshSession();
    }
  });
  schedule();
}
