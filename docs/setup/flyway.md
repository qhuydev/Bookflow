# Quản lý migration với Flyway

## Flyway giải quyết vấn đề gì?

Flyway quản lý thay đổi database bằng các file SQL có version trong source code. Mỗi môi trường chạy cùng một chuỗi migration theo cùng thứ tự, nhờ đó schema có thể tái lập và được review. Không sửa database thủ công vì thay đổi đó không có lịch sử, khó tái tạo và dễ làm các môi trường lệch nhau.

BF-006 kết nối Spring Boot với PostgreSQL local và thêm migration baseline no-op. Ticket này chưa tạo bảng nghiệp vụ BookFlow.

## Quy tắc migration

Migration versioned đặt tên theo mẫu `V<version>__<description>.sql`, ví dụ:

```text
V1__baseline.sql
V2__authentication_schema.sql
V3__password_reset_tokens.sql
V4__business_and_membership_schema.sql
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

## Integration test với Testcontainers

BF-007 dùng PostgreSQL container tạm, do Spring Boot và Testcontainers tạo tự động. Test không đọc `BOOKFLOW_TEST_DB_*`, không dùng PostgreSQL Compose và không cần tạo schema test thủ công. Docker daemon vẫn phải hoạt động.

```powershell
.\apps\api\mvnw.cmd clean verify
```

Spring Boot lấy JDBC/Flyway connection qua `@ServiceConnection`, tự chạy toàn bộ migration hiện có khi context khởi động, rồi test chạy `validate` và xác nhận lần `migrate` tiếp theo có 0 migration mới. Xem [Integration test với Testcontainers](testcontainers.md).

Kiểm tra migration được đóng gói trong executable JAR:

```powershell
jar tf .\apps\api\target\bookflow-api-0.0.1-SNAPSHOT.jar |
  Select-String "db/migration/V4__business_and_membership_schema.sql"
```

## Xử lý checksum mismatch

1. Dừng triển khai và xác định migration nào đã bị thay đổi.
2. Nếu thay đổi ngoài ý muốn, khôi phục file migration đúng từ Git.
3. Nếu cần thay đổi schema mới, giữ migration cũ và tạo version migration tiếp theo.
4. Chỉ cân nhắc `repair` sau khi đã review lịch sử database và có quyết định kỹ thuật rõ ràng; không dùng `repair` chỉ để che lỗi.
5. Không sửa trực tiếp `flyway_schema_history` và không dùng `clean` trên database BookFlow.

Không dùng `repair` để che checksum mismatch và không sửa migration đã áp dụng. Database Testcontainer là tạm thời; runtime local vẫn dùng các biến `BOOKFLOW_DB_*` như trước.
