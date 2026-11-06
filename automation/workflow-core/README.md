# POSE Workflow Core

Kernel Java thuần cho W08-01. Module này không phải Spring Boot runtime và không
kết nối cơ sở dữ liệu; các `port` mô tả boundary phải được adapter Spring/JDBC
triển khai trên backend tích hợp. Xem `docs/implementation/w08-01-workflow-handoff.md`.

Chạy test độc lập với JDK 17+:

```powershell
javac -d automation/workflow-core/out (Get-ChildItem automation/workflow-core/src -Recurse -Filter *.java | ForEach-Object FullName)
java -ea -cp automation/workflow-core/out com.pose.workflow.WorkflowWorkerTest
```
