import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import HeroA, { metadata as metadataA } from "@/app/thu-nghiem/hero-a/page";
import HeroB, { metadata as metadataB } from "@/app/thu-nghiem/hero-b/page";
import { HeroMotion } from "@/components/landing/concepts/HeroMotion";
import { IndustryMarquee } from "@/components/landing/concepts/IndustryMarquee";
import { ConceptHeader } from "@/components/landing/concepts/ConceptHeader";

vi.mock("next/font/google", () => ({ Plus_Jakarta_Sans: () => ({ variable: "display-a" }), Fraunces: () => ({ variable: "display-b" }) }));
vi.mock("next/navigation", () => ({ notFound: () => { throw new Error("NOT_FOUND"); } }));
let reduced = false;
const mediaListeners = new Set<() => void>();
const observed = new Set<Element>();
let notifyIntersection: IntersectionObserverCallback;

function intersect(isIntersecting = true, top = 100) {
  act(() => notifyIntersection([...observed].map(target => ({ target, isIntersecting, boundingClientRect: { top } }) as IntersectionObserverEntry), {} as IntersectionObserver));
}

beforeEach(() => {
  reduced = false;
  vi.stubEnv("NODE_ENV", "development");
  vi.stubGlobal("matchMedia", () => ({ get matches() { return reduced; }, addEventListener: (_: string, listener: () => void) => mediaListeners.add(listener), removeEventListener: (_: string, listener: () => void) => mediaListeners.delete(listener) }));
  vi.stubGlobal("IntersectionObserver", class {
    constructor(callback: IntersectionObserverCallback) { notifyIntersection = callback; }
    observe(node: Element) { observed.add(node); }
    unobserve(node: Element) { observed.delete(node); }
    disconnect() { observed.clear(); }
  });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllEnvs(); vi.restoreAllMocks(); });

describe("development hero concepts", () => {
  it("uses identical static copy, one H1/header and valid section headings in both concepts", () => {
    const a = render(<HeroA />);
    const title = screen.getByRole("heading", { level: 1 }).textContent;
    expect(a.container.querySelectorAll("header")).toHaveLength(1);
    for (const section of a.container.querySelectorAll("section")) expect(section.querySelector(`#${section.getAttribute("aria-labelledby")}`)?.tagName).toMatch(/^H[12]$/);
    const copy = a.container.textContent;
    expect(copy).not.toMatch(/đồ án|tiểu luận|TLCN|phạm vi thiết kế|giai đoạn sau|nhóm POSE|SALES_ONLY|CARE_VISIBLE|[\w.+-]+@[\w.-]+\.[a-z]{2,}/i);
    expect(a.container.querySelector("figure [aria-hidden=true]")).not.toBeNull();
    a.unmount();
    const b = render(<HeroB />);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title);
    expect(b.container.textContent).toBe(copy);
  });

  it("keeps both routes noindex and rejects them outside development", () => {
    expect(metadataA.robots).toEqual({ index: false, follow: false });
    expect(metadataB.robots).toEqual(metadataA.robots);
    vi.stubEnv("NODE_ENV", "production");
    expect(() => HeroA()).toThrow("NOT_FOUND");
    expect(() => HeroB()).toThrow("NOT_FOUND");
  });

  it("rotates only in view, pauses on focus and requires an explicit restart", () => {
    vi.useFakeTimers();
    const { container } = render(<HeroMotion><span>Mock</span></HeroMotion>);
    intersect();
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector(".concept-audience")).toHaveTextContent("bộ phận chăm sóc");
    fireEvent.focus(screen.getByRole("link", { name: "Đăng nhập" }));
    act(() => vi.advanceTimersByTime(8000));
    expect(container.querySelector(".concept-audience")).toHaveTextContent("bộ phận chăm sóc");
    expect(container.querySelector("section")).not.toHaveAttribute("data-running");
    fireEvent.click(screen.getByRole("button", { name: "Tiếp tục hiệu ứng" }));
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector(".concept-audience")).toHaveTextContent("người quản lý");
    intersect(false);
    act(() => vi.advanceTimersByTime(8000));
    expect(container.querySelector(".concept-audience")).toHaveTextContent("người quản lý");
  });

  it("stops on a hidden page and removes running states for reduced motion", () => {
    const { container } = render(<HeroMotion><span>Mock</span></HeroMotion>);
    intersect();
    expect(container.querySelector("section")).toHaveAttribute("data-running");
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    fireEvent(document, new Event("visibilitychange"));
    expect(container.querySelector("section")).not.toHaveAttribute("data-running");
    visibility.mockReturnValue("visible"); fireEvent(document, new Event("visibilitychange"));
    act(() => { reduced = true; mediaListeners.forEach(listener => listener()); });
    expect(container.querySelector("section")).not.toHaveAttribute("data-running");
    expect(container.querySelector("section")).toHaveAttribute("data-won");
    expect(container.querySelector(".concept-phrase")).toBeNull();
    expect(screen.getByRole("button", { name: "Chuyển động đã tắt" })).toBeDisabled();
  });

  it("changes mock emphasis with accessible buttons", async () => {
    const user = userEvent.setup();
    const { container } = render(<HeroMotion><span>Mock</span></HeroMotion>);
    await user.click(screen.getByRole("button", { name: "Hồ sơ" }));
    expect(screen.getByRole("button", { name: "Hồ sơ" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Cơ hội" })).toHaveAttribute("aria-pressed", "false");
    expect(container.querySelector("section")).toHaveAttribute("data-demo", "customer");
  });

  it("provides pause/resume and hides the duplicate marquee from accessibility", async () => {
    const user = userEvent.setup();
    const { container } = render(<IndustryMarquee />);
    intersect();
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
    expect(container.querySelector(".concept-marquee-clone")).toHaveAttribute("aria-hidden", "true");
    await user.click(screen.getByRole("button", { name: "Tạm dừng" }));
    expect(container.querySelector("section")).not.toHaveAttribute("data-running");
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(container.querySelector("section")).toHaveAttribute("data-running");
  });

  it("opens the nonmodal disclosure by keyboard and restores focus after Escape", async () => {
    const user = userEvent.setup();
    render(<ConceptHeader />);
    const trigger = screen.getByRole("button", { name: "Mở điều hướng" });
    trigger.focus(); await user.keyboard("{Enter}");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const panel = document.getElementById("concept-mobile-nav")!;
    expect(panel).not.toHaveAttribute("hidden");
    within(panel).getByRole("link", { name: "Tính năng" }).focus();
    await user.keyboard("{Escape}");
    expect(trigger).toHaveFocus(); expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("switches header appearance only after the hero sentinel passes above the viewport", () => {
    const { container } = render(<><ConceptHeader /><span id="concept-hero-end" /></>);
    intersect(false, 1000); expect(container.querySelector("header")).not.toHaveAttribute("data-past-hero");
    intersect(false, -100); expect(container.querySelector("header")).toHaveAttribute("data-past-hero");
    intersect(true, 100); expect(container.querySelector("header")).not.toHaveAttribute("data-past-hero");
  });
});
