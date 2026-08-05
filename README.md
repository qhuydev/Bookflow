# BookFlow

BookFlow là nền tảng SaaS quản lý đặt lịch cho salon, spa, phòng khám nhỏ, trung tâm gia sư và studio. Dự án giúp doanh nghiệp tổ chức chi nhánh, nhân viên, dịch vụ, lịch làm việc và booking; khách hàng có thể tìm khung giờ phù hợp và đặt lịch tin cậy.

## Đối tượng sử dụng và vai trò

- **System Admin**: quản trị nền tảng, theo dõi và hỗ trợ vận hành ở cấp hệ thống.
- **Business Owner**: quản lý doanh nghiệp, chi nhánh, nhân sự, dịch vụ và lịch hẹn của tenant mình.
- **Staff**: xem và xử lý lịch làm việc, booking được phân quyền.
- **Customer**: tìm lịch trống, tạo và quản lý booking của bản thân.
- **Guest**: người chưa đăng nhập, có thể khám phá thông tin công khai theo chính sách sau này.

## Mục tiêu học tập

Xây dựng một sản phẩm mẫu có kỷ luật kỹ thuật để thực hành Java 21, Spring Boot, Next.js, bảo mật, thiết kế modular monolith, multi-tenancy, kiểm thử tích hợp và xử lý cạnh tranh khi đặt lịch.

## Phạm vi MVP

- Authentication và refresh token.
- Quản lý doanh nghiệp và chi nhánh.
- Quản lý nhân viên và dịch vụ.
- Thiết lập lịch làm việc và ngày nghỉ.
- Tìm khung giờ còn trống.
- Tạo, xác nhận, hủy và hoàn thành booking.
- Phân quyền.
- Cách ly dữ liệu giữa các tenant.
- Kiểm thử hai request cùng đặt một khung giờ.

## Không thuộc MVP

- Microservices hoàn chỉnh.
- Kafka.
- Kubernetes.
- Mobile application.
- AI recommendation.
- Hệ thống kế toán đầy đủ.
- Tích hợp nhiều cổng thanh toán cùng lúc.

## Công nghệ dự kiến

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA.
- Frontend: Next.js, TypeScript; BF-005 dùng CSS và CSS Modules, chưa dùng UI framework.
- Dữ liệu: PostgreSQL; Redis cho cache và giữ chỗ tạm thời.
- Hạ tầng: Docker Compose, GitHub Actions, Nginx.

Kiến trúc chọn là **modular monolith**: các miền nghiệp vụ được tách module rõ ràng nhưng vẫn triển khai cùng một ứng dụng. Cách này giảm độ phức tạp vận hành ban đầu, giữ giao dịch booking nhất quán và vẫn tạo ranh giới tốt để có thể tách dịch vụ khi có lý do thực tế.

Multi-tenancy dùng shared database/shared schema; dữ liệu tenant được cách ly bằng `business_id`.

## Cấu trúc repository

```text
apps/       # Ứng dụng backend và frontend (chưa khởi tạo ở BF-001)
docs/       # Backlog, tiêu chuẩn, ADR và hướng dẫn thiết lập
infra/      # Hạ tầng trong các ticket sau
scripts/    # Script kiểm tra môi trường phát triển
tests/      # Tài nguyên kiểm thử dùng chung trong tương lai
.github/    # Template issue và pull request
```

## Trạng thái hiện tại

Dự án đang ở giai đoạn khởi tạo. Đã có PostgreSQL, Redis local, nền backend Spring Boot, nền frontend Next.js, Flyway baseline, PostgreSQL integration test bằng Testcontainers, error contract API dựa trên `ProblemDetail`, OpenAPI JSON và Swagger UI. Thiết kế authentication/refresh token đã được chốt bằng ADR; authentication runtime, schema nghiệp vụ, API nghiệp vụ và Redis integration vào backend chưa được triển khai.

```text
BF-001: Completed
BF-002: Completed
BF-003: Completed
BF-004: Completed
BF-005: In progress — production audit đã sạch sau overrides, nhưng cây dependency còn lỗi `npm ls` cần xử lý
BF-006: Completed
BF-007: Completed
BF-008: Completed
BF-009: Completed
BF-010: Completed — design only, chưa triển khai authentication runtime
```

## Infrastructure local quick start

Sao chép `.env.example` thành `.env`, thay password local mạnh (không commit file này), rồi chạy:

```powershell
.\scripts\postgres.ps1 up
.\scripts\redis.ps1 up
.\scripts\postgres.ps1 ready
.\scripts\redis.ps1 ready
```

Hoặc khởi động toàn bộ stack:

```powershell
docker compose up -d
docker compose ps
```

Xem hướng dẫn tại [PostgreSQL local bằng Docker Compose](docs/setup/postgresql-docker.md) và [Redis local bằng Docker Compose](docs/setup/redis-docker.md).

## Backend quick start

```powershell
.\apps\api\mvnw.cmd clean test    # Unit và application-context test, không chạy container
.\apps\api\mvnw.cmd clean verify  # Bao gồm PostgreSQL Testcontainer integration test

$env:SPRING_PROFILES_ACTIVE = "local"
.\apps\api\mvnw.cmd spring-boot:run
```

Health URL: `http://127.0.0.1:8080/actuator/health`.

Khi backend đang chạy, OpenAPI JSON có tại `http://127.0.0.1:8080/v3/api-docs` và Swagger UI có tại `http://127.0.0.1:8080/swagger-ui/index.html`. Tài liệu hiện chưa có API nghiệp vụ nên object `paths` có thể rỗng.

Xem [hướng dẫn Spring Boot local](docs/setup/spring-boot-local.md), [hướng dẫn Flyway](docs/setup/flyway.md), [hướng dẫn Testcontainers](docs/setup/testcontainers.md), [chuẩn lỗi API](docs/standards/api-errors.md), [ADR authentication và refresh token](docs/adr/0001-authentication-and-refresh-token.md) và [README của backend](apps/api/README.md). Backend chưa có JPA, Redis integration, authentication runtime hoặc booking API.

## Frontend quick start

```powershell
npm --prefix .\apps\web ci
npm --prefix .\apps\web run dev
```

Frontend URL: `http://127.0.0.1:3000`.

Chạy toàn bộ kiểm tra frontend:

```powershell
npm --prefix .\apps\web run verify
```

Xem [hướng dẫn Next.js local](docs/setup/nextjs-local.md) và [README của frontend](apps/web/README.md). BF-005 chưa có authentication, booking interface, dashboard, API integration hoặc CORS integration.

## Kiểm tra môi trường

PowerShell trên Windows:

```powershell
./scripts/check-prerequisites.ps1
```

Bash qua Git Bash hoặc WSL:

```bash
bash scripts/check-prerequisites.sh
```

Script chỉ kiểm tra, không tự cài bất kỳ công cụ nào. Java/JDK 21, Git, Node.js/npm, Docker và Docker Compose là các điều kiện bắt buộc dự kiến; VS Code CLI là tùy chọn.

## Bảo mật

Không đưa secret, khóa API, mật khẩu, token hay file `.env` vào source code. Dùng biến môi trường và chỉ commit các file mẫu an toàn như `.env.example` khi cần.
