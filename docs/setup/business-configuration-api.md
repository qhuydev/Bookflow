# Cập nhật cấu hình business (BF-029)

BF-029 thêm endpoint `PATCH /api/v1/businesses/{businessId}` để cập nhật từng phần cấu hình của một business đang hoạt động.

## Xác thực, CSRF và tenant authorization

Request phải có Bearer JWT hợp lệ và CSRF token hợp lệ theo convention hiện tại (`X-XSRF-TOKEN` cùng cookie `XSRF-TOKEN`). User được lấy từ JWT `sub`, không nhận `userId` hoặc tenant ID từ client.

`TenantAuthorizationService` đọc lại user, business và membership từ PostgreSQL. Chỉ membership `ACTIVE` với business `ACTIVE` mới được xét quyền. Permission cần có là `BUSINESS_CONFIGURATION_MANAGE`:

| Role | Quyền cập nhật cấu hình |
| --- | --- |
| `OWNER` | Có |
| `ADMIN` | Có |
| `STAFF` | Không (`403`) |

Business không tồn tại, không thuộc user, inactive hoặc membership inactive đều trả `404 RESOURCE_NOT_FOUND`; điều này không làm lộ tenant khác. JWT thiếu/sai trả `401`. CSRF thiếu/sai bị Spring Security chặn với `403` trước khi vào nghiệp vụ.

## Request partial update

Chỉ các trường hiện diện trong JSON được thay đổi. Body rỗng hoặc trường không được phép trả `400`.

```json
{
  "name": "BookFlow Salon Quận 1",
  "slug": "bookflow-salon-q1",
  "type": "SALON",
  "timeZone": "Asia/Ho_Chi_Minh",
  "currencyCode": "VND",
  "cancellationPolicy": "MODERATE",
  "maxBookingAdvanceDays": 30
}
```

Các trường hỗ trợ là `name`, `slug`, `type`, `timeZone`, `currencyCode`, `cancellationPolicy`, `maxBookingAdvanceDays`. Không thể sửa `id`, `status`, owner, membership hoặc thời điểm tạo.

- Tên không rỗng, tối đa 200 ký tự.
- Slug được trim, lower-case, theo mẫu `a-z`, số và dấu gạch ngang, tối đa 100 ký tự; slug trùng trả `409`.
- `type` là loại business có trong schema.
- `timeZone` phải là IANA time zone hợp lệ.
- `currencyCode` là mã ISO-4217 ba ký tự.
- `cancellationPolicy` là `FLEXIBLE`, `MODERATE` hoặc `STRICT`.
- `maxBookingAdvanceDays` nằm trong khoảng 0–365.

Migration V5 thêm các cấu hình có default tương thích dữ liệu cũ: `VND`, `FLEXIBLE` và 90 ngày. Update dùng một câu lệnh JDBC có điều kiện membership/role trong cùng transaction, nên không lọc tenant ở Java hoặc dùng `SELECT *`.

## Response và lỗi

Thành công trả `200 OK` với business đã cập nhật cùng membership của chính user hiện tại. Response không chứa membership của user khác.

- `400`: UUID/body/trường cấu hình không hợp lệ.
- `401`: JWT không có hoặc không hợp lệ.
- `403`: membership active nhưng role không có permission.
- `404`: business/membership không hợp lệ cho tenant hiện tại.
- `409`: slug đã thuộc business khác.

BF-029 không thêm API xóa/cập nhật business khác, quản lý membership, branch, employee, service hay frontend.
