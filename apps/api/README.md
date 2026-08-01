# BookFlow API

Backend dùng Java 21, Spring Boot 4.1.0 và Maven Wrapper. BF-006 đã kết nối profile local với PostgreSQL và quản lý migration bằng Flyway; chưa có schema nghiệp vụ hoặc Redis integration.

```powershell
.\mvnw.cmd clean verify
$env:BOOKFLOW_DB_URL = "jdbc:postgresql://127.0.0.1:5433/bookflow"
$env:BOOKFLOW_DB_USERNAME = "bookflow"
$env:BOOKFLOW_DB_PASSWORD = "<lấy từ .env local>"
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Health URL: `http://127.0.0.1:8080/actuator/health`.

Chạy Flyway integration test với ba biến `BOOKFLOW_TEST_DB_URL`, `BOOKFLOW_TEST_DB_USERNAME`, `BOOKFLOW_TEST_DB_PASSWORD` đã được cấu hình:

```powershell
.\mvnw.cmd clean verify -Pflyway-it
```

Xem [Thiết lập Spring Boot local](../../docs/setup/spring-boot-local.md) và [Quản lý migration với Flyway](../../docs/setup/flyway.md).
