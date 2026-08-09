# BOOKFLOW — TỔNG KẾT GIAI ĐOẠN 2: AUTHENTICATION

## 1. Mục tiêu của giai đoạn

- Xây dựng một hệ thống xác thực hoàn chỉnh cho BookFlow, thay vì chỉ dừng ở chức năng đăng nhập cơ bản.
- Bảo vệ tài khoản người dùng trước các rủi ro phổ biến:
  - Đăng ký trùng tài khoản.
  - Dò mật khẩu và credential stuffing.
  - Đánh cắp hoặc sử dụng lại refresh token.
  - Dùng đồng thời một token nhiều lần.
  - Dò xem email nào đã tồn tại trong hệ thống.
  - Chiếm quyền truy cập từ các phiên đăng nhập cũ sau khi đổi mật khẩu.
  - Tấn công CSRF vào các API làm thay đổi dữ liệu xác thực.
- Bảo đảm hệ thống hoạt động đúng khi có nhiều request đồng thời, lỗi giữa transaction hoặc Redis tạm thời không khả dụng.
- Xây dựng bộ kiểm thử đủ mạnh để chứng minh các cơ chế bảo mật hoạt động trên PostgreSQL và Redis thật.

---

## 2. Đăng ký tài khoản

- **Đã làm gì?**
  - Xây dựng luồng đăng ký người dùng mới.
  - Chuẩn hóa và kiểm tra dữ liệu đầu vào.
  - Mã hóa mật khẩu trước khi lưu vào cơ sở dữ liệu.
  - Kiểm soát email trùng lặp ở cả tầng ứng dụng và cơ sở dữ liệu.

- **Giải quyết vấn đề gì?**
  - Ngăn dữ liệu người dùng không hợp lệ đi vào hệ thống.
  - Không lưu mật khẩu dạng rõ trong PostgreSQL.
  - Tránh tạo hai tài khoản có cùng email khi hai request đăng ký đến gần như đồng thời.
  - Trả lỗi theo một contract thống nhất, không để lộ lỗi SQL hoặc thông tin nội bộ.

- **Làm như thế nào?**
  - Dùng Bean Validation để kiểm tra request.
  - Chuẩn hóa email trước khi tìm kiếm hoặc lưu trữ.
  - Dùng `PasswordEncoder` để tạo password hash.
  - Dùng unique constraint trong PostgreSQL làm lớp bảo vệ cuối cùng cho tính duy nhất của email.
  - Đặt các thao tác ghi dữ liệu trong transaction phù hợp.

---

## 3. Đăng nhập và access token

- **Đã làm gì?**
  - Xây dựng luồng đăng nhập bằng email và mật khẩu.
  - Phát hành access token theo chuẩn JWT để truy cập API được bảo vệ.
  - Cấu hình Spring Security Resource Server để xác minh token ở mỗi request.

- **Giải quyết vấn đề gì?**
  - Xác minh đúng danh tính người gọi API.
  - Không phải lưu access token dạng session trong bộ nhớ ứng dụng.
  - Ngăn token giả, token hết hạn hoặc token được phát hành bởi hệ thống khác truy cập BookFlow.

- **Làm như thế nào?**
  - Xác thực mật khẩu bằng `PasswordEncoder` hiện tại.
  - Ký access token bằng RS256.
  - Kiểm tra chữ ký, thời hạn, issuer và audience.
  - Tuân thủ JWT access-token profile RFC 9068.
  - Bắt buộc header `typ=at+jwt`; token thiếu `typ` hoặc dùng `typ=JWT` bị từ chối.
  - Ánh xạ định danh người dùng từ claim `sub` vào `Authentication.getName()`.
  - Dùng public error response chung cho đăng nhập thất bại, tránh tiết lộ email không tồn tại hay mật khẩu sai.

---

## 4. Session và refresh-token rotation

- **Đã làm gì?**
  - Xây dựng session đăng nhập và refresh token để cấp lại access token.
  - Triển khai refresh-token rotation: mỗi lần refresh thành công, token cũ bị thay thế bằng token mới.
  - Triển khai phát hiện refresh token cũ bị sử dụng lại.

- **Giải quyết vấn đề gì?**
  - Access token có thể đặt thời gian sống ngắn mà người dùng vẫn duy trì được phiên đăng nhập.
  - Nếu refresh token bị đánh cắp, việc sử dụng lại token đã rotate có thể được phát hiện.
  - Hạn chế kẻ tấn công duy trì quyền truy cập lâu dài bằng token cũ.
  - Ngăn hai request refresh đồng thời cùng sử dụng thành công một token.

- **Làm như thế nào?**
  - Chỉ lưu SHA-256 hash của refresh token trong PostgreSQL, không lưu raw token.
  - Quản lý token theo session và token family.
  - Khi refresh thành công:
    - Xác minh token và trạng thái session.
    - Thu hồi token hiện tại.
    - Sinh token kế tiếp.
    - Trả access token mới theo contract.
  - Khi phát hiện reuse:
    - Xác định token family/session liên quan.
    - Thu hồi phạm vi phiên bị ảnh hưởng theo policy.
  - Dùng transaction và cơ chế khóa của PostgreSQL để bảo đảm chỉ một request đồng thời thành công.

---

## 5. Logout và logout-all

- **Đã làm gì?**
  - Xây dựng chức năng đăng xuất phiên hiện tại.
  - Xây dựng chức năng đăng xuất khỏi tất cả thiết bị.

- **Giải quyết vấn đề gì?**
  - Người dùng có thể chủ động chấm dứt một phiên không còn sử dụng.
  - Khi nghi ngờ tài khoản bị lộ, người dùng có thể thu hồi toàn bộ phiên đăng nhập.
  - Việc đăng xuất của một người không được ảnh hưởng đến người dùng khác.

- **Làm như thế nào?**
  - Logout chỉ thu hồi đúng session/refresh-token family hiện tại.
  - Logout-all thu hồi mọi session thuộc đúng user đang xác thực.
  - Các thao tác thu hồi được kiểm soát bằng transaction.
  - Test kiểm tra session isolation để chứng minh session của user khác không bị thay đổi.

---

## 6. Chống tấn công CSRF

- **Đã làm gì?**
  - Áp dụng CSRF protection cho các endpoint xác thực làm thay đổi trạng thái.
  - Audit toàn bộ registration, login, refresh, logout, logout-all, forgot-password và reset-password.

- **Giải quyết vấn đề gì?**
  - Ngăn website độc hại lợi dụng trình duyệt của người dùng để gửi request xác thực ngoài ý muốn.
  - Ngăn việc một endpoint public hoặc Bearer-authenticated vô tình được miễn CSRF quá rộng.

- **Làm như thế nào?**
  - Giữ `CsrfFilter`, không tắt CSRF toàn cục.
  - Request thiếu hoặc sai CSRF token bị từ chối với HTTP `403`.
  - Chỉ request có CSRF hợp lệ mới đi tiếp tới business logic.
  - Kiểm thử riêng từng mutation endpoint và rà soát thứ tự giữa CSRF, rate limiter và authentication filters.

---

## 7. Quên mật khẩu và đặt lại mật khẩu

- **Đã làm gì?**
  - Xây dựng luồng forgot-password và reset-password.
  - Tạo migration Flyway V3 cho bảng `password_reset_tokens`.
  - Tạo notification port để tách logic sinh token khỏi nhà cung cấp email.

- **Giải quyết vấn đề gì?**
  - Cho phép người dùng lấy lại quyền truy cập khi quên mật khẩu.
  - Ngăn attacker dò email đã đăng ký thông qua response của forgot-password.
  - Ngăn reset token bị lộ từ database hoặc log.
  - Ngăn một token được dùng nhiều lần hoặc hai request đồng thời cùng reset thành công.
  - Ngăn thiết bị cũ tiếp tục hoạt động sau khi mật khẩu đã bị thay đổi.

- **Làm như thế nào?**
  - Forgot-password trả cùng status và public response cho email tồn tại và không tồn tại.
  - Sinh raw token bằng `SecureRandom` với entropy đủ mạnh.
  - Raw token chỉ được chuyển qua notification boundary.
  - Chỉ lưu SHA-256 hash của token trong PostgreSQL.
  - Token có TTL, trạng thái đã dùng/vô hiệu hóa và chỉ được sử dụng một lần.
  - Token sai, hết hạn, đã dùng hoặc bị revoke trả cùng public error contract.
  - Migration V3 bổ sung:
    - Foreign key tới user.
    - Unique constraint cho token hash.
    - Metadata constraint và các index cần thiết.
    - Thời điểm tạo, hết hạn, sử dụng và vô hiệu hóa.
  - Reset-password chạy trong một transaction gồm:
    - Khóa reset-token record bằng `SELECT ... FOR UPDATE`.
    - Cập nhật password hash.
    - Consume token đang dùng.
    - Revoke các reset token còn lại.
    - Revoke toàn bộ session và refresh token của đúng user.
  - Nếu một bước lỗi, toàn bộ transaction rollback; password, token và session không bị cập nhật dở dang.

---

## 8. Rate limiting và chống brute-force

- **Đã làm gì?**
  - Áp dụng rate limiting cho các authentication endpoint nhạy cảm.
  - Tạo bộ giới hạn theo IP và account identifier.
  - Chuẩn hóa response khi vượt giới hạn.

- **Giải quyết vấn đề gì?**
  - Hạn chế brute-force mật khẩu.
  - Hạn chế credential stuffing và dò refresh/reset token.
  - Ngăn một nguồn gửi số lượng lớn request làm quá tải authentication service.
  - Không để request của một user vô tình chặn user khác.

- **Làm như thế nào?**
  - Dùng Redis làm nơi lưu counter ngắn hạn.
  - Dùng Lua script để thực hiện `INCR + PEXPIRE` atomically.
  - Mỗi bucket có TTL và policy cấu hình được.
  - Redis key có namespace rõ ràng.
  - Email/account identifier được chuẩn hóa rồi SHA-256; key không chứa PII, password hoặc token dạng raw.
  - Phân tách bucket theo IP/account phù hợp với từng endpoint.
  - Mặc định lấy remote address và không tin `X-Forwarded-For` do client tự gửi.
  - Khi vượt giới hạn, trả HTTP `429 Too Many Requests` và `Retry-After`.
  - Khi Redis không khả dụng, hệ thống áp dụng policy fail-closed và trả `503`, thay vì âm thầm bỏ qua lớp bảo vệ.
  - Rate limiter chỉ quyết định request có được đi tiếp; PostgreSQL vẫn là nguồn quyết định token/session có hợp lệ hay không.

---

## 9. Transaction và xử lý concurrency

- **Đã làm gì?**
  - Kiểm tra các luồng có nguy cơ race condition: đăng ký, refresh token, reset password và thu hồi session.
  - Bổ sung transaction, database constraint và row locking tại nơi cần thiết.

- **Giải quyết vấn đề gì?**
  - Tránh kiểu xử lý `check rồi update` không khóa, khiến hai request cùng nhìn thấy trạng thái hợp lệ.
  - Ngăn dữ liệu rơi vào trạng thái cập nhật một nửa khi có lỗi giữa quy trình.
  - Giữ đúng invariant của token ngay cả khi nhiều request đến đồng thời.

- **Làm như thế nào?**
  - Dùng PostgreSQL làm source of truth.
  - Dùng unique constraint cho các invariant có thể bảo vệ ở database.
  - Dùng `SELECT ... FOR UPDATE` hoặc khóa tương đương tại luồng consume token.
  - Gom các thay đổi liên quan vào cùng transaction.
  - Tạo test chủ động gây lỗi giữa transaction để chứng minh rollback đầy đủ.
  - Tạo test concurrent refresh/reset để chứng minh chỉ một request thành công.

---

## 10. Bảo vệ dữ liệu nhạy cảm và error contract

- **Đã làm gì?**
  - Audit source code, cấu hình, migration, response và test reports.
  - Chuẩn hóa lỗi xác thực theo HTTP status và public error contract.
  - Khắc phục việc Spring MVC DEBUG có thể ghi access/CSRF token từ response body.

- **Giải quyết vấn đề gì?**
  - Ngăn password, token, cookie, secret hoặc private key xuất hiện trong log và repository.
  - Không tiết lộ account existence hoặc trạng thái nội bộ chi tiết của token.
  - Không trả stack trace, exception name, Redis key hoặc lỗi SQL cho client.

- **Làm như thế nào?**
  - Password chỉ lưu encoded hash.
  - Refresh token và reset token chỉ lưu SHA-256 hash.
  - Redis key chỉ chứa identifier đã hash.
  - `.env` tiếp tục bị ignore; private key không được đưa vào repository.
  - Sử dụng status theo đúng loại lỗi:
    - `400`: request/validation không hợp lệ.
    - `401`: thông tin xác thực hoặc token không hợp lệ.
    - `403`: CSRF hoặc hành động bị policy từ chối.
    - `409`: domain conflict thực sự.
    - `429`: vượt rate limit.
    - `503`: Redis không khả dụng theo fail-closed policy.
  - Secret scan và log scan được chạy ở bước audit cuối.

---

## 11. Kiểm thử và audit toàn bộ Authentication

- **Đã làm gì?**
  - Xây dựng ma trận kiểm thử cho toàn bộ authentication flow.
  - Kiểm thử unit, integration, security, migration, rollback và concurrency.
  - Chạy audit cuối trước khi đóng giai đoạn.

- **Giải quyết vấn đề gì?**
  - Không chỉ chứng minh từng service chạy riêng lẻ, mà chứng minh toàn bộ hệ thống hoạt động đúng với Spring Security, PostgreSQL và Redis thật.
  - Phát hiện regression giữa registration, login, JWT, refresh, logout, password recovery, rate limiting và CSRF.
  - Tránh test xanh giả do mock database, mock Redis hoặc bypass security.

- **Làm như thế nào?**
  - Dùng PostgreSQL Testcontainers để kiểm tra migration, transaction, row locking và concurrent request.
  - Dùng Redis Testcontainers để kiểm tra TTL, atomic counter và concurrency.
  - Không dùng H2 để chứng minh hành vi riêng của PostgreSQL.
  - Không mock Redis cho atomicity và TTL.
  - Không tắt hoặc bypass Spring Security trong integration test.
  - Kiểm tra JWT với token hết hạn, sai chữ ký, sai issuer, sai audience và sai/missing `typ`.
  - Kiểm tra CSRF thiếu, sai và hợp lệ trên từng mutation endpoint.
  - Kiểm tra database side effects sau cả request thành công và thất bại.

---

## 12. Kết quả cuối cùng

- BF-020 — Forgot/Reset Password: **Completed**.
- BF-021 — Rate Limiting & Authentication Security Hardening: **Completed**.
- BF-022 — Full Authentication Test Matrix & Final Audit: **Completed**.
- Toàn bộ Giai đoạn 2 — Authentication: **Completed**.
- `mvnw clean verify`: **PASS**.
- Unit tests: **19/19 passed**.
- Integration tests: **30/30 passed**.
- Failed: **0**.
- Errors: **0**.
- Skipped: **0**.
- `git diff --check`: **PASS**.
- Secret scan: **PASS**, không phát hiện private key hoặc token thật.
- Không phát hiện access token hoặc CSRF token trong test reports.
- PostgreSQL và Redis Compose vẫn healthy sau kiểm thử.

---

## 13. Giới hạn hiện tại và công việc có thể làm sau

- Notification adapter của forgot-password hiện là no-op:
  - Domain và application port đã sẵn sàng.
  - Hệ thống chưa tích hợp nhà cung cấp email thật.
  - Đây là phần hạ tầng có thể tách thành ticket riêng, không làm giảm tính đúng đắn của luồng tạo/consume reset token.
- Còn cảnh báo không chặn build:
  - PowerShell profile.
  - Mockito dynamic Java agent.
- Đây không phải lỗi chức năng và không ảnh hưởng kết quả đóng giai đoạn Authentication.

---

## 14. Luồng logic tổng quát của hệ thống

- **Đăng nhập**
  - Client gửi email, password và CSRF token.
  - Hệ thống kiểm tra rate limit.
  - Hệ thống validate request và xác thực password.
  - Nếu hợp lệ, tạo session/refresh token và phát hành JWT access token.
  - Nếu không hợp lệ, trả public authentication error chung.

- **Refresh token**
  - Client gửi refresh request hợp lệ theo security contract.
  - Hệ thống kiểm tra rate limit và CSRF.
  - PostgreSQL khóa và kiểm tra token/session.
  - Token cũ bị rotate, token mới được tạo trong cùng transaction.
  - Nếu phát hiện reuse, session/token family liên quan bị revoke theo policy.

- **Reset password**
  - Forgot-password tạo raw token và chỉ lưu SHA-256 hash.
  - Raw token đi qua notification boundary.
  - Reset-password kiểm tra rate limit, CSRF, TTL và trạng thái token.
  - PostgreSQL khóa reset-token record.
  - Hệ thống đổi password, consume token và revoke toàn bộ session của user trong một transaction.
  - Các thiết bị cũ buộc phải đăng nhập lại.

---

## 15. Cách trình bày ngắn gọn khi phỏng vấn

- “Trong BookFlow, em không chỉ làm API đăng ký và đăng nhập mà xây dựng toàn bộ authentication lifecycle.”
- “Access token dùng JWT RS256 theo RFC 9068; refresh token được rotation và chỉ lưu SHA-256 hash.”
- “Em xử lý refresh-token reuse và concurrent request bằng transaction cùng row locking trên PostgreSQL.”
- “Forgot-password chống dò email; reset token có TTL, dùng một lần và reset thành công sẽ thu hồi toàn bộ session cũ.”
- “Các authentication endpoint được rate limit bằng Redis Lua để increment và gắn TTL atomically, theo cả IP và account đã hash.”
- “Em giữ CSRF protection cho toàn bộ browser mutation endpoint, không tắt security để làm test pass.”
- “Cuối cùng em dùng PostgreSQL và Redis Testcontainers để kiểm tra transaction, rollback, concurrency và security; toàn bộ 49 test đều pass.”

