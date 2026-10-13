import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import HomePage from "@/app/page";
import { HeroSection } from "@/components/landing/HeroSection";
import { IndustryMarquee } from "@/components/landing/IndustryMarquee";
import { HeaderScrollState } from "@/components/landing/HeaderScrollState";
import { industries, landingNavigation } from "@/content/landing";

vi.mock("next/font/google", () => ({ Fraunces: () => ({ variable: "display-b" }) }));
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

describe("selected hero and motion", () => {
  it("keeps one static H1/header and valid section headings without credentials", () => {
    const a = render(<HomePage />);
    const title = screen.getByRole("heading", { level: 1 }).textContent;
    expect(a.container.querySelectorAll("header")).toHaveLength(1);
    for (const section of a.container.querySelectorAll("section")) expect(section.querySelector(`#${section.getAttribute("aria-labelledby")}`)?.tagName).toMatch(/^H[12]$/);
    const copy = a.container.textContent;
    expect(copy).not.toMatch(/đồ án|tiểu luận|TLCN|phạm vi thiết kế|giai đoạn sau|nhóm POSE|SALES_ONLY|CARE_VISIBLE|[\w.+-]+@[\w.-]+\.[a-z]{2,}/i);
    expect(a.container.querySelector("figure [aria-hidden=true]")).not.toBeNull();
    expect(title).toBe("Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng.");
    expect(screen.getByRole("heading", { level: 1 })).not.toHaveClass("hero-enter");
    expect(a.container.querySelector(".hero-copy")).not.toHaveAttribute("data-motion-ready");
  });

  it("links to implemented sections and exposes no concept route", () => {
    expect(landingNavigation.some(item => item.href === "#cach-hoat-dong")).toBe(true);
    const { container } = render(<HomePage />);
    expect(container.querySelector('a[href*="thu-nghiem"]')).toBeNull();
  });

  it("rotates only in view, pauses on focus and requires an explicit restart", () => {
    vi.useFakeTimers();
    const { container } = render(<HeroSection />);
    intersect();
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector(".hero-audience")).toHaveTextContent("bộ phận chăm sóc");
    fireEvent.focus(screen.getByRole("link", { name: "Đăng nhập" }));
    act(() => vi.advanceTimersByTime(8000));
    expect(container.querySelector(".hero-audience")).toHaveTextContent("bộ phận chăm sóc");
    expect(container.querySelector(".hero-figure")).not.toHaveAttribute("data-running");
    fireEvent.click(screen.getByRole("button", { name: "Tiếp tục hiệu ứng" }));
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector(".hero-audience")).toHaveTextContent("người quản lý");
    intersect(false);
    act(() => vi.advanceTimersByTime(8000));
    expect(container.querySelector(".hero-audience")).toHaveTextContent("người quản lý");
  });

  it("stops on a hidden page and removes running states for reduced motion", () => {
    const { container } = render(<HeroSection />);
    intersect();
    expect(container.querySelector(".hero-figure")).toHaveAttribute("data-running");
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    fireEvent(document, new Event("visibilitychange"));
    expect(container.querySelector(".hero-figure")).not.toHaveAttribute("data-running");
    visibility.mockReturnValue("visible"); fireEvent(document, new Event("visibilitychange"));
    act(() => { reduced = true; mediaListeners.forEach(listener => listener()); });
    expect(container.querySelector(".hero-figure")).not.toHaveAttribute("data-running");
    expect(container.querySelector(".hero-figure")).toHaveAttribute("data-phase", "2");
    expect(container.querySelector(".rotating-phrase")).toBeNull();
    expect(screen.getByRole("button", { name: "Chuyển động đã tắt" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Chuyển động đã tắt" })).toHaveTextContent("Đã tắt chuyển động");
    expect(screen.getByRole("button", { name: "Chuyển động đã tắt" }).querySelector("svg")).toBeNull();
  });

  it("changes mock emphasis with accessible buttons", async () => {
    const user = userEvent.setup();
    const { container } = render(<HeroSection />);
    await user.click(screen.getByRole("button", { name: "Hồ sơ" }));
    expect(screen.getByRole("button", { name: "Hồ sơ" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Cơ hội" })).toHaveAttribute("aria-pressed", "false");
    expect(container.querySelector(".hero-figure")).toHaveAttribute("data-demo", "customer");
  });

  it("provides pause/resume and hides the duplicate marquee from accessibility", async () => {
    const user = userEvent.setup();
    const { container } = render(<IndustryMarquee title="Một nền tảng, nhiều ngành" items={industries.map(item => ({ label: item.label, icon: <span /> }))} />);
    intersect();
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
    expect(container.querySelector(".marquee-clone")).toHaveAttribute("aria-hidden", "true");
    await user.click(screen.getByRole("button", { name: "Tạm dừng dải ngành" }));
    expect(container.querySelector(".industry-strip")).not.toHaveAttribute("data-running");
    await user.click(screen.getByRole("button", { name: "Tiếp tục dải ngành" }));
    expect(container.querySelector(".industry-strip")).toHaveAttribute("data-running");
  });

  it("opens the nonmodal disclosure by keyboard and restores focus after Escape", async () => {
    const user = userEvent.setup();
    render(<HeaderScrollState><span>POSE CRM</span></HeaderScrollState>);
    const trigger = screen.getByRole("button", { name: "Mở điều hướng" });
    trigger.focus(); await user.keyboard("{Enter}");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const panel = document.getElementById("landing-mobile-navigation")!;
    expect(panel).not.toHaveAttribute("hidden");
    within(panel).getByRole("link", { name: "Tính năng" }).focus();
    await user.keyboard("{Escape}");
    expect(trigger).toHaveFocus(); expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("switches header appearance only after the hero sentinel passes above the viewport", () => {
    const { container } = render(<><HeaderScrollState><span>POSE CRM</span></HeaderScrollState><span id="hero-end" /></>);
    intersect(false, 1000); expect(container.querySelector("header")).not.toHaveAttribute("data-scrolled");
    intersect(false, -100); expect(container.querySelector("header")).toHaveAttribute("data-scrolled");
    intersect(true, 100); expect(container.querySelector("header")).not.toHaveAttribute("data-scrolled");
  });
});
