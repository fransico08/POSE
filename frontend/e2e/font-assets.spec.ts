import { expect, test } from "@playwright/test";

test("loads Be Vietnam Pro from local assets without requesting Google Fonts", async ({ page }) => {
  const googleFontRequests: string[] = [];
  const localFontResponses: Array<{ url: string; status: number }> = [];
  page.on("request", request => {
    if (/fonts\.(?:googleapis|gstatic)\.com/i.test(request.url())) googleFontRequests.push(request.url());
  });
  page.on("response", response => {
    if (/\/_next\/static\/media\/.*\.ttf(?:\?|$)/i.test(response.url())) {
      localFontResponses.push({ url: response.url(), status: response.status() });
    }
  });

  await page.goto("/dang-nhap");
  await expect(page.getByText("Kết nối để chăm sóc tốt hơn")).toBeVisible();
  await page.evaluate(async () => { await document.fonts.ready; });
  const font = await page.evaluate(() => {
    const family = getComputedStyle(document.body).fontFamily.split(",")[0].trim().replace(/^["']|["']$/g, "");
    return { family, loaded: document.fonts.check(`16px "${family}"`, "Tiếng Việt") };
  });

  expect(googleFontRequests).toEqual([]);
  expect(localFontResponses.length).toBeGreaterThan(0);
  expect(localFontResponses.every(response => response.status === 200)).toBe(true);
  expect(font).toEqual({ family: "vietnam", loaded: true });
});
