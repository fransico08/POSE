# POSE Workflow Core

Kernel Java thuần cho W08-01. Module không phụ thuộc Spring; adapter JDBC cần
runtime cung cấp transaction và actor. Backend tích hợp worker theo DEC-16.
Xem `docs/implementation/w08-01-workflow-handoff.md`.

Chạy test với JDK 21 và Maven tại gốc repository:

```powershell
mvn -pl automation/workflow-core test
```

Wrapper JUnit gọi nguyên các kịch bản của `WorkflowWorkerTest` và bật Java assertions.
Không dùng `mvn clean`, vì reactor có thể xóa cluster test dưới backend/target.
