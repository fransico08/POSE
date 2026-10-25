# Fraunces tự host cho landing POSE

## Nguồn và phiên bản

- Bản nhị phân đã nghiệm thu do Tài bàn giao tại `D:\POSE\handoff\fraunces-700-800-known-good`, trích từ build Next 16.4.0. Đã kiểm toàn bộ SHA-256 của manifest nguồn trước khi copy; chỉ đổi tên file, không subset/convert hoặc tải font lại từ Google.
- Font family: **Fraunces**. Name table trong cả ba WOFF2: **Version 1.000;[b76b70a41]**. Hai descriptor CSS normal 700/800 dùng chung ba file này như bản đã nghiệm thu.
- Dự án font chính thức: [undercasetype/Fraunces](https://github.com/undercasetype/Fraunces).
- Giấy phép **SIL Open Font License 1.1**, file [OFL.txt](OFL.txt) lấy nguyên nội dung từ [repo chính thức tại revision 284bc5ea73b1233a05d6ec644e641b9404cba8a0](https://github.com/undercasetype/Fraunces/blob/284bc5ea73b1233a05d6ec644e641b9404cba8a0/OFL.txt). SHA-256 bản OFL lưu local: `bdf4c22802eaf804f998195871c6b8938aac2ac14b2d78a8bd66a6f1eced833b`.

## File, dung lượng và checksum

| File local | Subset | Byte | SHA-256 |
|---|---|---:|---|
| fraunces-vietnamese.woff2 | vietnamese | 11.536 | 250cc2966c658fb6d336731de9d82a8129025e9839c20c253bbc477852f6cf4f |
| fraunces-latin-ext.woff2 | latin-ext | 33.640 | f1451edd6434085c4f9f3a8b4a674182dd7d6acccf53bfced19fd167f0705a06 |
| fraunces-latin.woff2 | latin | 36.560 | 88e17be075f1be50ab67b057b99e3701b828f44ed28f9452df6c02645bb0cba9 |

Tổng **81.736 byte**, ngân sách tối đa **81.920 byte**. Manifest máy đọc ở [SHA256SUMS.txt](SHA256SUMS.txt). File nguồn lần lượt là `1e219c03c996efbd-s.p.2qo5md4hn_gum.woff2`, `b35b0dbffda7f2c4-s.p.1d9rlgtqyj_-l.woff2`, `03bda585a99c6450-s.p.32sris142tqlb.woff2`.

## Cách sử dụng và bảo trì

Chỉ `src/styles/landing-fraunces.css` khai báo sáu @font-face (700/800 × ba subset), được landing-v2.css import; unicode-range giữ nguyên CSS 16.4.0 trong test fixture. Fallback Times New Roman giữ ascent 84.71%, descent 22.09%, line-gap 0.0%, size-adjust 115.45%. `--landing-display-font` là `"Fraunces", "Fraunces Fallback", serif`.

HomePage route `/` preload đúng ba URL `/fonts/fraunces/fraunces-*.woff2` với as=font/type=font/woff2/crossOrigin=anonymous. Không khai báo preload trong root layout hoặc app. Không import Fraunces từ next/font/google; Be Vietnam Pro của app giữ nguyên.

Không cập nhật font tự động khi nâng Next hoặc chạy build. Test chặn sai SHA/dung lượng/unicode-range/fallback và kiểm preload; kiểm glyph H1/H2 bằng Chrome production trước khi chấp nhận thay font. Thư mục handoff gốc thuộc Tài, không xóa khi dọn artifact.
