import { act, fireEvent, render, screen } from "@testing-library/react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { TimeGreeting } from "@/components/overview/TimeGreeting";
import { LoginIllustration } from "@/components/auth/LoginIllustration";
import { Customer360Card } from "@/components/landing/mock/Customer360Card";
import { Skeleton } from "@/components/ui/Skeleton";
import { HeroMotion } from "@/components/landing/HeroMotion";
import { HeroMotionScope } from "@/components/landing/HeroMotionScope";
import { heroFrames } from "@/content/landing";
import { installMotionFixture } from "./landing-motion-fixture";
const motion = installMotionFixture();
const fakeClock = () => vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date", "performance"] });
describe("app micro interactions", () => {
  it("keeps the decorative coverflow stopped while its page is hidden", () => {
    fakeClock();
    const images = heroFrames.map(frame => <span key={frame.id} />);
    const { container } = render(<HeroMotionScope><HeroMotion frames={heroFrames} images={images} backdrops={images} /></HeroMotionScope>);
    motion.makeVisible(); const flow = container.querySelector(".hero-coverflow")!;
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    fireEvent(document, new Event("visibilitychange"));
    act(() => vi.advanceTimersByTime(15000)); expect(flow).toHaveAttribute("data-selected", "0");
    visibility.mockReturnValue("visible"); fireEvent(document, new Event("visibilitychange"));
    act(() => vi.advanceTimersByTime(5000)); expect(flow).toHaveAttribute("data-selected", "1");
    expect(flow.querySelector("button, a, [tabindex]")).toBeNull();
  });
  it.each([[8, "Chào buổi sáng"], [14, "Chào buổi chiều"], [21, "Chào buổi tối"]])("uses the browser hour %s", (hour, greeting) => {
    fakeClock(); vi.setSystemTime(new Date(2026, 9, 14, Number(hour)));
    render(<TimeGreeting />); expect(screen.getByText(greeting)).toBeVisible();
    vi.setSystemTime(new Date(2026, 9, 14, 18)); act(() => vi.advanceTimersByTime(60000));
    expect(screen.getByText("Chào buổi tối")).toBeVisible();
  });
  it("uses a stable server greeting before browser hydration", () => {
    expect(renderToStaticMarkup(<TimeGreeting />)).toBe("Chào");
  });
  it("pauses the finite login animation on hidden tabs and never adds controls", () => {
    fakeClock(); const { container } = render(<LoginIllustration><Customer360Card /></LoginIllustration>);
    motion.makeVisible(); const scene = container.firstElementChild!;
    expect(scene).toHaveAttribute("data-running");
    expect(container.querySelector("button, a, input, [tabindex]")).toBeNull();
    act(() => vi.advanceTimersByTime(2400));
    const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    fireEvent(document, new Event("visibilitychange"));
    act(() => vi.advanceTimersByTime(10000));
    expect(scene).not.toHaveAttribute("data-running"); expect(scene).not.toHaveAttribute("data-complete");
    visibility.mockReturnValue("visible"); fireEvent(document, new Event("visibilitychange"));
    act(() => vi.advanceTimersByTime(2400));
    expect(scene).toHaveAttribute("data-complete"); expect(scene).not.toHaveAttribute("data-running");
  });
  it("removes login animation activation for reduced motion", () => {
    const { container } = render(<LoginIllustration><Customer360Card /></LoginIllustration>); motion.makeVisible();
    motion.setReduced(true);
    expect(container.firstElementChild).not.toHaveAttribute("data-running");
    expect(container.firstElementChild).not.toHaveAttribute("data-motion-ready");
    expect(container).toHaveTextContent("Đã tắt chuyển động");
  });
  it("finishes shimmer while keeping the checking status available", () => {
    fakeClock(); render(<Skeleton />); motion.makeVisible();
    const main = screen.getByRole("main"); expect(main).toHaveAttribute("aria-busy", "true");
    act(() => vi.advanceTimersByTime(4800));
    expect(main).toHaveAttribute("data-complete"); expect(main).not.toHaveAttribute("data-running");
    expect(screen.getByRole("status")).toHaveTextContent("Đang kiểm tra phiên đăng nhập");
  });
});
