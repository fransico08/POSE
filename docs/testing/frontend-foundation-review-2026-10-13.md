# Sửa review frontend foundation — 13/10/2026

Owner: Huỳnh Minh Tài · Nhánh Tài · Commit cục bộ, không push.

Review của Tài phát hiện ba lỗi chưa được bộ kiểm ngày 12/10 bao phủ đúng. Báo cáo này ghi phép kiểm bổ sung; không lấy kết quả trước làm bằng chứng cho clean install hoặc refresh mất mạng.

## 1. Lockfile đồng bộ

Chạy `npm install --no-fund --no-audit` trong frontend. Giữ nguyên package.json và toàn bộ version trực tiếp. Lock mới sửa @emnapi/wasi-threads cấp gốc thành 1.2.3; bổ sung @emnapi/core 1.10.0 và wasi-threads 1.2.1 lồng dưới resolver WASM theo dependency graph.

Tạo thư mục tạm mới, chỉ copy các file frontend do Git quản lý từ worktree. Trước cài xác nhận không có node_modules, .next hoặc next-env.d.ts sinh tự động; không copy dependency/build cache từ worktree. npm được phép dùng download cache thông thường. Node 24.13.0/npm 11.6.2.

| Lệnh trên bản sao sạch thứ nhất | Kết quả |
|---|---|
| npm ci --no-fund --no-audit | Exit 0, cài 448 package; không lỗi lockfile |
| npm run lint | Exit 0, không warning lint |
| npm run typecheck | Exit 0, trước build và chưa có .next |
| npm run test | Exit 0, 41/41 test trong 5 file |
| npm run build | Exit 0, production build |

Helper xuất log lần đầu không mã hóa được ký hiệu Unicode trong output build; đã đặt UTF-8 và chạy lại build thành công. Các lệnh ci/lint/typecheck/test trước đó đều exit 0. npm còn cảnh báo ESLint 9 hết hỗ trợ như evidence cũ; không thay version hoặc stack ngoài yêu cầu.

[Kết quả clean install cho lỗi 1](assets/frontend-foundation-review-2026-10-13/lock-clean-checks.json).

## 2. Refresh khi mất mạng

Đang sửa và bổ sung regression test; chưa ghi PASS cho kiểm backend thật.

## 3. Phân biệt lỗi login

Đang sửa và bổ sung RTL; chưa ghi PASS cho kiểm backend thật. Wordmark Poppins tùy chọn chưa được Tài xác nhận, giữ Be Vietnam Pro.
