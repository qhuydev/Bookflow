# Authentication local (BF-015 đến BF-022)

BookFlow hiện có đăng ký, đăng nhập, JWT RS256, refresh rotation, logout, khôi phục mật khẩu và Redis rate limiting. PostgreSQL là nguồn sự thật cho user/session/token; Redis chỉ là lớp giới hạn lưu lượng. Authorization nghiệp vụ ngoài authentication vẫn chưa được triển khai.

## RSA PEM local

Profile `local` yêu cầu các biến sau và sẽ fail fast khi thiếu hoặc PEM không hợp lệ:

```powershell
$env:BOOKFLOW_AUTH_KEY_ID = "bookflow-local-2026"
$env:BOOKFLOW_AUTH_PRIVATE_KEY_LOCATION = "file:C:/duong-dan-rieng/bookflow-private.pem"
$env:BOOKFLOW_AUTH_PUBLIC_KEY_LOCATION = "file:C:/duong-dan-rieng/bookflow-public.pem"
```

Tạo cặp RSA riêng cho máy local bằng công cụ quản lý key phù hợp, lưu ngoài repository và cấu hình đường dẫn trong `.env` local hoặc environment. Không commit private/public key, `.env` hoặc log nội dung key.

## CSRF và login

Gọi `GET /api/v1/auth/csrf`, giữ cookie `XSRF-TOKEN` Spring Security cấp và gửi token lại bằng header `X-XSRF-TOKEN` khi gọi `POST /api/v1/auth/login`. Login thành công trả access token JSON; refresh token chỉ có trong cookie `HttpOnly`, `SameSite=Lax`, path `/api/v1/auth`.

`POST /api/v1/auth/refresh` xoay token trong transaction PostgreSQL. Token cũ chỉ dùng được một lần; reuse sẽ revoke family. `POST /api/v1/auth/logout` thu hồi session hiện tại, còn `logout-all` thu hồi toàn bộ session của user. Cả ba endpoint đều yêu cầu CSRF và xóa cookie đúng thuộc tính khi logout.

## Forgot/reset password

`POST /api/v1/auth/forgot-password` luôn trả `202`, bất kể email có tồn tại. Với user hợp lệ, ứng dụng sinh token ngẫu nhiên, chỉ lưu SHA-256 hash và chuyển raw token cho `PasswordResetNotificationPort` sau khi transaction commit. Adapter mặc định hiện là no-op vì dự án chưa tích hợp email provider; không có endpoint hay log nào trả raw token.

`POST /api/v1/auth/reset-password` nhận raw token và mật khẩu mới, áp dụng password policy/Argon2id rồi consume token một lần. Thành công sẽ revoke reset token còn lại, refresh token và session của đúng user trong cùng transaction. TTL mặc định là 30 phút, cấu hình bằng `BOOKFLOW_AUTH_PASSWORD_RESET_TTL_MINUTES`. Cả hai endpoint đều yêu cầu cookie/header CSRF.

## Redis rate limiting

Profile local lấy Redis từ `BOOKFLOW_REDIS_HOST`, `REDIS_PORT` và `REDIS_PASSWORD`. Counter theo IP và account được tăng bằng một Lua script cùng TTL; key có namespace `bookflow:auth:rate-limit:v1` và chỉ chứa SHA-256 identifier. Mặc định mỗi action cho phép 5 request/account và 20 request/IP trong 60 giây; vượt ngưỡng trả `429` với `Retry-After`.

BookFlow chọn **fail-closed** (`BOOKFLOW_AUTH_RATE_LIMIT_FAIL_OPEN=false`): Redis lỗi thì authentication mutation trả `503`, còn PostgreSQL vẫn là nguồn quyết định token/session hợp lệ. Không tin `X-Forwarded-For` mặc định. Chỉ đặt `BOOKFLOW_AUTH_RATE_LIMIT_TRUSTED_PROXIES` thành danh sách IP reverse proxy do đội vận hành kiểm soát; nếu không dùng proxy hãy để trống.

Các giá trị local có thể đặt trong `.env` (file bị Git ignore). Không dùng cấu hình/mật khẩu mẫu cho staging hoặc production.

## Kiểm thử

Từ `apps/api`, chạy `./mvnw.cmd clean verify`. PostgreSQL Testcontainers kiểm tra migration, transaction, locking và concurrency; Redis Testcontainers kiểm tra atomic counter, TTL, key privacy và fail-closed. Test không dùng PostgreSQL/Redis Compose làm dữ liệu kiểm thử.
