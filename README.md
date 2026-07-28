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
- Frontend: Next.js, TypeScript, Tailwind CSS.
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

Dự án đang ở giai đoạn **khởi tạo**. BF-001 chỉ tạo bộ khung và quy ước; chưa có Spring Boot, Next.js, PostgreSQL, Redis hay Docker Compose.

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
