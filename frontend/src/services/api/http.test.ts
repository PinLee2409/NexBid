// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * EN: The session side of the HTTP client: when it renews the access token, when it retries, and when it
 *     gives up and signs the user out. `fetch` is replaced by a small fake backend.
 * VI: Phần phiên đăng nhập của HTTP client: khi nào nó gia hạn access token, khi nào thử lại, và khi nào nó
 *     bỏ cuộc và đăng xuất người dùng. `fetch` được thay bằng một backend giả nhỏ.
 */

type Answer = { status: number; data?: unknown };

const envelope = ({ status, data }: Answer) =>
  new Response(JSON.stringify(status < 400 ? { success: true, data } : { success: false, code: "NOT_AUTHENTICATED" }), {
    status,
    headers: { "Content-Type": "application/json" },
  });

/** EN: Answers each URL from its own queue, recording what was asked and with which token. / VI: Trả lời mỗi URL theo hàng đợi riêng, ghi lại đã hỏi gì và bằng token nào. */
function fakeBackend(answers: Record<string, Answer[]>) {
  const calls: Array<{ url: string; token: string | null }> = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const auth = (init?.headers as Record<string, string> | undefined)?.Authorization ?? null;
    calls.push({ url, token: auth?.replace("Bearer ", "") ?? null });
    const queue = answers[url];
    if (!queue?.length) throw new Error(`Unexpected request to ${url}`);
    return envelope(queue.length > 1 ? queue.shift()! : queue[0]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

const renewal = (token: string): Answer => ({
  status: 200,
  data: { accessToken: token, expiresAt: new Date(Date.now() + 15 * 60_000).toISOString() },
});

async function freshClient() {
  vi.resetModules();
  const store = await import("./token-store");
  const { api, ApiError } = await import("./http");
  return { store, api, ApiError };
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("renewing the session around requests", () => {
  it("renews a token about to run out before sending the request", async () => {
    const { store, api } = await freshClient();
    store.setToken("old", new Date(Date.now() + 30_000).toISOString());
    const calls = fakeBackend({
      "/api/auth/refresh": [renewal("new")],
      "/api/users/me": [{ status: 200, data: { id: "u1" } }],
    });

    await expect(api("/api/users/me")).resolves.toEqual({ id: "u1" });
    expect(calls).toEqual([
      { url: "/api/auth/refresh", token: null },
      { url: "/api/users/me", token: "new" },
    ]);
  });

  it("renews once and retries once when a request is refused with 401", async () => {
    const { store, api } = await freshClient();
    store.setToken("revoked", new Date(Date.now() + 10 * 60_000).toISOString());
    const calls = fakeBackend({
      "/api/users/me": [{ status: 401 }, { status: 200, data: { id: "u1" } }],
      "/api/auth/refresh": [renewal("new")],
    });

    await expect(api("/api/users/me")).resolves.toEqual({ id: "u1" });
    expect(calls.map((call) => `${call.url} ${call.token}`)).toEqual([
      "/api/users/me revoked",
      "/api/auth/refresh null",
      "/api/users/me new",
    ]);
  });

  it("does not loop when the renewed token is refused as well", async () => {
    const { store, api, ApiError } = await freshClient();
    store.setToken("t1", new Date(Date.now() + 10 * 60_000).toISOString());
    const calls = fakeBackend({
      "/api/users/me": [{ status: 401 }],
      "/api/auth/refresh": [renewal("t2")],
    });

    await expect(api("/api/users/me")).rejects.toBeInstanceOf(ApiError);
    expect(calls).toHaveLength(3);
    expect(store.hasSession()).toBe(false);
  });

  it("signs the user out when the session can no longer be renewed", async () => {
    const { store, api } = await freshClient();
    store.setToken("t1", new Date(Date.now() + 10 * 60_000).toISOString());
    fakeBackend({ "/api/users/me": [{ status: 401 }], "/api/auth/refresh": [{ status: 401 }] });

    await expect(api("/api/users/me")).rejects.toMatchObject({ status: 401 });
    expect(store.hasSession()).toBe(false);
  });

  it("never renews around the auth endpoints themselves", async () => {
    const { store, api } = await freshClient();
    store.setToken("old", new Date(Date.now() - 1_000).toISOString());
    const calls = fakeBackend({ "/api/auth/login": [{ status: 401 }] });

    await expect(api("/api/auth/login", { method: "POST", body: {} })).rejects.toMatchObject({ status: 401 });
    expect(calls.map((call) => call.url)).toEqual(["/api/auth/login"]);
  });

  it("leaves a visitor without a session alone", async () => {
    const { api } = await freshClient();
    const calls = fakeBackend({ "/api/categories": [{ status: 200, data: [] }] });

    await api("/api/categories");
    expect(calls).toEqual([{ url: "/api/categories", token: null }]);
  });
});

describe("one renewal at a time", () => {
  it("shares a renewal between requests that need it together", async () => {
    const { store } = await freshClient();
    const { refreshSession } = await import("./session-refresh");
    store.setToken("old", new Date(Date.now() - 1_000).toISOString());
    const calls = fakeBackend({ "/api/auth/refresh": [renewal("new")] });

    const results = await Promise.all([refreshSession(), refreshSession(), refreshSession()]);
    expect(results).toEqual([true, true, true]);
    expect(calls).toHaveLength(1);
  });

  it("skips the server when another tab has already renewed", async () => {
    const { store } = await freshClient();
    const { refreshSession } = await import("./session-refresh");
    store.setToken("old", new Date(Date.now() - 1_000).toISOString());
    const calls = fakeBackend({});
    // EN: The other tab wrote its renewal while this one waited for the lock. / VI: Tab kia đã ghi kết quả gia hạn trong lúc tab này chờ khoá.
    window.localStorage.setItem(
      "nexbid.token",
      JSON.stringify({ token: "renewed-elsewhere", expiresAt: new Date(Date.now() + 15 * 60_000).toISOString() }),
    );

    await expect(refreshSession()).resolves.toBe(true);
    expect(calls).toHaveLength(0);
    expect(store.getToken()).toBe("renewed-elsewhere");
  });
});
