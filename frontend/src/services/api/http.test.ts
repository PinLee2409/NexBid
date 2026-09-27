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

describe("what the client makes of an answer", () => {
  const stub = (response: Response | Error) =>
    vi.stubGlobal("fetch", vi.fn(async () => {
      if (response instanceof Error) throw response;
      return response;
    }));

  it("reports an unreachable server as an error of its own, not a crash", async () => {
    const { api } = await freshClient();
    stub(new TypeError("Failed to fetch"));
    await expect(api("/api/categories")).rejects.toMatchObject({ status: 0, code: "INTERNAL_ERROR" });
  });

  it.each([
    [404, "NOT_FOUND"],
    [403, "ACCESS_DENIED"],
    [401, "NOT_AUTHENTICATED"],
    [502, "INTERNAL_ERROR"],
  ])("reads a %i page outside the envelope as %s", async (status, code) => {
    const { api } = await freshClient();
    stub(new Response("<html>Bad gateway</html>", { status }));
    await expect(api("/api/categories")).rejects.toMatchObject({ status, code });
  });

  it("keeps the server's code, field errors and how long to wait", async () => {
    const { api } = await freshClient();
    stub(new Response(
      JSON.stringify({ success: false, code: "BID_RATE_LIMITED", message: "Slow down", details: { amount: "too often" } }),
      { status: 429, headers: { "Retry-After": "7" } },
    ));
    await expect(api("/api/auctions/a1/bids", { method: "POST", body: {} })).rejects.toMatchObject({
      code: "BID_RATE_LIMITED",
      details: { amount: "too often" },
      retryAfterSeconds: 7,
    });
  });

  it("repeats list parameters and leaves out empty ones", async () => {
    const { api } = await freshClient();
    const fetchMock = vi.fn<(url: string) => Promise<Response>>(async () => new Response(JSON.stringify({ success: true, data: [] })));
    vi.stubGlobal("fetch", fetchMock);

    await api("/api/auctions", { query: { status: ["ACTIVE", "SCHEDULED"], search: "", page: 2, sort: undefined } });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auctions?status=ACTIVE&status=SCHEDULED&page=2");
  });

  it("sends JSON with its content type, and a form as it is", async () => {
    const { api } = await freshClient();
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
      async () => new Response(JSON.stringify({ success: true, data: null })),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api("/api/x", { method: "POST", body: { a: 1 } });
    await api("/api/y", { method: "POST", form: new FormData() });
    const [json, form] = fetchMock.mock.calls.map((call) => call[1]!.headers as Record<string, string>);
    expect(json["Content-Type"]).toBe("application/json");
    expect(form["Content-Type"]).toBeUndefined();
  });
});

describe("keeping an open page's token fresh", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renews a minute before the token runs out", async () => {
    vi.useFakeTimers();
    const { store } = await freshClient();
    const { keepSessionFresh } = await import("./session-refresh");
    store.setToken("old", new Date(Date.now() + 2 * 60_000).toISOString());
    const calls = fakeBackend({ "/api/auth/refresh": [renewal("new")] });

    keepSessionFresh();
    await vi.advanceTimersByTimeAsync(59_000);
    expect(calls).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(2_000);

    expect(calls.map((call) => call.url)).toEqual(["/api/auth/refresh"]);
    expect(store.getToken()).toBe("new");
  });

  it("catches up as soon as a tab that slept comes back", async () => {
    vi.useFakeTimers();
    const { store } = await freshClient();
    const { keepSessionFresh } = await import("./session-refresh");
    store.setToken("stale", new Date(Date.now() + 10 * 60_000).toISOString());
    keepSessionFresh();
    const calls = fakeBackend({ "/api/auth/refresh": [renewal("new")] });

    // EN: Nine and a half minutes pass while the tab sleeps: the clock moves, its timers do not fire.
    // VI: Chín phút rưỡi trôi qua khi tab ngủ: đồng hồ chạy, nhưng hẹn giờ của tab thì không.
    vi.setSystemTime(Date.now() + 9.5 * 60_000);
    expect(calls).toHaveLength(0);

    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
    await vi.waitFor(() => expect(store.getToken()).toBe("new"));
    expect(calls).toHaveLength(1);
  });

});
