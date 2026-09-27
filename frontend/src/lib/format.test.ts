import { afterAll, beforeAll, describe, expect, it } from "vitest";

import {
  formatCompactCurrency,
  formatCompactNumber,
  formatCurrency,
  fromDateAndTimeParts,
  padTwo,
  parseCurrencyInput,
  toDateAndTimeParts,
  toDuration,
} from "./format";

describe("prices", () => {
  it("print in VND the same way in every locale", () => {
    expect(formatCurrency(18_500_000)).toBe("₫18,500,000");
    expect(formatCompactCurrency(18_500_000)).toBe("₫18.5M");
  });

  it("keep live counters short", () => {
    expect(formatCompactNumber(999)).toBe("999");
    expect(formatCompactNumber(1_284)).toBe("1.3K");
  });

  it.each([
    ["19,000,000", 19_000_000],
    ["₫19,000,000", 19_000_000],
    // EN: Vietnamese grouping uses dots. / VI: Cách nhóm số của tiếng Việt dùng dấu chấm.
    ["19.000.000", 19_000_000],
    [" 500000 ", 500_000],
  ])("read %j back as %i", (typed, amount) => {
    expect(parseCurrencyInput(typed)).toBe(amount);
  });

  it("read nothing from input without digits", () => {
    expect(parseCurrencyInput("")).toBeNull();
    expect(parseCurrencyInput("₫")).toBeNull();
  });
});

describe("the countdown's parts", () => {
  it("split a remainder without a calendar", () => {
    const ms = ((1 * 24 + 2) * 3600 + 3 * 60 + 4) * 1000 + 999;
    expect(toDuration(ms)).toEqual({ days: 1, hours: 2, minutes: 3, seconds: 4, totalMs: ms });
  });

  it("stop at zero rather than counting past the end", () => {
    expect(toDuration(-5_000)).toEqual({ days: 0, hours: 0, minutes: 0, seconds: 0, totalMs: 0 });
  });

  it("pad to two digits", () => {
    expect(padTwo(7)).toBe("07");
    expect(padTwo(42)).toBe("42");
  });
});

describe("the scheduling form's date and time", () => {
  // EN: The form edits the seller's own clock. / VI: Form chỉnh theo đồng hồ của chính người bán.
  const zone = process.env.TZ;
  beforeAll(() => {
    process.env.TZ = "Asia/Ho_Chi_Minh";
  });
  afterAll(() => {
    process.env.TZ = zone;
  });

  it("show an instant in the seller's time zone", () => {
    expect(toDateAndTimeParts("2026-09-27T03:21:00.000Z")).toEqual({ date: "2026-09-27", time: "10:21" });
  });

  it("turn the seller's date and time back into that instant", () => {
    expect(fromDateAndTimeParts("2026-09-27", "10:21")).toBe("2026-09-27T03:21:00.000Z");
  });

  it("give nothing for a missing or impossible date", () => {
    expect(fromDateAndTimeParts("", "10:21")).toBeNull();
    expect(fromDateAndTimeParts("2026-02-31x", "10:21")).toBeNull();
  });
});
