# Quản lý migration với Flyway

## Flyway giải quyết vấn đề gì?

Flyway quản lý thay đổi database bằng các file SQL có version trong source code. Mỗi môi trường chạy cùng một chuỗi migration theo cùng thứ tự, nhờ đó schema có thể tái lập và được review. Không sửa database thủ công vì thay đổi đó không có lịch sử, khó tái tạo và dễ làm các môi trường lệch nhau.

BF-006 kết nối Spring Boot với PostgreSQL local và thêm migration baseline no-op. Ticket này chưa tạo bảng nghiệp vụ BookFlow.

## Quy tắc migration

Migration versioned đặt tên theo mẫu `V<version>__<description>.sql`, ví dụ:

```text
V1__baseline.sql
V2__create_businesses.sql
```

Giữa version và description có hai dấu gạch dưới. Migration đã áp dụng là **immutable**: không đổi tên, sửa nội dung hoặc checksum. Thay đổi tiếp theo phải nằm trong một migration version mới.

Flyway ghi lịch sử vào `flyway_schema_history`, gồm version, description, checksum, thời điểm chạy và trạng thái thành công. `validate-on-migrate` so sánh file hiện tại với lịch sử trước khi migrate để phát hiện file bị thiếu hoặc checksum thay đổi.

`baseline-on-migrate` đang tắt để Flyway không tự nhận một database có schema ngoài kiểm soát là baseline hợp lệ. `clean-disabled` đang bật để ứng dụng không thể dùng Flyway xóa schema.

## Cấu hình local

Khởi động PostgreSQL và kiểm tra port thực tế:

```powershell
.\scripts\postgres.ps1 up
.\scripts\postgres.ps1 ready
docker compose port postgres 5432
```

Thiết lập biến chỉ trong PowerShell hiện tại. Điều chỉnh port theo kết quả Docker, ví dụ `5433`:

```powershell
$env:BOOKFLOW_DB_URL = "jdbc:postgresql://127.0.0.1:5433/bookflow"
$env:BOOKFLOW_DB_USERNAME = "bookflow"
$env:BOOKFLOW_DB_PASSWORD = "<lấy từ .env local>"
$env:SPRING_PROFILES_ACTIVE = "local"
```

Không đưa password vào source, tài liệu, Git history hoặc biến công khai phía frontend.

Chạy ứng dụng:

```powershell
.\apps\api\mvnw.cmd spring-boot:run
```

Kiểm tra health và migration:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
.\scripts\postgres.ps1 psql
```

Trong `psql`, có thể kiểm tra lịch sử mà không sửa dữ liệu:

```sql
SELECT version, description, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Integration test với PostgreSQL local

BF-006 dùng database PostgreSQL local nhưng tạo schema tạm ngẫu nhiên `bf006_it_*`, chạy migration hai lần và chỉ xóa schema tạm đó sau test.

```powershell
$env:BOOKFLOW_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:5433/bookflow"
$env:BOOKFLOW_TEST_DB_USERNAME = "bookflow"
$env:BOOKFLOW_TEST_DB_PASSWORD = "<lấy từ .env local>"
.\apps\api\mvnw.cmd clean verify -Pflyway-it
```

Profile `flyway-it` không tự skip khi thiếu biến môi trường. Lần migrate đầu phải chạy V1, `validate` phải thành công và lần migrate thứ hai phải chạy 0 migration mới.

Kiểm tra migration được đóng gói trong executable JAR:

```powershell
jar tf .\apps\api\target\bookflow-api-0.0.1-SNAPSHOT.jar |
  Select-String "db/migration/V1__baseline.sql"
```

## Xử lý checksum mismatch

1. Dừng triển khai và xác định migration nào đã bị thay đổi.
2. Nếu thay đổi ngoài ý muốn, khôi phục file migration đúng từ Git.
3. Nếu cần thay đổi schema mới, giữ migration cũ và tạo version migration tiếp theo.
4. Chỉ cân nhắc `repair` sau khi đã review lịch sử database và có quyết định kỹ thuật rõ ràng; không dùng `repair` chỉ để che lỗi.
5. Không sửa trực tiếp `flyway_schema_history` và không dùng `clean` trên database BookFlow.

BF-007 sẽ thay phụ thuộc vào PostgreSQL local trong integration test bằng Testcontainers để test có database cô lập và tái lập tự động.
