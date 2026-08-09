# Xem business của user hiện tại (BF-025)

BF-025 cung cấp hai endpoint chỉ đọc:

- `GET /api/v1/businesses`
- `GET /api/v1/businesses/{businessId}`

Cả hai yêu cầu access token Bearer JWT hợp lệ. Vì là `GET`, chúng không yêu cầu CSRF token. User hiện tại luôn được lấy từ `SecurityContext`/JWT `sub`; client không gửi `userId` hay tenant ID.

## Tenant isolation

`TenantAuthorizationService` dùng JDBC query thực hiện một `JOIN` giữa `business_memberships` và `businesses`, rồi lọc ngay trong PostgreSQL theo:

- `m.user_id` của authenticated user;
- `m.status = ACTIVE`;
- `b.status = ACTIVE`.

`business_memberships.tenant_id` là `businesses.id` theo ADR 0002. Query chọn rõ cột cần thiết, không dùng `SELECT *`, không lọc tenant ở Java và không phát sinh N+1 query. Danh sách có thứ tự ổn định `created_at`, rồi `id`.

Response chỉ có dữ liệu business và membership của chính user hiện tại (`OWNER`, `ADMIN` hoặc `STAFF` khi membership đang `ACTIVE`). Membership của người khác không được trả về.

## Error contract

- JWT thiếu hoặc sai: `401` theo Spring Security.
- UUID `businessId` sai định dạng: `400` theo ProblemDetail.
- Business không tồn tại, bị inactive, membership inactive hoặc không thuộc user: cùng trả `404 RESOURCE_NOT_FOUND`, tránh lộ tenant.
- Danh sách không có business phù hợp: `200` với `[]`.

BF-025 không thêm pagination vì repository chưa có convention phân trang. Xem thêm [tenant authorization và permission matrix](tenant-authorization.md).
