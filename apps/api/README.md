# BookFlow API

Backend dùng Java 21, Spring Boot 4.1.0 và Maven Wrapper. Profile local kết nối PostgreSQL Compose; integration test dùng PostgreSQL Testcontainer tạm và Spring Boot `@ServiceConnection`. Lỗi HTTP được chuẩn hóa bằng Spring `ProblemDetail`. Chưa có schema nghiệp vụ hoặc Redis integration.

BF-008 đã được xác minh bằng MockMvc, full Maven verification và runtime smoke test với profile `local`: Actuator health trả `UP`, endpoint không tồn tại trả `ENDPOINT_NOT_FOUND` theo error contract.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify

$env:BOOKFLOW_DB_URL = "jdbc:postgresql://127.0.0.1:5433/bookflow"
$env:BOOKFLOW_DB_USERNAME = "bookflow"
$env:BOOKFLOW_DB_PASSWORD = "<lấy từ .env local>"
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Health URL: `http://127.0.0.1:8080/actuator/health`.

`clean test` chạy unit/application-context test và MockMvc test cho error contract, không cần Docker. `clean verify` chạy thêm `FlywayMigrationIT`; Docker daemon phải hoạt động nhưng không cần biến `BOOKFLOW_TEST_DB_*` hoặc PostgreSQL local.

Xem [Thiết lập Spring Boot local](../../docs/setup/spring-boot-local.md), [Quản lý migration với Flyway](../../docs/setup/flyway.md), [Integration test với Testcontainers](../../docs/setup/testcontainers.md) và [Chuẩn lỗi API](../../docs/standards/api-errors.md).
