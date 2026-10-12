import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatDate, formatTimestamp, shortenText } from "./formatters";

describe("shortenText", () => {
  it("returns an empty string for missing text", () => {
    expect(shortenText(null, 10)).toBe("");
    expect(shortenText(undefined, 10)).toBe("");
    expect(shortenText("", 10)).toBe("");
  });

  it("keeps text that fits within the limit", () => {
    expect(shortenText("short", 5)).toBe("short");
  });

  it("truncates long text with an ellipsis within the limit", () => {
    const result = shortenText("Power outage in the city centre", 10);

    expect(result).toBe("Power o...");
    expect(result).toHaveLength(10);
  });
});

describe("formatTimestamp", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-01-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("formats past timestamps relative to now", () => {
    expect(formatTimestamp("2026-01-15T11:55:00Z")).toBe("5 minutes ago");
  });

  it("accepts epoch milliseconds and Date objects", () => {
    const twoHoursAgo = Date.now() - 2 * 60 * 60 * 1000;

    expect(formatTimestamp(twoHoursAgo)).toBe("about 2 hours ago");
    expect(formatTimestamp(new Date(twoHoursAgo))).toBe("about 2 hours ago");
  });
});

describe("formatDate", () => {
  it("formats a date in en-US short form", () => {
    const formatted = formatDate("2026-03-07T10:30:00");

    expect(formatted).toContain("Mar 7, 2026");
    expect(formatted).toMatch(/10:30\sAM/);
  });
});
