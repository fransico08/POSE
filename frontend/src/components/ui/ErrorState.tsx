"use client";
import { Button } from "./Button";
export function ErrorState({ reset, inline = false, message = "Không thể hiển thị trang lúc này. Vui lòng thử lại." }: { reset: () => void; inline?: boolean; message?: string }) { return <section className="status-content">{inline ? <h2>Đã có lỗi xảy ra</h2> : <h1>Đã có lỗi xảy ra</h1>}<p>{message}</p><Button onClick={reset}>Thử lại</Button></section>; }
