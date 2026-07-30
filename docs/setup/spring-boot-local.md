# Chạy Spring Boot local

## BF-004 làm gì?

BF-004 tạo nền backend tối thiểu bằng Java 21 và Spring Boot 4.1.0. Spring Boot quản lý application context và embedded web server; Maven Wrapper giúp các máy dùng cùng Maven version; Actuator health cho biết ứng dụng đã sẵn sàng. Chưa có nghiệp vụ BookFlow trong ticket này.

## Cấu trúc project

- `pom.xml`: metadata, dependency và cấu hình build Maven.
- `mvnw`, `mvnw.cmd`: Maven Wrapper cho Unix và Windows.
- `src/main/java`: source production; main class nằm tại package gốc `com.bookflow`.
- `src/main/resources`: cấu hình ứng dụng và profile local.
- `src/test/java`: unit test và application context test.
- `target`: build output, luôn bị Git ignore.

Feature module tương lai tuân theo `com.bookflow.<module>.api`, `application`, `domain`, `infrastructure`. Controller không chứa business logic; transaction bắt đầu tại application service; module không truy cập repository nội bộ của module khác.

## Kiểm tra Java và Maven Wrapper

```powershell
java -version
.\apps\api\mvnw.cmd --version
```

Cả hai phải cho thấy Java 21.

## Build và test

Từ root repository:

```powershell
.\apps\api\mvnw.cmd clean verify
```

Từ `apps/api`:

```powershell
.\mvnw.cmd clean verify
```

Bash:

```bash
./apps/api/mvnw clean verify
```

## Chạy local

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\apps\api\mvnw.cmd spring-boot:run
```

Dừng bằng `Ctrl+C`, rồi có thể xóa biến khỏi shell:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
```

Chạy executable JAR:

```powershell
.\apps\api\mvnw.cmd clean package
java -jar .\apps\api\target\bookflow-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## Kiểm tra health

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
curl.exe http://127.0.0.1:8080/actuator/health
```

Đổi port local mà không sửa source:

```powershell
$env:BOOKFLOW_API_PORT = "8081"
$env:SPRING_PROFILES_ACTIVE = "local"
.\apps\api\mvnw.cmd spring-boot:run
```

Không kill tiến trình khác đang chiếm port.

## Quan hệ với PostgreSQL và Redis

PostgreSQL và Redis tiếp tục chạy qua Docker Compose, nhưng BF-004 chưa kết nối backend đến hai service này. Build và context test không cần Docker. Flyway dự kiến ở BF-006, Testcontainers ở BF-007 và Redis integration ở ticket sau. Khi database integration được triển khai, PostgreSQL vẫn là nguồn dữ liệu nghiệp vụ chính.

## Troubleshooting

- Không có `java` hoặc Java không phải 21: cài/chọn JDK 21 theo chính sách máy; không sửa cấu hình hệ thống ngoài phạm vi repository.
- `JAVA_HOME` sai: kiểm tra `java -version` và Maven Wrapper đang dùng runtime nào.
- Wrapper hoặc Maven Central không tải được: kiểm tra mạng/proxy tin cậy; không tắt SSL hay xóa toàn bộ cache `.m2` như bước đầu tiên.
- Port 8080 bị chiếm: dùng `BOOKFLOW_API_PORT=8081` cho lần chạy đó, không kill tiến trình khác.
- Context test lỗi: đọc nguyên nhân đầu tiên trong stack trace; không skip test.
- Health trả connection refused: xác nhận app đã chạy, profile local đã bật và đúng port.
- PowerShell execution policy không áp dụng cho file `.cmd`.
- Bash/WSL trả `E_ACCESSDENIED`: dùng `mvnw.cmd` trên Windows và ghi nhận giới hạn môi trường.
