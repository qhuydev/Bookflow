# BookFlow API

Backend tối thiểu dùng Java 21, Spring Boot 4.1.0 và Maven Wrapper. BF-004 chưa tích hợp PostgreSQL hoặc Redis.

```powershell
.\mvnw.cmd clean verify
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Health URL: `http://127.0.0.1:8080/actuator/health`.

Xem hướng dẫn đầy đủ tại [Thiết lập Spring Boot local](../../docs/setup/spring-boot-local.md).
