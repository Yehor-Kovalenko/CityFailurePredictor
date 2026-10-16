import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it } from "vitest";
import { getErrorMessage } from "@/utils/errors";

function axiosErrorWith(data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError("Request failed", "ERR_BAD_REQUEST", config, null, {
    data,
    status: 400,
    statusText: "Bad Request",
    headers: {},
    config,
  });
}

describe("getErrorMessage", () => {
  it("returns the backend message from an API error", () => {
    const error = axiosErrorWith({ code: "VALIDATION", message: "Title is required" });

    expect(getErrorMessage(error, "fallback")).toBe("Title is required");
  });

  it("falls back when the API error has no message", () => {
    expect(getErrorMessage(axiosErrorWith({}), "fallback")).toBe("fallback");
  });

  it("falls back for non-API errors", () => {
    expect(getErrorMessage(new Error("boom"), "fallback")).toBe("fallback");
    expect(getErrorMessage("boom", "fallback")).toBe("fallback");
  });
});
