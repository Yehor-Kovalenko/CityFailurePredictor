import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { publish, useSubscribe } from "./eventBroker";

describe("eventBroker", () => {
  it("delivers published data to subscribers of the event", () => {
    const callback = vi.fn();
    renderHook(() => useSubscribe("incident:created", callback));

    publish("incident:created", { id: "1" });

    expect(callback).toHaveBeenCalledOnce();
    expect(callback).toHaveBeenCalledWith({ id: "1" });
  });

  it("publishes to every event in a list", () => {
    const first = vi.fn();
    const second = vi.fn();
    renderHook(() => useSubscribe("first", first));
    renderHook(() => useSubscribe("second", second));

    publish(["first", "second"], "payload");

    expect(first).toHaveBeenCalledWith("payload");
    expect(second).toHaveBeenCalledWith("payload");
  });

  it("does not notify subscribers of other events", () => {
    const callback = vi.fn();
    renderHook(() => useSubscribe("subscribed", callback));

    publish("other", "payload");

    expect(callback).not.toHaveBeenCalled();
  });

  it("unsubscribes when the component unmounts", () => {
    const callback = vi.fn();
    const { unmount } = renderHook(() => useSubscribe("unmount-test", callback));

    unmount();
    publish("unmount-test", "payload");

    expect(callback).not.toHaveBeenCalled();
  });

  it("calls the latest callback after a re-render without re-subscribing", () => {
    const initial = vi.fn();
    const latest = vi.fn();
    const { rerender } = renderHook(({ cb }) => useSubscribe("rerender-test", cb), {
      initialProps: { cb: initial },
    });

    rerender({ cb: latest });
    publish("rerender-test", "payload");

    expect(initial).not.toHaveBeenCalled();
    expect(latest).toHaveBeenCalledOnce();
  });
});
