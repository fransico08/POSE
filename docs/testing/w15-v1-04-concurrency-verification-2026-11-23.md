# W15-V1-04: Xac minh dong thoi AI-07

## Pham vi

File test doc lap `W15ConcurrencyVerificationTest` chi dung `MockProvider`, khong sua ma san xuat, migration, W12 hoac W13.

## Ket qua can xac minh

| Kiem tra | Trang thai | Chung cu mong doi |
| --- | --- | --- |
| Hai worker extract cung mot message | NOT RUN | Mot `customer_signals`, mot `customer_signal_extractions`, hai `ai_invocations`/MockProvider calls. Day la race da biet chua duoc sua. |
| Retry sau extract thanh cong | NOT RUN | Mot signal, mot extraction, mot `ai_invocations`. |
| Accept va edit dong thoi | NOT RUN | Mot HTTP 200, mot HTTP 409, mot audit `AI_SIGNAL_ACCEPTED` hoac `AI_SIGNAL_EDITED`, trang thai cuoi ACCEPTED hoac EDITED. |

## Ket luan hien trang

Unique write bao ve du lieu persisted, va review dung `FOR UPDATE` nen co mot quyet dinh cuoi cung mot audit. Tuy nhien extract chua claim message truoc khi goi provider; hai worker co the goi provider hai lan truoc khi unique write loai ban ghi trung. Test dau tien ghi nhan race nay, khong coi la PASS cho acceptance "mot provider invocation".

## Lenh chay

```powershell
$env:JAVA_HOME='C:\jdk-21.0.13.1+2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\apache-maven-3.10.0\bin\mvn.cmd' -pl backend -am '-Dtest=W15ConcurrencyVerificationTest,CustomerSignalIntegrationTest,CustomerHumanSignalIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Trang thai

NOT RUN: file dang duoc stage tai workspace chinh va chua duoc copy vao publish worktree. Cap nhat bang ket qua Maven thuc te sau khi chay lenh tren publish worktree.
