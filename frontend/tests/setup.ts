import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

afterEach(cleanup);

HTMLDialogElement.prototype.showModal = function () { this.open = true; };
HTMLDialogElement.prototype.close = function () { this.open = false; };
vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
// Component tests run after an already-painted frame; real frame scheduling is checked in Chrome.
vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { callback(0); return 1; });
vi.stubGlobal("requestIdleCallback", (callback: IdleRequestCallback) => { callback({ didTimeout: false, timeRemaining: () => 50 }); return 1; });
vi.stubGlobal("cancelIdleCallback", () => {});
