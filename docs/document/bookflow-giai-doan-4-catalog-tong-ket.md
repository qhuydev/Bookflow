# Tổng kết Giai đoạn 4 — Business Catalog

## Phạm vi đã xây dựng

Giai đoạn 4 cung cấp luồng quản trị catalog theo tenant cho business, branch, employee, member và service; đồng thời có Public Catalog chỉ đọc theo business slug. Schedule, availability và booking chưa thuộc giai đoạn này.

Backend vẫn là modular monolith, dùng Spring JDBC và PostgreSQL làm nguồn dữ liệu chính. Mọi truy vấn tài nguyên nghiệp vụ được giới hạn bằng `tenant_id` cùng ID tài nguyên. Frontend dùng Next.js App Router, giữ logic HTTP trong lớp `lib/api` và state server trong các provider thay vì dùng mock làm nguồn dữ liệu dashboard.

## Authentication và bảo mật trình duyệt

- Access token là JWT RS256 và chỉ tồn tại trong React memory của `AuthProvider`; frontend không ghi token vào `localStorage` hoặc `sessionStorage`.
- Refresh token gốc nằm trong cookie `HttpOnly`; PostgreSQL chỉ lưu hash. Khi tải lại trang, frontend gọi refresh để khôi phục access token.
- Mutation lấy CSRF token từ `GET /api/v1/auth/csrf`, gửi cookie với `credentials: include` và header `X-XSRF-TOKEN`.
- Protected request gửi Bearer access token. Nếu backend trả `401`, frontend refresh và thử lại đúng một lần; refresh thất bại sẽ xóa authentication state, không tạo vòng lặp vô hạn.
- CORS lấy danh sách origin cho phép từ cấu hình. Local profile cho phép origin frontend local; `allowCredentials=true`, không dùng wildcard origin, và chỉ cho các method/header đã khai báo.

## Tenant và role

`BusinessProvider` tải danh sách business từ backend, lưu `selectedBusinessId` là UUID thật và tải detail của business đang chọn. `CatalogProvider` dùng cặp khóa phiên `accessToken:selectedBusinessId` để chỉ công bố dữ liệu đúng phiên và đúng tenant; khi đổi business, dữ liệu cũ bị ẩn cho tới khi lần tải mới hoàn tất.

Backend luôn đọc user từ JWT/Spring Security và kiểm tra membership hiện tại trong PostgreSQL. Client không quyết định `userId` hay tenant. Membership hoặc business không còn `ACTIVE` mất quyền ngay trong request tiếp theo, kể cả JWT vẫn còn hạn.

Quy tắc HTTP chính:

- `401`: thiếu hoặc sai xác thực.
- `403`: đã có membership hợp lệ nhưng role không đủ quyền, hoặc CSRF không hợp lệ.
- `404`: business/tài nguyên không tồn tại, inactive hoặc nằm ngoài tenant; cách trả này tránh làm lộ tenant.

`OWNER` và `ADMIN` quản lý catalog. `STAFF` chỉ đọc các tài nguyên được permission matrix cho phép và không được quản lý member. Chỉ `OWNER` quản lý membership.

## CRUD và assignment

- Business: list, create, detail và partial update.
- Branch: CRUD, archive mềm.
- Employee: CRUD, archive mềm, gán nhiều branch.
- Member: list, mời user đã tồn tại, đổi role, revoke mềm, link/unlink employee.
- Service: CRUD, archive mềm, gán branch và employee.

Assignment luôn được kiểm tra cùng tenant và trạng thái `ACTIVE`. Employee–service chỉ hợp lệ khi employee và service có ít nhất một branch active chung. Database có constraint/FK tenant-scoped làm lớp bảo vệ cuối cùng; Redis không tham gia quyết định tính nhất quán này.

Frontend refetch phần state liên quan sau mutation. Các mutation nhiều bước dùng `finally` để tải lại state server khi một bước thất bại, tránh giữ UI ở trạng thái thành công giả.

## Public Catalog boundary

Các endpoint `GET /api/v1/public/businesses/{slug}` và các nhánh `/branches`, `/services`, `/employees` không cần JWT hoặc CSRF. Backend chỉ trả business và tài nguyên `ACTIVE`, đồng thời validate branch/service filter thuộc đúng business.

Public response không chứa `tenant_id`, membership, user ID, email/phone riêng của employee, trạng thái nội bộ hoặc audit timestamps. Landing page `/` vẫn là dữ liệu giới thiệu tĩnh; trang `/{slug}` dùng Public Catalog API.

## Error contract

Backend dùng `application/problem+json` với status, code và detail an toàn. Frontend chuyển các lỗi `400`, `401`, `403`, `404`, `409`, `429` và `503` thành thông báo giao diện, không hiển thị stack trace, SQL hoặc credential.

## Kiểm thử và công cụ

Lệnh chuẩn:

```powershell
.\apps\api\mvnw.cmd -f .\apps\api\pom.xml test
.\apps\api\mvnw.cmd -f .\apps\api\pom.xml clean verify
apps\web\node_modules\.bin\tsc.cmd --noEmit -p apps\web\tsconfig.json
npm.cmd --prefix .\apps\web run build
npm.cmd --prefix .\apps\web run lint
git diff --check
```

`clean verify` chạy integration test trên PostgreSQL Testcontainers. Regression Giai đoạn 4 bao phủ role, tenant isolation, membership lifecycle, catalog assignment, migration và giới hạn dữ liệu Public Catalog. ESLint dùng flat config tương thích Next.js 16.

## Trạng thái verification BF-038

Kết quả chính thức phải lấy từ lần chạy lệnh gần nhất trong báo cáo BF-038; tài liệu này không thay thế test report. API-level regression và Testcontainers có thể xác minh backend mà không sửa PostgreSQL/Redis Compose. Browser smoke test vẫn phải được ghi rõ là PASS hay chưa chạy, không suy diễn từ build thành công.

## Giới hạn còn lại

- Chưa có schedule, availability, booking, payment hoặc notification.
- Chưa có browser automation end-to-end chính thức cho toàn bộ dashboard.
- Landing marketplace vẫn có nội dung demo tĩnh; Public Catalog theo slug mới là dữ liệu backend thật.
- Access token đã phát hành có hiệu lực theo thời hạn/validation hiện tại; session và refresh token có cơ chế revoke/rotation theo thiết kế authentication.
