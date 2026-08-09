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

Multi-tenancy dùng shared database/shared schema; business là tenant và mọi dữ liệu nghiệp vụ tenant-owned sẽ được cách ly bằng `tenant_id`. Các tài liệu cũ dùng `business_id` để chỉ cùng ranh giới business.

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

Dự án đã có nền xác thực và schema tenant: đăng ký, JWT RS256, session/refresh rotation, khôi phục mật khẩu, Redis rate limiting, API tạo/xem/cập nhật cấu hình business với OWNER membership đầu tiên và tenant authorization runtime. PostgreSQL vẫn là nguồn sự thật; quản lý membership và schema booking chưa được triển khai.

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
BF-011: Completed — design only, chưa triển khai multi-tenancy runtime
BF-013: Completed — authentication database schema
BF-014: Completed — user registration và Argon2id password hashing
BF-015: Completed — login, JWT RS256 và authentication session
BF-017/018/019: Completed — refresh rotation, reuse detection và logout
BF-020: Completed — forgot/reset password
BF-021: Completed — Redis rate limiting và security hardening
BF-022: Completed — authentication final audit
BF-023: Completed — business và business membership database schema
BF-024: Completed — Create Business API và owner membership
BF-025: Completed — xem business của user hiện tại
BF-026: Completed — tenant authorization dùng chung
BF-027: Completed — role permission matrix
BF-028: Completed — tenant security review và tài liệu
BF-029: Completed — cập nhật cấu hình business
```

## CI GitHub Actions

Workflow CI tại [.github/workflows/ci.yml](.github/workflows/ci.yml) chạy kiểm tra backend, frontend và repository trên mỗi push vào `main` và mỗi pull request. Nội dung kiểm tra bao gồm:

- Backend: Maven `test` và `verify` (bao gồm integration test Flyway/Testcontainers).
- Frontend: `npm ci`, ESLint, TypeScript typecheck, Vitest và production build.
- Repository: script kiểm tra môi trường `scripts/check-prerequisites.sh` và `git diff --check`.

Các lệnh local tương đương:

```powershell
.\apps\api\mvnw.cmd test
.\apps\api\mvnw.cmd verify
npm --prefix .\apps\web ci
npm --prefix .\apps\web run lint
npm --prefix .\apps\web run typecheck
npm --prefix .\apps\web run test -- --maxWorkers=1 --minWorkers=1
npm --prefix .\apps\web run build
bash ./scripts/check-prerequisites.sh
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

Khi backend đang chạy, OpenAPI JSON có tại `http://127.0.0.1:8080/v3/api-docs` và Swagger UI có tại `http://127.0.0.1:8080/swagger-ui/index.html`. API xem và cập nhật cấu hình business dùng tenant authorization và permission matrix PostgreSQL; API quản lý membership khác chưa được triển khai.

Xem [hướng dẫn Spring Boot local](docs/setup/spring-boot-local.md), [hướng dẫn Flyway](docs/setup/flyway.md), [schema business/membership](docs/setup/business-membership-schema.md), [API tạo business](docs/setup/business-creation-api.md), [API xem business](docs/setup/business-query-api.md), [API cập nhật cấu hình](docs/setup/business-configuration-api.md), [tenant authorization](docs/setup/tenant-authorization.md), [hướng dẫn Testcontainers](docs/setup/testcontainers.md), [chuẩn lỗi API](docs/standards/api-errors.md), [ADR authentication](docs/adr/0001-authentication-and-refresh-token.md), [ADR multi-tenancy](docs/adr/0002-multi-tenancy-and-membership.md) và [README của backend](apps/api/README.md). Backend chưa có JPA, tenant context switch, API quản lý membership hoặc booking API.

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

## Authentication hiện có

Các endpoint authentication hiện có gồm đăng ký, đăng nhập, refresh, logout/logout-all và forgot/reset password. Mọi mutation ngoài đăng ký đều giữ CSRF; access token dùng JWT RS256, raw refresh/reset token không được lưu trong PostgreSQL. Redis chỉ giới hạn lưu lượng, không quyết định tính hợp lệ của credential hoặc session. Xem [authentication local](docs/setup/authentication-local.md) để cấu hình RSA PEM, Redis và chính sách local.
