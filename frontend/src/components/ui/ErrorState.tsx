"use client";
import { Button } from "./Button";
export function ErrorState({ reset }: { reset: () => void }) { return <section className="status-content"><h1>Đã có lỗi xảy ra</h1><p>Không thể hiển thị trang lúc này. Vui lòng thử lại.</p><Button onClick={reset}>Thử lại</Button></section>; }
