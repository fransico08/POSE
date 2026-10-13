import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HomePage, { metadata } from "@/app/page";
import { MobileNavDisclosure } from "@/components/landing/MobileNavDisclosure";
import { contentUpdatedAt, faqEntries, landingMeta, productCaption, scopeNote } from "@/content/landing";
import { landingStructuredData, serializeJsonLd } from "@/lib/landing-seo";
import { getSiteUrl } from "@/lib/site";
import robots from "@/app/robots";
import sitemap from "@/app/sitemap";

vi.mock("next/image", () => ({ default: ({ alt }: { alt: string }) => <span role="img" aria-label={alt} /> }));
afterEach(() => vi.unstubAllEnvs());

describe("public landing document", () => {
  it("has one H1 and does not skip heading levels", () => {
    const { container } = render(<HomePage />);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    const levels = [...container.querySelectorAll("h1,h2,h3,h4,h5,h6")].map(node => Number(node.tagName[1]));
    expect(levels[0]).toBe(1);
    levels.slice(1).forEach((level, index) => expect(level).toBeLessThanOrEqual(levels[index] + 1));
  });

  it("labels every section with a unique heading inside it", () => {
    const { container } = render(<HomePage />);
    const sections = [...container.querySelectorAll("section")];
    expect(sections).toHaveLength(9);
    sections.forEach(section => {
      const id = section.getAttribute("aria-labelledby");
      expect(id).toBeTruthy();
      const target = document.getElementById(id!);
      expect(target?.tagName).toMatch(/^H[12]$/);
      expect(section.contains(target)).toBe(true);
      expect(container.querySelectorAll(`[id="${id}"]`)).toHaveLength(1);
    });
  });

  it("resolves every internal navigation anchor, including the skip link", () => {
    const { container } = render(<HomePage />);
    const anchors = [...container.querySelectorAll<HTMLAnchorElement>('a[href^="#"]')];
    expect(anchors.length).toBeGreaterThan(5);
    anchors.forEach(anchor => expect(document.getElementById(anchor.hash.slice(1))).not.toBeNull());
    expect(document.getElementById("noi-dung")).toHaveAttribute("tabindex", "-1");
  });

  it("renders exactly the visible questions and answers in FAQPage JSON-LD", () => {
    const { container } = render(<HomePage />);
    const graph = JSON.parse(container.querySelector('script[type="application/ld+json"]')!.textContent!)["@graph"];
    const faq = graph.find((item: { "@type": string }) => item["@type"] === "FAQPage");
    const details = [...container.querySelectorAll("details")];
    expect(details).toHaveLength(8);
    expect(faq.mainEntity).toHaveLength(details.length);
    details.forEach((detail, index) => {
      expect(detail.querySelector("summary")!.textContent).toBe(faq.mainEntity[index].name);
      expect(detail.querySelector("p")!.textContent).toBe(faq.mainEntity[index].acceptedAnswer.text);
    });
    expect(details[5].textContent).toContain(faqEntries[5].answer);
  });

  it("keeps implementation status visible and excludes credentials and technical labels", () => {
    const { container } = render(<HomePage />);
    const copy = container.textContent!;
    expect(copy).toContain(scopeNote);
    expect(copy).toContain(productCaption);
    expect(copy).not.toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(copy).not.toMatch(/SALES_ONLY|CARE_VISIBLE|\bpermission\b|POSE_DEMO_PASSWORD/i);
    const demoPassword = process.env.POSE_DEMO_PASSWORD;
    // Boolean assertion keeps the process-only value out of failure output.
    if (demoPassword) expect(copy.includes(demoPassword)).toBe(false);
    expect(container.querySelector("form")).toBeNull();
    expect(container.querySelector("time")).toHaveAttribute("datetime", contentUpdatedAt);
  });
});

describe("landing structured data and crawl metadata", () => {
  it("escapes script-closing text without altering the parsed content", () => {
    const data = { text: "</script><script>unexpected</script>" };
    const serialized = serializeJsonLd(data);
    expect(serialized).not.toContain("</script");
    expect(serialized).toContain("\\u003c");
    expect(JSON.parse(serialized)).toEqual(data);
  });

  it("describes only the website, application and visible FAQ without invented offers or ratings", () => {
    const data = landingStructuredData(new URL("https://pose.example/"));
    expect(data["@graph"].map(item => item["@type"])).toEqual(["WebSite", "SoftwareApplication", "FAQPage"]);
    expect(data["@graph"][1]).toMatchObject({ applicationCategory: "BusinessApplication", operatingSystem: "Web", inLanguage: "vi", description: landingMeta.description });
    expect(serializeJsonLd(data)).not.toMatch(/"offers"|"aggregateRating"|"review"/);
  });

  it("uses an absolute title, a single canonical and indexable public metadata", () => {
    expect(metadata.title).toEqual({ absolute: landingMeta.title });
    expect(landingMeta.title.length).toBeLessThan(60);
    expect(landingMeta.description.length).toBeGreaterThanOrEqual(120);
    expect(landingMeta.description.length).toBeLessThanOrEqual(160);
    expect(metadata.alternates).toEqual({ canonical: "/" });
    expect(metadata.robots).toEqual({ index: true, follow: true });
  });

  it("uses the configured public origin for crawl routes and a fixed content date", () => {
    vi.stubEnv("SITE_URL", "https://pose.example/");
    expect(getSiteUrl().href).toBe("https://pose.example/");
    expect(robots()).toEqual({ rules: { userAgent: "*", allow: "/", disallow: ["/app", "/api", "/dang-nhap"] }, sitemap: "https://pose.example/sitemap.xml" });
    expect(sitemap()).toEqual([{ url: "https://pose.example/", lastModified: contentUpdatedAt }]);
  });
});

describe("mobile navigation disclosure", () => {
  it("opens by keyboard, remains nonmodal, closes with Escape and returns focus", async () => {
    const user = userEvent.setup();
    render(<MobileNavDisclosure />);
    const trigger = screen.getByRole("button", { name: "Mở điều hướng" });
    const panel = document.getElementById(trigger.getAttribute("aria-controls")!)!;
    expect(panel).toHaveAttribute("hidden");
    trigger.focus(); await user.keyboard("{Enter}");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(panel).not.toHaveAttribute("hidden");
    expect(screen.queryByRole("dialog")).toBeNull();
    await user.tab();
    expect(within(panel).getByRole("link", { name: "Năng lực" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).toHaveFocus();
  });

  it("toggles with Space and closes after selecting a page anchor", async () => {
    const user = userEvent.setup();
    render(<MobileNavDisclosure />);
    const trigger = screen.getByRole("button", { name: "Mở điều hướng" });
    trigger.focus(); await user.keyboard(" ");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    await user.click(screen.getByRole("link", { name: "Quy trình" }));
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("does not intercept Escape or move focus outside the disclosure", async () => {
    const user = userEvent.setup();
    render(<><MobileNavDisclosure /><button type="button">Outside</button></>);
    const trigger = screen.getByRole("button", { name: "Mở điều hướng" });
    await user.click(trigger);
    const outside = screen.getByRole("button", { name: "Outside" });
    outside.focus(); await user.keyboard("{Escape}");
    expect(outside).toHaveFocus();
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });
});
