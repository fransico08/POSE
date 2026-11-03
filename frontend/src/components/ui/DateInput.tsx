"use client";

import { useRef, useState, type InputHTMLAttributes } from "react";
import { CalendarDaysIcon } from "@heroicons/react/24/outline";
import { formatIsoDateAsDdMmYyyy, parseDdMmYyyy } from "@/lib/date-format";

type DateInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "value" | "onChange" | "placeholder" | "inputMode"> & {
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
};

export function DateInput({ id, value, onChange, min, max, disabled, className, ...inputProps }: DateInputProps) {
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
        // Fall back to the browser's regular date input interaction.
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
      placeholder="dd/MM/yyyy"
      maxLength={10}
      value={displayValue}
      disabled={disabled}
      onChange={event => changeDisplayValue(event.target.value)}
    />
    <button className="date-input-calendar" type="button" aria-label="Mở lịch chọn ngày" disabled={disabled} onClick={openPicker}>
      <CalendarDaysIcon aria-hidden="true" />
    </button>
    <input
      ref={pickerRef}
      className="sr-only"
      type="date"
      tabIndex={-1}
      aria-hidden="true"
      value={parseDdMmYyyy(displayValue) ?? ""}
      min={min}
      max={max}
      disabled={disabled}
      onChange={event => changeDisplayValue(formatIsoDateAsDdMmYyyy(event.target.value))}
    />
  </div>;
}
