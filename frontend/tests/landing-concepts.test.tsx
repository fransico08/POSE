import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import HomePage from "@/app/page";
import { renderToStaticMarkup } from "react-dom/server";
import { HeroSection } from "@/components/landing/HeroSection";
import { IndustryMarquee } from "@/components/landing/IndustryMarquee";
import { HeaderScrollState } from "@/components/landing/HeaderScrollState";
import { industries, landingNavigation } from "@/content/landing";

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
  it("does not let React automatically preload the first eager illustration", () => {
    const html = renderToStaticMarkup(<HeroSection />);
    const parsed = new DOMParser().parseFromString(html, "text/html");
    expect(parsed.querySelector('link[rel="preload"][as="image"]')).toBeNull();
    const image = parsed.querySelector(".hero-coverflow img")!;
    expect(image.getAttribute("loading")).toBe("eager");
    expect(image.getAttribute("fetchpriority")).toBe("low");
    expect(parsed.querySelectorAll(".hero-coverflow img")).toHaveLength(1);
  });
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

  it("rotates every five seconds and resumes after hover and focus leave", () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    const { container } = render(<HeroSection />); intersect();
    const hero = container.querySelector(".landing-hero")!, flow = container.querySelector(".hero-coverflow")!;
    act(() => vi.advanceTimersByTime(4999)); expect(flow).toHaveAttribute("data-selected", "0");
    act(() => vi.advanceTimersByTime(1)); expect(flow).toHaveAttribute("data-selected", "1");
    fireEvent.mouseEnter(hero); act(() => vi.advanceTimersByTime(10000)); expect(flow).toHaveAttribute("data-selected", "1");
    fireEvent.mouseLeave(hero); act(() => vi.advanceTimersByTime(5000)); expect(flow).toHaveAttribute("data-selected", "2");
    const login = screen.getByRole("link", { name: "Đăng nhập" });
    fireEvent.focus(login); act(() => vi.advanceTimersByTime(10000)); expect(flow).toHaveAttribute("data-selected", "2");
    const anchor = screen.getByRole("link", { name: "Xem cách hoạt động" });
    fireEvent.blur(login, { relatedTarget: anchor }); fireEvent.focus(anchor);
    act(() => vi.advanceTimersByTime(5000)); expect(flow).toHaveAttribute("data-selected", "2");
    fireEvent.blur(anchor, { relatedTarget: null }); act(() => vi.advanceTimersByTime(5000)); expect(flow).toHaveAttribute("data-selected", "3");
    intersect(false); act(() => vi.advanceTimersByTime(10000)); expect(flow).toHaveAttribute("data-selected", "3");
  });


  it("stops on a hidden page and uses a static education frame without a timer in reduced motion", () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    const { container } = render(<HeroSection />); intersect(); const flow = container.querySelector(".hero-coverflow")!;
    expect(flow).toHaveAttribute("data-running");
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    fireEvent(document, new Event("visibilitychange")); expect(flow).not.toHaveAttribute("data-running");
    visibility.mockReturnValue("visible"); fireEvent(document, new Event("visibilitychange"));
    act(() => vi.advanceTimersByTime(5000)); expect(flow).toHaveAttribute("data-selected", "1");
    act(() => { reduced = true; mediaListeners.forEach(listener => listener()); });
    expect(flow).not.toHaveAttribute("data-running"); expect(flow).toHaveAttribute("data-selected", "0");
    expect(flow).toHaveAttribute("data-reduced"); expect(vi.getTimerCount()).toBe(0);
    expect(container.querySelector(".rotating-phrase")).toBeNull();
    act(() => vi.advanceTimersByTime(30000)); expect(flow).toHaveAttribute("data-selected", "0");
  });


  it("keeps illustration unfocusable and exposes six equivalent industry descriptions", () => {
    const { container } = render(<HeroSection />);
    const flow = container.querySelector(".hero-coverflow")!;
    expect(flow).toHaveAttribute("aria-hidden", "true");
    expect(flow.querySelector("a,button,input,select,textarea,[tabindex]")).toBeNull();
    const summary = screen.getByRole("list", { name: "Luồng CRM minh họa theo sáu ngành" });
    expect(summary).toHaveClass("sr-only"); expect(within(summary).getAllByRole("listitem")).toHaveLength(6);
    for (const industry of industries) expect(summary).toHaveTextContent(industry.label);
    expect(container.querySelector(".hero-demo-picker")).toBeNull();
  });


  it("pauses the marquee on hover and focus without adding controls", () => {
    const { container } = render(<IndustryMarquee title="Một nền tảng, nhiều ngành" items={industries.map(item => ({ label: item.label, icon: <span /> }))} />); intersect();
    const strip = container.querySelector(".industry-strip")!;
    expect(screen.getAllByRole("listitem")).toHaveLength(6); expect(container.querySelector(".marquee-clone")).toHaveAttribute("aria-hidden", "true");
    expect(strip).toHaveAttribute("data-running"); fireEvent.mouseEnter(strip); expect(strip).not.toHaveAttribute("data-running");
    fireEvent.mouseLeave(strip); expect(strip).toHaveAttribute("data-running");
    fireEvent.focus(strip); expect(strip).not.toHaveAttribute("data-running");
    fireEvent.blur(strip, { relatedTarget: null }); expect(strip).toHaveAttribute("data-running");
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("contains no pause or resume button anywhere in the landing", () => {
    render(<HomePage />);
    expect(screen.queryByRole("button", { name: /Tạm dừng|Tiếp tục/ })).toBeNull();
  });

  it("loops through all six coverflow frames", () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    const { container } = render(<HeroSection />); intersect();
    act(() => vi.advanceTimersByTime(25000)); expect(container.querySelector(".hero-coverflow")).toHaveAttribute("data-selected", "5");
    act(() => vi.advanceTimersByTime(5000)); expect(container.querySelector(".hero-coverflow")).toHaveAttribute("data-selected", "0");
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

  it("updates the header when an anchor skips the thin sentinel", () => {
    const { container } = render(<><HeaderScrollState><span>POSE CRM</span></HeaderScrollState><section><h1 id="hero-title">Hero</h1></section><span id="hero-end" /></>);
    const hero = container.querySelector("section")!;
    const notifyHero = (isIntersecting: boolean, top: number) => {
      const rect = hero.getBoundingClientRect();
      act(() => notifyIntersection([{ target: hero, isIntersecting, boundingClientRect: { ...rect, top }, intersectionRatio: isIntersecting ? 1 : 0, intersectionRect: rect, rootBounds: null, time: 0 }], {} as IntersectionObserver));
    };
    intersect(false, 1000);
    notifyHero(true, 0);
    expect(container.querySelector("header")).not.toHaveAttribute("data-scrolled");
    notifyHero(false, -1200);
    expect(container.querySelector("header")).toHaveAttribute("data-scrolled");
    notifyHero(true, 0);
    expect(container.querySelector("header")).not.toHaveAttribute("data-scrolled");
  });
});
