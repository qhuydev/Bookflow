# Integration test với Testcontainers

## Mục đích

Testcontainers tạo PostgreSQL thật trong Docker cho từng lần integration test. Nhờ đó test không phụ thuộc database được cài sẵn, port local, credential trong `.env` hoặc dữ liệu còn lại từ lần chạy trước. Docker daemon vẫn là điều kiện bắt buộc cho full verification.

PostgreSQL Testcontainer khác service `postgres` trong `compose.yaml`: container Compose phục vụ chạy ứng dụng local và giữ dữ liệu trong named volume; container test chỉ tồn tại cùng Spring test context, dùng dữ liệu tạm và được Testcontainers tự dừng, xóa sau test. Không dùng Testcontainers cho runtime production.

## Thiết kế BF-007

- Image test được ghim `postgres:17.10-alpine`.
- Database tạm là `bookflow_test`; credential chỉ dành cho container test, không lấy từ `.env` và không tái sử dụng production credential.
- Docker ánh xạ cổng `5432` của container sang một host port động. Test không dùng cố định `5433`, nhờ đó tránh xung đột với PostgreSQL Compose và cho phép các lần test độc lập.
- `PostgreSQLContainer` được khai báo thành Spring test bean. `@ServiceConnection` cung cấp connection details cho DataSource và Flyway mà không cần `@DynamicPropertySource` hoặc JDBC URL `jdbc:tc:`.
- Spring quản lý lifecycle container cùng ApplicationContext; không bật reusable container và không gọi Docker CLI từ test.
- Profile `testcontainers` bật Flyway tại `classpath:db/migration`, giữ `validate-on-migrate`, tắt `baseline-on-migrate`, `out-of-order` và vô hiệu hóa `clean`.
- Khi context khởi động, Flyway tự áp dụng migration thật. Test xác nhận V1, checksum, `success`, validate, không có migration pending và lần migrate kế tiếp chạy 0 migration.

## Chạy kiểm thử

Chỉ chạy unit test và application-context test, không khởi động container:

```powershell
.\apps\api\mvnw.cmd clean test
```

Chạy toàn bộ, gồm `FlywayMigrationIT` bằng PostgreSQL Testcontainer:

```powershell
.\apps\api\mvnw.cmd clean verify
```

`clean test` dừng tại phase `test`, nên Surefire không chạy file hậu tố `IT`. `clean verify` tiếp tục qua Failsafe `integration-test` và `verify`. Trong log thành công phải thấy `FlywayMigrationIT`, PostgreSQL 17, mapped port động, `Tests run` không có failure/error/skipped và `BUILD SUCCESS`.

Không cần khai báo `BOOKFLOW_TEST_DB_URL`, `BOOKFLOW_TEST_DB_USERNAME` hoặc `BOOKFLOW_TEST_DB_PASSWORD`. Các biến `BOOKFLOW_DB_*` vẫn chỉ dùng khi chạy ứng dụng với profile `local`.

## Vòng đời và an toàn

Mỗi lần `clean verify` tạo database/container sạch mới. Không bật `.withReuse(true)`, không tắt Ryuk, không mount `bookflow_postgres-data` và không tham gia `bookflow-network`. Container test phải biến mất sau khi Maven kết thúc; PostgreSQL và Redis Compose không bị dừng hay tạo lại.

Không log credential test, không dùng credential production và không dùng database local làm fallback. PostgreSQL Compose vẫn là runtime database local, còn Testcontainer chỉ phục vụ integration test.

## Troubleshooting

- Docker Desktop chưa chạy hoặc daemon không truy cập được: khởi động Docker Desktop, kiểm tra `docker version` và `docker info`, rồi chạy lại `clean verify`. Test phải fail rõ ràng; không chuyển sang PostgreSQL local.
- Không pull được image: kiểm tra mạng/proxy và quyền Docker. Không tắt SSL, sửa quyền hệ thống hoặc đổi image sang `latest` để che lỗi.
- Cảnh báo `.docker\config.json`: nếu Docker vẫn chạy, ghi nhận cảnh báo; nếu pull/start thất bại, xử lý quyền ngoài repository theo quyết định của người dùng.
- `clean test` tạo container: kiểm tra quy ước hậu tố `Test`/`IT` và cấu hình Surefire/Failsafe.
- `clean verify` không chạy `FlywayMigrationIT`: đọc Failsafe report; không dùng profile cũ `flyway-it` để tạo kết quả PASS giả.
- Container còn chạy sau Maven: kiểm tra Docker events và Testcontainers/Ryuk; không tự xóa container để làm sạch báo cáo trước khi xác định nguyên nhân.
