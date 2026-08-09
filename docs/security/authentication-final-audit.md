# Authentication final audit — BF-022

BF-022 rà soát implementation BF-013 đến BF-021, không mở rộng API. Kết quả được chứng minh bằng unit test và PostgreSQL/Redis Testcontainers trong Maven `verify`.

## Invariant đã rà soát

- Password chỉ lưu dưới dạng Argon2id; refresh/reset token chỉ lưu SHA-256 hash, raw token không xuất hiện trong response hoặc log.
- JWT access token dùng RS256 và RFC 9068 `typ=at+jwt`; decoder kiểm tra chữ ký, thời hạn, issuer và audience.
- Refresh rotation/reuse, logout/logout-all, password reset và các race condition dùng PostgreSQL transaction/locking làm lớp bảo vệ cuối.
- Mọi mutation authentication ngoài registration đều qua CSRF filter hiện tại.
- Redis limiter chỉ làm admission control. Lua script cập nhật counter/TTL atomically; key chỉ chứa identifier đã hash; forwarded IP chỉ được dùng từ proxy tin cậy.
- Spring MVC response-body logging được giữ ở mức `INFO` để chế độ debug chung không ghi access/CSRF token từ response body.
- Flyway V1/V2/V3 là append-only. V3 tạo `password_reset_tokens` với FK, unique hash, status/metadata checks và index phục vụ lookup/cleanup.

## Chiến lược lỗi

Forgot-password giữ response `202` chung cả khi account không tồn tại hoặc notification adapter lỗi. Reset token sai, hết hạn, đã dùng hay revoked trả cùng error công khai. Rate limiter mặc định fail-closed: Redis không khả dụng trả lỗi dịch vụ, nhưng không thay đổi hoặc xác nhận tính hợp lệ của session/token.

## Phạm vi còn lại

Email provider, MFA, authorization theo tenant và observability production không thuộc chuỗi ticket này. Trước production cần cấu hình RSA key, Redis credentials, trusted proxy và notification adapter qua secret manager/hạ tầng ngoài repository.
