// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * EN: The store keeps its state in module variables, so each test imports a fresh copy.
 * VI: Store giữ trạng thái trong biến của module, nên mỗi test import một bản mới.
 */
async function freshStore() {
  vi.resetModules();
  return import("./token-store");
}

/** EN: A JWT-shaped token for a subject; the signature is never checked here. / VI: Một token dạng JWT cho một subject; chữ ký không được kiểm ở đây. */
function jwtFor(sub: string): string {
  const payload = btoa(JSON.stringify({ sub })).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `header.${payload}.signature`;
}

const inAMinute = () => new Date(Date.now() + 60_000).toISOString();
const aMinuteAgo = () => new Date(Date.now() - 60_000).toISOString();

beforeEach(() => {
  window.localStorage.clear();
});

describe("the access token store", () => {
  it("hands out a live token and keeps it across a reload", async () => {
    const first = await freshStore();
    first.setToken("abc", inAMinute());

    const afterReload = await freshStore();
    expect(afterReload.getToken()).toBe("abc");
    expect(afterReload.tokenExpiresIn()).toBeGreaterThan(50_000);
  });

  it("withholds an expired token but remembers that a session may still be renewed", async () => {
    const store = await freshStore();
    store.setToken("old", aMinuteAgo());

    expect(store.getToken()).toBeNull();
    expect(store.hasSession()).toBe(true);
    expect(store.tokenExpiresIn()).toBeLessThan(0);
  });

  it("forgets the session entirely on sign-out", async () => {
    const store = await freshStore();
    store.setToken("abc", inAMinute());
    store.clearToken();

    expect(store.getToken()).toBeNull();
    expect(store.hasSession()).toBe(false);
    expect(window.localStorage.getItem("nexbid.token")).toBeNull();
  });

  it("follows a renewal made in another tab", async () => {
    const store = await freshStore();
    store.setToken("mine", aMinuteAgo());
    const listener = vi.fn();
    store.onTokenChange(listener);

    const renewed = JSON.stringify({ token: "from-the-other-tab", expiresAt: inAMinute() });
    window.localStorage.setItem("nexbid.token", renewed);
    window.dispatchEvent(new StorageEvent("storage", { key: "nexbid.token", newValue: renewed }));

    expect(listener).toHaveBeenCalled();
    expect(store.getToken()).toBe("from-the-other-tab");
  });

  it("reads who a token belongs to, and nothing from a malformed one", async () => {
    const store = await freshStore();
    expect(store.subjectOf(jwtFor("user-42"))).toBe("user-42");
    expect(store.subjectOf("not-a-jwt")).toBeNull();
    expect(store.subjectOf(null)).toBeNull();
  });
});
