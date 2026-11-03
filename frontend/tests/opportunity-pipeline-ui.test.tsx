import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { OpportunityPipeline } from "@/components/opportunities/OpportunityPipeline";
import { previewCustomers, previewOfferings, previewOpportunities } from "@/components/opportunities/preview-data";
import { formatIsoDateAsDdMmYyyy, formatLocalDateTimeAsDdMmYyyyHhMm, parseDdMmYyyy, parseDdMmYyyyHhMm } from "@/lib/date-format";

function show(canWrite = true) {
  return render(<OpportunityPipeline initialOpportunities={previewOpportunities} customers={previewCustomers} offerings={previewOfferings} canWrite={canWrite} preview />);
}

describe("Opportunity pipeline acceptance", () => {
  it("uses dd/MM/yyyy for display and accepts valid calendar dates only", () => {
    expect(formatIsoDateAsDdMmYyyy("2026-11-02")).toBe("02/11/2026");
    expect(parseDdMmYyyy("02/11/2026")).toBe("2026-11-02");
    expect(parseDdMmYyyy("31/02/2026")).toBeNull();
    expect(parseDdMmYyyy("11/02/2026")).toBe("2026-02-11");
    expect(formatLocalDateTimeAsDdMmYyyyHhMm("2026-11-02T03:40")).toBe("02/11/2026 03:40");
    expect(parseDdMmYyyyHhMm("02/11/2026 03:40")).toBe("2026-11-02T03:40");
    expect(parseDdMmYyyyHhMm("02/11/2026 24:40")).toBeNull();
  });

  it("shows all stages, a multi-offering opportunity and forecast value", () => {
    show();
    for (const stage of ["Mới", "Đủ điều kiện", "Đề xuất", "Thành công", "Thất bại"]) expect(screen.getByRole("heading", { level: 2, name: stage })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Đào tạo tiếng Anh cho đội ngũ kinh doanh" })).toBeVisible();
    expect(screen.getByText("Khóa học Business English × 2")).toBeVisible();
    expect(screen.getAllByText("Tư vấn CRM × 1").length).toBeGreaterThan(0);
    expect(screen.getByText(/24\.920\.000/)).toBeVisible();
  });

  it("filters opportunities by name, customer or offering and shows a useful empty state", async () => {
    const user = userEvent.setup(); show();
    const search = screen.getByRole("textbox", { name: "Tìm cơ hội" });
    await user.type(search, "Sao Mai");
    expect(screen.getByRole("heading", { name: "Đào tạo phân tích dữ liệu" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Đào tạo tiếng Anh cho đội ngũ kinh doanh" })).toBeNull();
    await user.clear(search); await user.type(search, "không tồn tại");
    expect(screen.getByRole("heading", { name: "Không tìm thấy cơ hội" })).toBeVisible();
  });

  it("creates a preview opportunity with multiple lines and percentage discounts", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    await user.type(within(dialog).getByLabelText("Tên cơ hội *"), "Gói đào tạo kết hợp");
    await user.selectOptions(within(dialog).getByLabelText("Khách hàng *"), "customer-1");
    await user.type(within(dialog).getByLabelText("Ngày dự kiến chốt *"), "15/12/2026");
    await user.click(within(dialog).getByRole("button", { name: "+ Thêm dòng" }));
    await user.selectOptions(within(dialog).getByLabelText("Sản phẩm/dịch vụ *", { selector: "#line-2-offering" }), "offering-2");
    await user.clear(within(dialog).getByLabelText("Chiết khấu (%) *", { selector: "#line-2-discount" }));
    await user.type(within(dialog).getByLabelText("Chiết khấu (%) *", { selector: "#line-2-discount" }), "10");
    expect(within(dialog).getByText(/18\.150\.000/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Tạo cơ hội" }));
    const createdCard = await screen.findByRole("article", { name: "Gói đào tạo kết hợp" });
    expect(within(createdCard).getByText("15/12/2026")).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("chưa được lưu lên hệ thống");
  });

  it("keeps the date form open and explains an invalid dd/MM/yyyy value", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    await user.type(within(dialog).getByLabelText("Tên cơ hội *"), "Ngày không hợp lệ");
    await user.selectOptions(within(dialog).getByLabelText("Khách hàng *"), "customer-1");
    const date = within(dialog).getByLabelText("Ngày dự kiến chốt *");
    expect(date).toHaveAttribute("placeholder", "dd/MM/yyyy");
    await user.type(date, "31/02/2026");
    await user.click(within(dialog).getByRole("button", { name: "Tạo cơ hội" }));
    expect(within(dialog).getByText("Ngày dự kiến chốt phải là ngày hợp lệ theo định dạng dd/MM/yyyy.")).toBeVisible();
    expect(date).toHaveAttribute("aria-invalid", "true");
    expect(dialog).toBeVisible();
  });

  it("styles line removal as a destructive action and preserves at least one line", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    await user.click(within(dialog).getByRole("button", { name: "+ Thêm dòng" }));
    const removeSecond = within(dialog).getByRole("button", { name: "Xóa dòng 2" });
    expect(removeSecond).toHaveClass("button-danger");
    await user.click(removeSecond);
    expect(within(dialog).queryByLabelText("Sản phẩm/dịch vụ *", { selector: "#line-2-offering" })).toBeNull();
    expect(within(dialog).getByLabelText("Sản phẩm/dịch vụ *", { selector: "#line-1-offering" })).toBeVisible();
    expect(within(dialog).queryByRole("button", { name: "Xóa dòng 1" })).toBeNull();
  });

  it("keeps the form open and marks required fields when creation data is missing", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    await user.click(within(dialog).getByRole("button", { name: "Tạo cơ hội" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Vui lòng kiểm tra");
    expect(within(dialog).getByText("Vui lòng nhập tên cơ hội.")).toBeVisible();
    expect(within(dialog).getByLabelText("Tên cơ hội *")).toHaveAttribute("aria-invalid", "true");
    expect(dialog).toBeVisible();
  });

  it("rejects a discount outside the percentage range and points to the invalid line", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    await user.type(within(dialog).getByLabelText("Tên cơ hội *"), "Kiểm tra chiết khấu");
    await user.selectOptions(within(dialog).getByLabelText("Khách hàng *"), "customer-1");
    await user.type(within(dialog).getByLabelText("Ngày dự kiến chốt *"), "15/12/2026");
    fireEvent.change(within(dialog).getByLabelText("Chiết khấu (%) *", { selector: "#line-1-discount" }), { target: { value: "101" } });
    await user.click(within(dialog).getByRole("button", { name: "Tạo cơ hội" }));
    expect(within(dialog).getByText("Chiết khấu phải từ 0% đến 100%." )).toBeVisible();
    expect(within(dialog).getByLabelText("Chiết khấu (%) *", { selector: "#line-1-discount" })).toHaveAttribute("aria-invalid", "true");
    expect(dialog).toBeVisible();
  });

  it("does not display a negative-zero total while a line has invalid quantity or discount", async () => {
    const user = userEvent.setup(); show();
    await user.click(screen.getByRole("button", { name: "+ Thêm cơ hội" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo cơ hội bán hàng" });
    fireEvent.change(within(dialog).getByLabelText("Số lượng *", { selector: "#line-1-quantity" }), { target: { value: "0" } });
    fireEvent.change(within(dialog).getByLabelText("Chiết khấu (%) *", { selector: "#line-1-discount" }), { target: { value: "101" } });
    expect(dialog.querySelector(".opportunity-line-total strong")).toHaveTextContent("—");
    expect(dialog.querySelector(".opportunity-total strong")).toHaveTextContent("—");
    expect(dialog).not.toHaveTextContent("-0 ₫");
  });

  it("allows forward-only stage movement and requires a reason and actual amount/date for WON", async () => {
    const user = userEvent.setup(); show();
    const qualified = screen.getByRole("region", { name: "Đủ điều kiện" });
    await user.click(within(qualified).getByRole("button", { name: "Xem / chỉnh sửa" }));
    const dialog = await screen.findByRole("dialog", { name: "Chỉnh sửa cơ hội" });
    const stage = within(dialog).getByLabelText("Giai đoạn");
    expect(within(stage).queryByRole("option", { name: "Thất bại" })).toBeNull();
    await user.selectOptions(stage, "PROPOSAL");
    await user.click(within(dialog).getByRole("button", { name: "Lưu thay đổi" }));
    const proposal = screen.getByRole("region", { name: "Đề xuất" });
    expect(within(proposal).getByRole("heading", { name: "Bồi dưỡng kỹ năng giao tiếp" })).toBeVisible();

    const proposalCard = within(proposal).getByRole("article", { name: "Bồi dưỡng kỹ năng giao tiếp" });
    await user.click(within(proposalCard).getByRole("button", { name: "Xem / chỉnh sửa" }));
    const closeDialog = await screen.findByRole("dialog", { name: "Chỉnh sửa cơ hội" });
    await user.selectOptions(within(closeDialog).getByLabelText("Giai đoạn"), "WON");
    await user.click(within(closeDialog).getByRole("button", { name: "Lưu thay đổi" }));
    expect(within(closeDialog).getByText("Vui lòng nhập lý do chốt cơ hội.")).toBeVisible();
    expect(within(closeDialog).getByText("Giá trị chốt phải là số không âm.")).toBeVisible();
    expect(within(closeDialog).getByText("Vui lòng nhập ngày chốt thực tế.")).toBeVisible();

    await user.type(within(closeDialog).getByLabelText("Lý do chốt *"), "Khách hàng ký hợp đồng");
    await user.type(within(closeDialog).getByLabelText("Giá trị chốt (VND) *"), "8200000");
    await user.type(within(closeDialog).getByLabelText("Ngày chốt thực tế *"), "02/11/2026");
    await user.click(within(closeDialog).getByRole("button", { name: "Lưu thay đổi" }));
    const won = screen.getByRole("region", { name: "Thành công" });
    const wonCard = within(won).getByRole("article", { name: "Bồi dưỡng kỹ năng giao tiếp" });
    expect(within(wonCard).getByText(/Giá trị chốt/)).toBeVisible();
    expect(within(wonCard).getByText(/Giá trị chốt/).parentElement).toHaveTextContent("8.200.000");
    expect(within(wonCard).getByText("Ngày chốt thực tế: 02/11/2026")).toBeVisible();
  });

  it("requires a closing reason for LOST and moves the opportunity to the lost column", async () => {
    const user = userEvent.setup(); show();
    const proposal = screen.getByRole("region", { name: "Đề xuất" });
    const card = within(proposal).getByRole("article", { name: "Đào tạo phân tích dữ liệu" });
    await user.click(within(card).getByRole("button", { name: "Xem / chỉnh sửa" }));
    const dialog = await screen.findByRole("dialog", { name: "Chỉnh sửa cơ hội" });
    await user.selectOptions(within(dialog).getByLabelText("Giai đoạn"), "LOST");
    await user.click(within(dialog).getByRole("button", { name: "Lưu thay đổi" }));
    expect(within(dialog).getByText("Vui lòng nhập lý do chốt cơ hội.")).toBeVisible();
    await user.type(within(dialog).getByLabelText("Lý do chốt *"), "Khách hàng chọn nhà cung cấp khác");
    await user.click(within(dialog).getByRole("button", { name: "Lưu thay đổi" }));
    const lost = screen.getByRole("region", { name: "Thất bại" });
    const lostCard = within(lost).getByRole("article", { name: "Đào tạo phân tích dữ liệu" });
    expect(within(lostCard).getByText(/Khách hàng chọn nhà cung cấp khác/)).toBeVisible();
    expect(within(lostCard).queryByRole("button", { name: "Xem / chỉnh sửa" })).toBeNull();
  });

  it("hides create and edit actions from users without write permission", () => {
    show(false);
    expect(screen.queryByRole("button", { name: "+ Thêm cơ hội" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Xem / chỉnh sửa" })).toBeNull();
  });

  it("shows actual-versus-forecast on won opportunities and locks their edit actions", () => {
    show();
    const won = screen.getByRole("region", { name: "Thành công" });
    expect(within(won).getByText(/So với dự kiến/)).toBeVisible();
    expect(within(won).getByText("11.800.000 ₫")).toBeVisible();
    expect(within(won).queryByRole("button", { name: "Xem / chỉnh sửa" })).toBeNull();
  });
});
