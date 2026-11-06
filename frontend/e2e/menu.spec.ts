import { expect, test } from "@playwright/test";
import { authPath } from "./helpers";

// W10: Đối chiếu roles-authorization-v1 §2–3, DEC-11/12/15 và /api/session/me
// trên bản sao EDU cô lập ngày 05/11/2026; danh sách chưa triển khai luôn ẩn.
const futureItems = ["Bàn giao khách hàng", "Yêu cầu hỗ trợ", "Nhập dữ liệu", "Người dùng", "Nhóm", "Vai trò & quyền", "Nhật ký hệ thống"];
const expectations: Record<string, { shown: string[]; hidden: string[] }> = {
  // ADMIN: session có offering.manage, sales_assignment.manage, customer/lead/opportunity/interaction.read, task.manage, customer_360.read.
  admin: {
    shown: ["Tổng quan", "Sản phẩm & dịch vụ", "Phân công sản phẩm", "Lead", "Khách hàng", "Cơ hội bán hàng", "Tương tác", "Nhiệm vụ", "Hồ sơ khách hàng 360"],
    hidden: [...futureItems],
  },
  // MANAGER: session có cùng permission menu với ADMIN; phạm vi dữ liệu theo team quản lý.
  mgr: {
    shown: ["Tổng quan", "Sản phẩm & dịch vụ", "Phân công sản phẩm", "Lead", "Khách hàng", "Cơ hội bán hàng", "Tương tác", "Nhiệm vụ", "Hồ sơ khách hàng 360"],
    hidden: [...futureItems],
  },
  // SALES_REP: session có offering.read, customer/lead/opportunity/interaction.read, task.manage, customer_360.read; không có sales_assignment.manage.
  s01: {
    shown: ["Tổng quan", "Sản phẩm & dịch vụ", "Lead", "Khách hàng", "Cơ hội bán hàng", "Tương tác", "Nhiệm vụ", "Hồ sơ khách hàng 360"],
    hidden: ["Phân công sản phẩm", ...futureItems],
  },
  // SALES_REP + CUSTOMER_CARE: hợp permission hai role; session không có sales_assignment.manage.
  s02: {
    shown: ["Tổng quan", "Sản phẩm & dịch vụ", "Lead", "Khách hàng", "Cơ hội bán hàng", "Tương tác", "Nhiệm vụ", "Hồ sơ khách hàng 360"],
    hidden: ["Phân công sản phẩm", ...futureItems],
  },
  // CUSTOMER_CARE: session có customer/interaction.read, task.manage, customer_360.read; thiếu opportunity.read (DEC-12 F4, lệch ma trận và đã ghi FINDING).
  care: {
    shown: ["Tổng quan", "Khách hàng", "Tương tác", "Nhiệm vụ", "Hồ sơ khách hàng 360"],
    hidden: ["Sản phẩm & dịch vụ", "Phân công sản phẩm", "Lead", "Cơ hội bán hàng", ...futureItems],
  },
  // DATA_STAFF: session có interaction.read và customer_360.read, không có task.manage; danh sách Customer/Lead và Opportunity chưa có scope theo DEC-11/12/15.
  data: {
    shown: ["Tổng quan", "Tương tác", "Hồ sơ khách hàng 360"],
    hidden: ["Sản phẩm & dịch vụ", "Phân công sản phẩm", "Lead", "Khách hàng", "Cơ hội bán hàng", "Nhiệm vụ", ...futureItems],
  },
};

for (const [role, expected] of Object.entries(expectations)) {
  test.describe(`menu permissions: ${role}`, () => {
    test.use({ storageState: authPath(role) });
    test("shows the independently specified menu", async ({ page }) => {
      await page.goto("/app");
      const nav = page.getByRole("navigation", { name: "Điều hướng chính" }).first();
      await expect(nav).toBeVisible();
      const session = await page.request.get("/api/session/me");
      expect(session.ok()).toBeTruthy();
      const body = await session.json();
      expect(body.user.permissions).toContain("dashboard.read");
      const shown = [...expected.shown, "Báo cáo"];
      for (const label of shown) await expect(nav.getByRole("link", { name: label, exact: true })).toHaveCount(1);
      for (const label of expected.hidden) await expect(nav.getByRole("link", { name: label, exact: true })).toHaveCount(0);
      await expect(nav.getByRole("link")).toHaveCount(shown.length);
      if (["s01", "s02", "care", "data"].includes(role)) {
        await page.goto("/app/phan-cong");
        await expect(page.getByText("Bạn chưa có quyền quản lý phân công")).toBeVisible();
      }
    });
  });
}
