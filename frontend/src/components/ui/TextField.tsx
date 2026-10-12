import type { InputHTMLAttributes, Ref } from "react";
export type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & { id: string; label: string; helper?: string; ref?: Ref<HTMLInputElement> };
export function TextField({ id, label, helper, className = "", ...props }: TextFieldProps) {
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <input {...props} id={id} className={`input ${className}`} aria-describedby={helper ? `${id}-helper` : props["aria-describedby"]} />
    {helper && <p id={`${id}-helper`} className="helper">{helper}</p>}
  </div>;
}
