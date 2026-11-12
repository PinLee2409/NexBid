import { describe, expect, it } from "vitest";

import { loginHref, safeRedirect } from "./redirect";

describe("where to go after signing in", () => {
  it.each(["/admin/categories", "/auctions?status=ACTIVE", "/auctions/abc#bids"])("accepts the site path %j", (path) => {
    expect(safeRedirect(path)).toBe(path);
  });

  it.each([
    "//evil.com",
    "/\\evil.com",
    "https://evil.com",
    "javascript:alert(1)",
    "admin",
    "/login",
    "/login?redirect=/admin",
    "/register",
    "/admin/login",
    "",
    null,
    undefined,
  ])("refuses %j", (value) => {
    expect(safeRedirect(value)).toBeNull();
  });
});

describe("the sign-in link", () => {
  it("carries the page to come back to", () => {
    expect(loginHref("/admin/categories?x=1")).toBe("/login?redirect=%2Fadmin%2Fcategories%3Fx%3D1");
  });

  it("can lead to the admin console's own sign-in page", () => {
    expect(loginHref("/admin/users", "/admin/login")).toBe("/admin/login?redirect=%2Fadmin%2Fusers");
  });

  it("is plain when there is nowhere worth returning to", () => {
    expect(loginHref("/")).toBe("/login");
    expect(loginHref("//evil.com")).toBe("/login");
  });
});
