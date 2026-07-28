# Hướng dẫn làm việc cho Codex

## Quy tắc bắt buộc

- Luôn đọc `README.md`, `AGENTS.md` và tài liệu liên quan trước khi sửa code.
- Chỉ triển khai đúng ticket được giao; không tự mở rộng phạm vi.
- Không thay đổi API hoặc database schema ngoài yêu cầu của ticket.
- Không được bỏ qua tenant filtering. Mọi tài nguyên thuộc doanh nghiệp phải được kiểm tra `business_id`.
- PostgreSQL là source of truth cho tính nhất quán booking. Redis không phải lớp bảo vệ cuối cùng chống đặt trùng.
- Không đưa secret vào repository.
- Mọi thay đổi database phải dùng Flyway migration.
- Phải viết hoặc cập nhật test khi thay đổi nghiệp vụ.
- Phải chạy các kiểm tra phù hợp trước khi kết thúc.
- Không commit, push hoặc tạo pull request nếu người dùng chưa yêu cầu.
- Không tuyên bố test đạt nếu chưa thực sự chạy.

## Kiến trúc backend dự kiến

```text
authentication
users
businesses
branches
employees
services
schedules
bookings
payments
notifications
reviews
reports
audit
```

Mỗi module cần phân tách hợp lý giữa controller, application/service, domain, repository, DTO, entity, validation và test.

## Báo cáo khi kết thúc

Luôn nêu rõ:

- File đã thay đổi.
- Quyết định kỹ thuật.
- Lệnh kiểm tra đã chạy.
- Kết quả kiểm tra.
- Rủi ro hoặc việc còn lại.
