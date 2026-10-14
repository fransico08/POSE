import { afterEach, describe, expect, it, vi } from "vitest";
import { scheduleWhenIdle } from "@/lib/motion/idle";
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });
describe("idle motion scheduling", () => {
  it("waits for the idle callback and supports cancellation", () => {
    let scheduled: (() => void) | undefined;
    const request = vi.spyOn(window, "requestIdleCallback").mockImplementation(callback => { scheduled = () => callback({ didTimeout: false, timeRemaining: () => 50 }); return 7; });
    const cancel = vi.spyOn(window, "cancelIdleCallback"), callback = vi.fn();
    const stop = scheduleWhenIdle(callback);
    expect(request).toHaveBeenCalledWith(expect.any(Function), { timeout: 1200 });
    expect(callback).not.toHaveBeenCalled(); scheduled?.(); expect(callback).toHaveBeenCalledOnce();
    stop(); expect(cancel).toHaveBeenCalledWith(7);
  });
  it("uses a cancellable timeout when idle callbacks are unavailable", () => {
    vi.useFakeTimers(); const original = window.requestIdleCallback; vi.stubGlobal("requestIdleCallback", undefined);
    const callback = vi.fn(), stop = scheduleWhenIdle(callback);
    vi.advanceTimersByTime(249); expect(callback).not.toHaveBeenCalled();
    stop(); vi.advanceTimersByTime(1); expect(callback).not.toHaveBeenCalled();
    scheduleWhenIdle(callback); vi.advanceTimersByTime(250); expect(callback).toHaveBeenCalledOnce();
    vi.stubGlobal("requestIdleCallback", original);
  });
});
