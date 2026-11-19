# Định dạng bộ nhãn đánh giá AI-10

Bộ AI-05 do Văn chuẩn bị; fixture trong `backend/src/test/resources/ai-eval/synthetic.jsonl` chỉ là **dữ liệu tổng hợp kiểm thử**, không phải bộ nhãn đánh giá mô hình. File đầu vào UTF-8 JSONL: mỗi dòng đúng một object, không có dòng trống. `sampleId` duy nhất trong toàn file, gồm 1–80 ký tự ASCII chữ, số, `_`, `-` hoặc `.`; không đưa dữ liệu cá nhân vào ID.

```json
{"sampleId":"DEMO-01","industry":"EDU","split":"test","text":"Tôi muốn hỏi về học phí","goldIntent":"ASK_PRICE","goldSentiment":"NEUTRAL","annotatorA":{"intent":"ASK_PRICE","sentiment":"NEUTRAL"},"annotatorB":{"intent":"ASK_PRICE","sentiment":"NEUTRAL"}}
```

- `industry`: `EDU`, `REA`, `TECH`, `RET`, `LOG`, `CLI`; `split`: `dev` hoặc `test`.
- `text`: chuỗi không rỗng, tối đa 40.000 ký tự. Tránh dùng dữ liệu thật chưa được phép; Gateway vẫn áp redaction và giới hạn context đang cấu hình.
- `goldIntent`: `ASK_PRICE`, `ASK_INFO`, `BOOKING`, `PURCHASE_READY`, `COMPLAINT`, `SUPPORT`, `OTHER`; `goldSentiment`: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Bộ nhãn hiện khớp `SignalOutputFilter` và kiến trúc mục 3.
- `annotatorA`, `annotatorB` tùy chọn; nếu có, mỗi object phải có đúng `intent` và `sentiment` thuộc các danh mục trên. Kappa dùng các dòng đã chấm có đủ cả hai object; báo số cặp thực tế.
- Tệp sai JSON, trường, nhãn hoặc ID trùng bị từ chối với số dòng trước khi gọi AI. Mặc định chọn `test`; dùng `dev` cho phát triển prompt, không báo là kết quả kiểm thử cuối.

Xem [hướng dẫn chạy](ai-10-eval-runbook.md). Output không chép `text`; dữ liệu gốc và output vẫn cần được quản lý theo quyền truy cập của nhóm.

Kết quả theo ngành chỉ tính chỉ số khi có ít nhất 5 mẫu đã chấm trong ngành; ngành ít hơn vẫn hiển thị số mẫu và `INSUFFICIENT_SAMPLES`. Ngưỡng này là quy ước báo cáo của công cụ, không phải bằng chứng đủ mẫu thống kê hay cam kết về chất lượng mô hình.
