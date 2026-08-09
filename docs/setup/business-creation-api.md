# Tạo business và OWNER membership (BF-024)

`POST /api/v1/businesses` tạo một business mới và membership ban đầu cho người dùng đã đăng nhập. Hai thao tác chạy trong cùng transaction PostgreSQL: nếu không tạo được membership thì business cũng bị rollback.

## Yêu cầu bảo mật

Endpoint yêu cầu access token Bearer JWT hợp lệ và CSRF token theo flow authentication hiện có. Lấy CSRF trước qua `GET /api/v1/auth/csrf`, gửi cookie `XSRF-TOKEN` cùng header `X-XSRF-TOKEN`, rồi gửi access token trong `Authorization: Bearer <access-token>`.

Server lấy user từ `SecurityContext`/JWT `sub`; request không có `userId`, `ownerId`, `tenantId`, role, status hay audit timestamp. User phải còn tồn tại và `ACTIVE`.

## Request và response

```json
{
  "name": "Huy Hair Studio",
  "slug": "huy-hair-studio",
  "type": "SALON",
  "timeZone": "Asia/Ho_Chi_Minh"
}
```

Khi thành công API trả `201 Created`, header `Location` và dữ liệu business an toàn. `name` được trim; `slug` được trim, lowercase và phải khớp định dạng chữ thường/chữ số/gạch nối. `type` phải là enum schema; `timeZone` được kiểm tra bằng `ZoneId.of` và lưu IANA canonical. Server luôn tạo `status=ACTIVE` cùng membership `OWNER/ACTIVE`.

Theo ADR 0002, `business_memberships.tenant_id` chính là ID business vừa tạo; không có cột `business_id` song song.

## Xung đột và lỗi

PostgreSQL unique constraint của `businesses.slug` là lớp bảo vệ cuối cùng. Hai request cùng slug chỉ có một request `201`; request còn lại nhận `409` với code `BUSINESS_SLUG_ALREADY_EXISTS`, không lộ SQL hay tên constraint. Request sai là `400`, thiếu/sai JWT là `401`, CSRF thiếu/sai là `403`.

BF-024 chưa có API sửa/xóa business, invitation, quản lý membership, chuyển OWNER, tenant context cho nghiệp vụ khác hoặc authorization theo role.
