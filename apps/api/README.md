# BookFlow API

Backend dùng Java 21, Spring Boot 4.1.0 và Maven Wrapper. Profile local kết nối PostgreSQL và Redis Compose; integration test dùng PostgreSQL/Redis Testcontainers tạm. Lỗi HTTP được chuẩn hóa bằng Spring `ProblemDetail`. Đã có API tạo, xem và cập nhật cấu hình business theo membership hiện tại; tenant authorization runtime đọc lại membership từ PostgreSQL.

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

Metadata hiện dùng title `BookFlow API` và version `v1`. OpenAPI mô tả các endpoint đăng ký, CSRF, login, refresh, logout/logout-all và forgot/reset password; các endpoint Actuator không được đưa vào tài liệu này.

## Thiết kế authentication

[ADR 0001 — Authentication và refresh token](../../docs/adr/0001-authentication-and-refresh-token.md) đã chốt kiến trúc JWT access token, opaque refresh token, session rotation và browser security. [Security review](../../docs/security/authentication-security-review.md) ghi threat model và test matrix. BF-014 đã có `POST /api/v1/auth/register`: email được chuẩn hóa và password được lưu bằng Argon2id.

BF-015 bổ sung CSRF/login; BF-017/018/019 hoàn thiện refresh rotation, reuse detection và logout. BF-020 thêm forgot/reset password một lần; BF-021 thêm Redis rate limiting atomically theo IP/account. Các request thay đổi trạng thái vẫn yêu cầu CSRF; PostgreSQL chỉ lưu SHA-256 hash của refresh/reset token. Xem [authentication local](../../docs/setup/authentication-local.md) để cấu hình PEM, Redis và chính sách local.

## Thiết kế multi-tenancy

[ADR 0002 — Multi-tenancy và membership](../../docs/adr/0002-multi-tenancy-and-membership.md) chốt business là tenant, membership nhiều business cho một user, role `OWNER`/`ADMIN`/`STAFF` và isolation bằng `tenant_id` lấy từ session/membership đã xác thực. BF-026–BF-028 thêm `TenantAuthorizationService`: membership/business được đọc lại từ PostgreSQL trên từng request và permission được kiểm tra qua matrix tường minh. BF-025 dùng `BUSINESS_VIEW` cho hai API GET; BF-029 dùng `BUSINESS_CONFIGURATION_MANAGE` cho `PATCH /api/v1/businesses/{businessId}` (`OWNER`/`ADMIN`). Xem [schema](../../docs/setup/business-membership-schema.md), [API tạo](../../docs/setup/business-creation-api.md), [API xem](../../docs/setup/business-query-api.md), [API cập nhật cấu hình](../../docs/setup/business-configuration-api.md) và [tenant authorization](../../docs/setup/tenant-authorization.md). Chưa có tenant switch, API membership hay booking.

`clean test` chạy unit/application-context test và MockMvc test cho error contract, không cần Docker. `clean verify` chạy thêm `FlywayMigrationIT`; Docker daemon phải hoạt động nhưng không cần biến `BOOKFLOW_TEST_DB_*` hoặc PostgreSQL local.

Xem [Thiết lập Spring Boot local](../../docs/setup/spring-boot-local.md), [Quản lý migration với Flyway](../../docs/setup/flyway.md), [Integration test với Testcontainers](../../docs/setup/testcontainers.md) và [Chuẩn lỗi API](../../docs/standards/api-errors.md).
