import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DateInput } from "@/components/ui/DateInput";
import { DateTimeInput } from "@/components/ui/DateTimeInput";

describe("localized date inputs", () => {
  it("shows a dd/MM/yyyy text field and formats dates picked from the calendar", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { container } = render(<div><label htmlFor="date">Ngày</label><DateInput id="date" value="" onChange={onChange} /></div>);
    const input = screen.getByLabelText("Ngày");
    expect(input).toHaveAttribute("placeholder", "dd/MM/yyyy");

    await user.type(input, "02/11/2026");
    expect(onChange).toHaveBeenLastCalledWith("02/11/2026");

    const nativePicker = container.querySelector<HTMLInputElement>('input[type="date"]');
    expect(nativePicker).not.toBeNull();
    fireEvent.change(nativePicker!, { target: { value: "2026-12-15" } });
    expect(input).toHaveValue("15/12/2026");
  });

  it("shows a dd/MM/yyyy HH:mm text field and formats date-time picker values", () => {
    const onChange = vi.fn();
    const { container } = render(<div><label htmlFor="date-time">Thời điểm</label><DateTimeInput id="date-time" value="" onChange={onChange} /></div>);
    const input = screen.getByLabelText("Thời điểm");
    expect(input).toHaveAttribute("placeholder", "dd/MM/yyyy HH:mm");

    const nativePicker = container.querySelector<HTMLInputElement>('input[type="datetime-local"]');
    expect(nativePicker).not.toBeNull();
    fireEvent.change(nativePicker!, { target: { value: "2026-12-15T09:30" } });
    expect(input).toHaveValue("15/12/2026 09:30");
    expect(onChange).toHaveBeenLastCalledWith("15/12/2026 09:30");
  });
});
