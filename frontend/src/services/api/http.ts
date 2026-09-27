import type { ErrorCode } from "@/types";

import { RENEW_BEFORE_MS, refreshSession } from "./session-refresh";
import { clearToken, getToken, hasSession, tokenExpiresIn } from "./token-store";

/**
 * EN: The one way the frontend talks to the backend. In the browser it calls its own origin
 *     (`/api/...`), which next.config rewrites to the backend; on the server it calls the backend
 *     directly at NEXBID_API_URL. Every answer is unwrapped from the envelope of spec §28.
 * VI: Cách duy nhất frontend nói chuyện với backend. Trên trình duyệt nó gọi chính origin của mình
 *     (`/api/...`), next.config chuyển tiếp sang backend; trên server nó gọi thẳng backend qua
 *     NEXBID_API_URL. Mọi câu trả lời được bóc khỏi envelope của spec §28.
 */

const SERVER_BASE_URL = process.env.NEXBID_API_URL ?? "http://localhost:8080";

/** EN: A refusal from the server, or no answer at all. / VI: Server từ chối, hoặc không trả lời được. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorCode,
    message: string,
    readonly details?: Record<string, string>,
    /** EN: Seconds, from Retry-After on a 429. / VI: Số giây, lấy từ Retry-After khi gặp 429. */
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type QueryValue = string | number | boolean | null | undefined | readonly (string | number)[];

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** EN: Sent as JSON. / VI: Gửi dưới dạng JSON. */
  body?: unknown;
  /** EN: Sent as multipart, e.g. photo uploads. / VI: Gửi dạng multipart, ví dụ tải ảnh lên. */
  form?: FormData;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
}

function queryString(query: RequestOptions["query"]): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    // EN: Lists repeat the key (?status=A&status=B), which Spring binds to a List.
    // VI: Danh sách lặp lại khoá (?status=A&status=B), Spring gom thành List.
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)));
    else params.append(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : "";
}

export async function api<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const onServer = typeof window === "undefined";
  const url = `${onServer ? SERVER_BASE_URL : ""}${path}${queryString(options.query)}`;
  // EN: The auth endpoints manage the session themselves. / VI: Các endpoint xác thực tự lo phiên đăng nhập.
  const authCall = path.startsWith("/api/auth/");

  // EN: A token about to run out (or already out, while a session may remain) is renewed first.
  // VI: Token sắp hết hạn (hoặc đã hết mà có thể vẫn còn phiên) được gia hạn trước.
  if (!onServer && !authCall && hasSession() && tokenExpiresIn() <= RENEW_BEFORE_MS) {
    await refreshSession();
  }

  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (options.form) {
    body = options.form;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  // EN: Server rendering is always anonymous: the token lives in the browser only.
  // VI: Render trên server luôn ẩn danh: token chỉ nằm trong trình duyệt.
  const token = onServer ? null : getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
      cache: "no-store",
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError(0, "INTERNAL_ERROR", "The server could not be reached");
  }

  const text = await response.text();
  let payload: { success?: boolean; data?: T; code?: ErrorCode; message?: string; details?: Record<string, string> } | null = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = null;
  }

  if (!response.ok || payload?.success === false) {
    // EN: A token the server no longer accepts gets one renewal and one retry; if the session cannot be
    //     renewed (signed out elsewhere, account blocked), the user is signed out.
    // VI: Token mà server không còn chấp nhận được gia hạn một lần và thử lại một lần; nếu không gia hạn được
    //     (đã đăng xuất ở nơi khác, tài khoản bị khoá) thì người dùng bị đăng xuất.
    if (response.status === 401 && token && !authCall) {
      if (!retried && (await refreshSession(token))) return api<T>(path, options, true);
      clearToken();
    }
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiError(
      response.status,
      payload?.code ?? codeForStatus(response.status),
      payload?.message ?? response.statusText,
      payload?.details,
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
    );
  }

  return (payload?.data ?? undefined) as T;
}

/** EN: For answers that did not come in the envelope, e.g. a proxy error page. / VI: Cho câu trả lời không nằm trong envelope, ví dụ trang lỗi của proxy. */
function codeForStatus(status: number): ErrorCode {
  if (status === 401) return "NOT_AUTHENTICATED";
  if (status === 403) return "ACCESS_DENIED";
  if (status === 404) return "NOT_FOUND";
  return "INTERNAL_ERROR";
}

/** EN: The error code, whatever was thrown. / VI: Mã lỗi, bất kể thứ bị ném ra là gì. */
export function errorCodeOf(error: unknown): ErrorCode {
  return error instanceof ApiError ? error.code : "INTERNAL_ERROR";
}
