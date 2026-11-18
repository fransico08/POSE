# Chạy đánh giá AI-10

Chỉ chạy khi đã có file theo [định dạng AI-10](ai-10-eval-format.md) và được phép dùng dữ liệu. Build từ gốc bằng `mvn -pl backend -am package -DskipTests` (không chạy `mvn clean`). Công cụ chỉ chạy khi bật profile `ai-eval`; ứng dụng bình thường không tự đánh giá. Mỗi run có tổ chức đánh giá không có user/hồ sơ và không tạo `customer_signals`, suggestion hay Task.

Chuẩn bị database PostgreSQL **riêng** tên `pose_ai_eval_<tên>` trên cluster kiểm thử `127.0.0.1:54329`. Trước mọi thao tác DB, kiểm `SHOW data_directory` là `D:/POSE/backend/target/auth-postgres-test`, theo guard trong `backend/README.md`. Công cụ tự kiểm URL trước khi khởi tạo app và kiểm lại `data_directory` trước Flyway; từ chối 5432, Supabase, database tên khác và URL override khác datasource. H2 bộ nhớ dùng được trên classpath test; H2 không được đóng gói runtime trong JAR. Không dùng database demo hay Supabase.

Ví dụ PowerShell chạy mock sau khi chuẩn bị cluster và database riêng:

```powershell
$env:POSE_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/pose_ai_eval_review'
$env:POSE_DATABASE_USERNAME = '<user-cua-cluster-test>'
$env:POSE_DATABASE_PASSWORD = '<chi-nhap-trong-session-neu-cluster-yeu-cau>'
$poseEvalJwt = New-Object byte[] 32
$poseEvalRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$poseEvalRng.GetBytes($poseEvalJwt)
$poseEvalRng.Dispose()
$env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($poseEvalJwt)
$env:POSE_AI_ENABLED = 'true'
$env:POSE_AI_PROVIDER = 'mock'
java -jar backend/target/pose-backend-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=ai-eval `
  '--pose.ai.eval.file=D:\AI-EVAL\labelled.jsonl' `
  '--pose.ai.eval.output-dir=D:\AI-EVAL\results' `
  --pose.ai.eval.split=test --pose.ai.eval.max-samples=100 `
  --pose.ai.eval.delay-ms=1000 --pose.ai.eval.max-consecutive-errors=3 `
  --pose.ai.eval.max-attempts=3
```

Nhập mật khẩu database/khóa bằng prompt kín hoặc secret manager, không thay placeholder bằng secret trong file/script hoặc chat. Không đặt provider thật trong CI. Tài tự đặt `POSE_AI_PROVIDER=gemini` hoặc `claude` cùng biến khóa tương ứng theo [hướng dẫn CRM.AI](../huong-dan-chay-local.md#biến-môi-trường-crmai) khi được duyệt; đặt ngân sách, delay và timeout phù hợp.

Mặc định output ở thư mục `POSE-ai-eval` trong home, ngoài repo; tùy chọn output khác cũng phải ở ngoài repo. `run.json` có UUID run, commit, trạng thái thay đổi tracked, SHA-256 file, split, cỡ mẫu, provider/model/prompt, thời gian, cấu hình và số lần gọi lại sau mất checkpoint. Để tái lập từ commit, chạy sau khi các thay đổi mã đã được commit (`gitDirty=false`); cờ này không lưu toàn bộ diff hoặc kiểm tra file untracked. `per-sample.jsonl` chỉ chứa kết quả cuối; `attempts.jsonl` ghi ID mẫu/lượt/invocation/trạng thái/mã lỗi, không ghi nội dung. `metrics.json` có ma trận, theo ngành, kappa và lỗi hạ tầng; `report.md` là tóm tắt tiếng Việt. Không commit kết quả chạy thật.

Để tiếp tục, chạy lại cùng lệnh với `--pose.ai.eval.run-id=<UUID>` và giữ nguyên file, commit, cấu hình, provider/model/prompt và output. Chỉ mẫu có kết quả cuối được bỏ qua. RATE_LIMIT, NETWORK, PROVIDER_ERROR, TIMEOUT, DISABLED, BLOCKED_BUDGET, STORAGE_ERROR, BUDGET_ERROR và lỗi nội bộ khác là tạm thời; mỗi lần thử có ID riêng. Mặc định tối đa 3 lượt/mẫu (`--pose.ai.eval.max-attempts`); hết lượt tạo kết quả `EXHAUSTED_RETRIES` với mã lỗi gốc và loại khỏi các chỉ số chất lượng. Nếu invocation SUCCEEDED bị mất checkpoint, công cụ gọi lại khi còn lượt vì Gateway không lưu output thô; nếu hết lượt thì ghi `CHECKPOINT_LOST` và loại mẫu khỏi F1, không tính sai. Khi process chết trước khi lưu trạng thái lượt thử và không có invocation trong DB, lượt đó được ghi `INTERRUPTED` để tránh gọi lặp không kiểm soát.

RATE_LIMIT/NETWORK/PROVIDER_ERROR/TIMEOUT liên tiếp đạt ngưỡng thì dừng, DISABLED/BLOCKED_BUDGET dừng ngay. `complete=false` và báo cáo có dòng **KẾT QUẢ TẠM — chưa đủ mẫu** cho đến khi mọi mẫu có kết quả cuối. Các checkpoint của phiên bản cũ được chuyển khi resume; commit cũ được giữ trong `originalGitCommit`. `metrics.json` tách lỗi hạ tầng, ID mẫu hết lượt và số mẫu thực sự dùng tính F1. Token và chi phí ước tính lấy mọi invocation; chi phí trên 100 tin dùng số mẫu đã có lượt thử. p50/p95 dùng nearest-rank trên các invocation `SUCCEEDED`, kèm số invocation. Nếu DB không ghi được invocation, không thể thu hồi token/chi phí của lượt đó từ DB.
