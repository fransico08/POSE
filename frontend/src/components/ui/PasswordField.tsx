"use client";
import { useState } from "react";
import { EyeIcon, EyeSlashIcon } from "@heroicons/react/24/outline";
import type { TextFieldProps } from "./TextField";
export function PasswordField({ id, label, className = "", ...props }: Omit<TextFieldProps, "type" | "helper">) {
  const [visible, setVisible] = useState(false);
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <div className="password-control">
      <input {...props} id={id} type={visible ? "text" : "password"} autoComplete="current-password" className={`input ${className}`} />
      <button type="button" className="icon-button password-toggle" aria-controls={id} aria-pressed={visible} aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"} onClick={() => setVisible(value => !value)}>
        {visible ? <EyeSlashIcon aria-hidden="true" /> : <EyeIcon aria-hidden="true" />}
      </button>
    </div>
  </div>;
}
