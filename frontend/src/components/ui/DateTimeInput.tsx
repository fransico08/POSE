"use client";

import { useRef, useState, type InputHTMLAttributes } from "react";
import { CalendarDaysIcon } from "@heroicons/react/24/outline";
import { formatLocalDateTimeAsDdMmYyyyHhMm, parseDdMmYyyyHhMm } from "@/lib/date-format";

type DateTimeInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "value" | "onChange" | "placeholder" | "inputMode"> & {
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
};

export function DateTimeInput({ id, value, onChange, min, max, disabled, className, ...inputProps }: DateTimeInputProps) {
  const [displayValue, setDisplayValue] = useState(value);
  const pickerRef = useRef<HTMLInputElement>(null);

  function openPicker() {
    const picker = pickerRef.current;
    if (!picker || disabled) return;
    if (typeof picker.showPicker === "function") {
      try {
        picker.showPicker();
        return;
      } catch {
        // Fall back to the browser's regular date and time input interaction.
      }
    }
    picker.focus();
    picker.click();
  }

  function changeDisplayValue(nextValue: string) {
    setDisplayValue(nextValue);
    onChange(nextValue);
  }

  return <div className="date-input-control">
    <input
      {...inputProps}
      id={id}
      className={`input date-input-text${className ? ` ${className}` : ""}`}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder="dd/MM/yyyy HH:mm"
      maxLength={16}
      value={displayValue}
      disabled={disabled}
      onChange={event => changeDisplayValue(event.target.value)}
    />
    <button className="date-input-calendar" type="button" aria-label="Mở lịch chọn ngày và giờ" disabled={disabled} onClick={openPicker}>
      <CalendarDaysIcon aria-hidden="true" />
    </button>
    <input
      ref={pickerRef}
      className="sr-only"
      type="datetime-local"
      tabIndex={-1}
      aria-hidden="true"
      value={parseDdMmYyyyHhMm(displayValue) ?? ""}
      min={min}
      max={max}
      disabled={disabled}
      onChange={event => changeDisplayValue(formatLocalDateTimeAsDdMmYyyyHhMm(event.target.value))}
    />
  </div>;
}
