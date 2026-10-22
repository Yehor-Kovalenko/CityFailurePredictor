import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useGeolocation } from "@/hooks/useGeolocation";

const CONSENT_KEY = "city-failure-predictor.geo-consent";

let onSuccess: PositionCallback | undefined;
let onError: PositionErrorCallback | undefined;
const watchPosition = vi.fn((ok: PositionCallback, fail: PositionErrorCallback) => {
  onSuccess = ok;
  onError = fail;
  return 7;
});
const clearWatch = vi.fn();

describe("useGeolocation", () => {
  beforeEach(() => {
    vi.stubGlobal("navigator", { geolocation: { watchPosition, clearWatch } });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    onSuccess = undefined;
    onError = undefined;
  });

  it("does not track the user before consent", () => {
    const { result } = renderHook(() => useGeolocation());

    expect(result.current.consent).toBe("unknown");
    expect(watchPosition).not.toHaveBeenCalled();
  });

  it("tracks the position after consent without persisting it", () => {
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.grant());
    act(() => onSuccess?.({ coords: { latitude: 51.7, longitude: 19.4 } } as GeolocationPosition));

    expect(result.current.position).toEqual({ lat: 51.7, lng: 19.4 });
    expect(localStorage.getItem(CONSENT_KEY)).toBe("granted");
    expect(JSON.stringify({ ...localStorage })).not.toContain("51.7");
  });

  it("stops tracking and forgets the position when consent is withdrawn", () => {
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.grant());
    act(() => onSuccess?.({ coords: { latitude: 51.7, longitude: 19.4 } } as GeolocationPosition));
    act(() => result.current.deny());

    expect(result.current.position).toBeNull();
    expect(clearWatch).toHaveBeenCalledWith(7);
    expect(localStorage.getItem(CONSENT_KEY)).toBe("denied");
  });

  it("falls back to denied when the browser blocks location access", () => {
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.grant());
    act(() => onError?.({ code: 1 } as GeolocationPositionError));

    expect(result.current.consent).toBe("denied");
    expect(result.current.error).toBe("Location access was denied");
  });

  it("restores a previous decision", () => {
    localStorage.setItem(CONSENT_KEY, "granted");

    renderHook(() => useGeolocation());

    expect(watchPosition).toHaveBeenCalledOnce();
  });
});
