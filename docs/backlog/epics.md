# Epic khởi đầu

## 1. Project Foundation
- Mục tiêu: tạo nền tảng repository và quy ước làm việc.
- Phạm vi chính: cấu trúc monorepo, tài liệu, script môi trường, Git và CI nền.
- Kết quả mong đợi: đội ngũ có điểm khởi đầu nhất quán.
- Phụ thuộc quan trọng: không có.

## 2. Requirements and Architecture
- Mục tiêu: làm rõ yêu cầu và các quyết định kiến trúc.
- Phạm vi chính: use case, mô hình miền, ADR, rủi ro bảo mật và concurrency.
- Kết quả mong đợi: backlog có thể triển khai và kiến trúc có cơ sở.
- Phụ thuộc quan trọng: Epic 1.

## 3. Backend Foundation
- Mục tiêu: tạo nền tảng Spring Boot có thể mở rộng.
- Phạm vi chính: bootstrap, cấu hình, error handling, OpenAPI và test base.
- Kết quả mong đợi: backend chạy được với chuẩn chung.
- Phụ thuộc quan trọng: Epic 1, 2.

## 4. Frontend Foundation
- Mục tiêu: tạo nền tảng Next.js nhất quán.
- Phạm vi chính: bootstrap, TypeScript, Tailwind, cấu trúc UI và API client.
- Kết quả mong đợi: frontend có thể phát triển theo module.
- Phụ thuộc quan trọng: Epic 1, 2.

## 5. Authentication and Security
- Mục tiêu: xác thực an toàn cho người dùng.
- Phạm vi chính: đăng nhập, refresh token, mật khẩu, session/token và security baseline.
- Kết quả mong đợi: luồng xác thực kiểm thử được.
- Phụ thuộc quan trọng: Epic 3.

## 6. Multi-tenancy and Authorization
- Mục tiêu: cô lập dữ liệu tenant và phân quyền.
- Phạm vi chính: `business_id`, policy truy cập, role và kiểm thử cách ly.
- Kết quả mong đợi: không truy cập chéo dữ liệu doanh nghiệp.
- Phụ thuộc quan trọng: Epic 3, 5.

## 7. Business Management
- Mục tiêu: quản lý doanh nghiệp và chi nhánh.
- Phạm vi chính: CRUD doanh nghiệp, chi nhánh và quyền owner.
- Kết quả mong đợi: tenant có cấu trúc vận hành cơ bản.
- Phụ thuộc quan trọng: Epic 6.

## 8. Employee and Service Management
- Mục tiêu: quản lý nhân viên và dịch vụ.
- Phạm vi chính: hồ sơ nhân viên, năng lực/dịch vụ và phân công chi nhánh.
- Kết quả mong đợi: có dữ liệu để tính lịch trống.
- Phụ thuộc quan trọng: Epic 7.

## 9. Scheduling and Availability
- Mục tiêu: biểu diễn lịch làm việc và khung giờ trống.
- Phạm vi chính: lịch định kỳ, ngày nghỉ, timezone và thuật toán availability.
- Kết quả mong đợi: trả về khung giờ đặt được chính xác.
- Phụ thuộc quan trọng: Epic 8.

## 10. Booking and Concurrency
- Mục tiêu: quản lý vòng đời booking không đặt trùng.
- Phạm vi chính: tạo, xác nhận, hủy, hoàn thành, transaction và kiểm thử cạnh tranh.
- Kết quả mong đợi: booking nhất quán khi có request đồng thời.
- Phụ thuộc quan trọng: Epic 9.

## 11. Payment and Webhook
- Mục tiêu: hỗ trợ thanh toán và xử lý webhook an toàn.
- Phạm vi chính: trạng thái thanh toán, idempotency và xác minh webhook.
- Kết quả mong đợi: tích hợp một luồng thanh toán có kiểm soát.
- Phụ thuộc quan trọng: Epic 10.

## 12. Notification, Outbox and Audit
- Mục tiêu: gửi thông báo tin cậy và lưu vết nghiệp vụ.
- Phạm vi chính: outbox, notification, audit log và retry.
- Kết quả mong đợi: thao tác quan trọng có thể truy vết.
- Phụ thuộc quan trọng: Epic 10, 11.

## 13. Reporting and Performance
- Mục tiêu: cung cấp báo cáo và tối ưu các luồng chính.
- Phạm vi chính: chỉ số, truy vấn báo cáo, index và profiling.
- Kết quả mong đợi: dữ liệu vận hành hữu ích với hiệu năng phù hợp.
- Phụ thuộc quan trọng: Epic 7 đến 12.

## 14. Production Deployment and Operations
- Mục tiêu: triển khai và vận hành an toàn.
- Phạm vi chính: Docker, Nginx, CI/CD, cấu hình môi trường, quan sát hệ thống và runbook.
- Kết quả mong đợi: quy trình triển khai lặp lại được.
- Phụ thuộc quan trọng: Epic 3, 4, 12, 13.
