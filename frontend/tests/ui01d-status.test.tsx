import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusPill, statusTone } from "@/components/ui/StatusPill";
describe("status labels", () => {
  it("maps Lead and Opportunity statuses with visible text", () => {
    render(<><StatusPill tone={statusTone("QUALIFIED")}>Đủ điều kiện</StatusPill><StatusPill tone={statusTone("LOST")}>Thất bại</StatusPill></>);
    expect(screen.getByText("Đủ điều kiện")).toHaveClass("pose-status-success");
    expect(screen.getByText("Thất bại")).toHaveClass("pose-status-danger");
  });
});
