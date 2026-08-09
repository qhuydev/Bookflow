# BookFlow API

Backend dùng Java 21, Spring Boot 4.1.0 và Maven Wrapper. Profile local kết nối PostgreSQL Compose; integration test dùng PostgreSQL Testcontainer tạm và Spring Boot `@ServiceConnection`. Lỗi HTTP được chuẩn hóa bằng Spring `ProblemDetail`. OpenAPI mô tả contract HTTP ở định dạng máy đọc được, còn Swagger UI hỗ trợ đọc và khám phá contract trên trình duyệt. Chưa có schema nghiệp vụ hoặc Redis integration.

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

## OpenAPI và Swagger UI

Sau khi khởi động backend local bằng lệnh trên, truy cập:

- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`.
- Swagger UI: `http://127.0.0.1:8080/swagger-ui/index.html`.

Metadata hiện dùng title `BookFlow API` và version `v1`. OpenAPI mô tả các endpoint đăng ký, CSRF, login, refresh, logout và logout-all; các endpoint Actuator không được đưa vào tài liệu này.

## Thiết kế authentication

[ADR 0001 — Authentication và refresh token](../../docs/adr/0001-authentication-and-refresh-token.md) đã chốt kiến trúc JWT access token, opaque refresh token, session rotation và browser security. [Security review](../../docs/security/authentication-security-review.md) ghi threat model và test matrix. BF-014 đã có `POST /api/v1/auth/register`: email được chuẩn hóa và password được lưu bằng Argon2id.

BF-015 bổ sung `GET /api/v1/auth/csrf` và `POST /api/v1/auth/login`. BF-017/018/019 hoàn thiện refresh rotation, reuse detection, logout và logout-all bằng Bearer JWT principal. Các request thay đổi trạng thái vẫn yêu cầu CSRF; refresh token gốc chỉ nằm trong cookie HttpOnly và PostgreSQL chỉ lưu SHA-256 hash. Xem [authentication local](../../docs/setup/authentication-local.md) để cấu hình PEM local ngoài repository.

## Thiết kế multi-tenancy

[ADR 0002 — Multi-tenancy và membership](../../docs/adr/0002-multi-tenancy-and-membership.md) chốt business là tenant, membership nhiều business cho một user, role `OWNER`/`ADMIN`/`STAFF` và isolation bằng `tenant_id` lấy từ session/membership đã xác thực. [Security review](../../docs/security/multi-tenancy-security-review.md) ghi threat model và test matrix. Đây chỉ là tài liệu thiết kế BF-011; chưa có tenant context runtime, entity, migration hoặc API chuyển business.

`clean test` chạy unit/application-context test và MockMvc test cho error contract, không cần Docker. `clean verify` chạy thêm `FlywayMigrationIT`; Docker daemon phải hoạt động nhưng không cần biến `BOOKFLOW_TEST_DB_*` hoặc PostgreSQL local.

Xem [Thiết lập Spring Boot local](../../docs/setup/spring-boot-local.md), [Quản lý migration với Flyway](../../docs/setup/flyway.md), [Integration test với Testcontainers](../../docs/setup/testcontainers.md) và [Chuẩn lỗi API](../../docs/standards/api-errors.md).
