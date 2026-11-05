import { expect, test } from "@playwright/test";

test.use({ trace: "off", screenshot: "off", video: "off" });

test("login rejects invalid credentials, accepts lowercase organization and clears state on logout", async ({ page }) => {
  const password = process.env.POSE_E2E_PASSWORD;
  if (!password) throw new Error("Missing isolated E2E password.");
  await page.goto("/app/khach-hang-360");
  await expect(page).toHaveURL(/\/dang-nhap/);
  await page.getByLabel("Mã tổ chức").fill("edu");
  await page.getByLabel("Email", { exact: true }).fill("edu.s01@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("invalid-password");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page.getByText("Mã tổ chức, email hoặc mật khẩu không đúng.")).toBeVisible();
  await page.getByLabel("Mã tổ chức").fill("e2e-unknown");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page.getByText("Mã tổ chức, email hoặc mật khẩu không đúng.")).toBeVisible();
  await page.getByLabel("Mã tổ chức").fill("edu");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(/\/app/);
  await expect(page.getByText("POSE Academy", { exact: false }).first()).toBeVisible();
  const sessionResponse = await page.request.get("/api/session/me");
  expect(sessionResponse.status()).toBe(200);
  const session = await sessionResponse.json() as { user: { email: string; fullName: string }; organization: { name: string } };
  expect(session.user.email).toBe("edu.s01@example.com");
  expect(session.organization.name).toBe("POSE Academy");
  await expect(page.getByRole("button", { name: /Menu người dùng:/ })).toHaveAttribute("aria-label", `Menu người dùng: ${session.user.fullName}`);
  const previousUser = await page.getByRole("button", { name: /Menu người dùng:/ }).getAttribute("aria-label");
  await page.goto("/app/khach-hang-360");
  await expect(page.getByLabel("Tìm khách hàng")).toBeVisible();
  const previousCustomer = await page.getByRole("table", { name: "Danh sách khách hàng" }).locator("tbody tr").first().locator("td").first().innerText();
  await page.getByRole("button", { name: /Menu người dùng:/ }).click();
  await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Đăng xuất" }).click();
  await expect(page).toHaveURL(/\/dang-nhap/);
  const next = await page.request.post("/api/session/login", {
    headers: { Origin: "http://127.0.0.1:3011" },
    data: { organizationCode: "EDU", email: "edu.data@example.com", password },
  });
  expect(next.status()).toBe(200);
  await page.goto("/app/khach-hang-360");
  await expect(page.getByRole("heading", { name: "Đối soát dữ liệu" })).toBeVisible();
  await expect(page.getByLabel("Tìm khách hàng")).toHaveCount(0);
  await expect(page.locator("main")).not.toContainText(previousCustomer);
  await expect(page.getByRole("button", { name: /Menu người dùng:/ })).not.toHaveAttribute("aria-label", previousUser ?? "");
});
