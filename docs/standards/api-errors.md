# Chuẩn lỗi API

BookFlow dùng một error contract thống nhất để frontend và các client xử lý lỗi có thể dự đoán được, đồng thời tránh làm lộ chi tiết triển khai. Contract dựa trên `ProblemDetail` của Spring và chuẩn Problem Details; mọi response lỗi do global handler tạo có content type `application/problem+json`.

## Cấu trúc response

Các field chuẩn:

- `type`: URI ổn định mô tả loại lỗi, theo dạng `urn:bookflow:problem:<type>`.
- `title`: tiêu đề ngắn, dành cho người đọc.
- `status`: HTTP status dạng số.
- `detail`: mô tả công khai, an toàn; không dùng field này để điều khiển logic client.
- `instance`: chỉ chứa request path, không chứa query string.

Các property của BookFlow:

- `code`: mã ổn định để frontend xử lý bằng chương trình.
- `timestamp`: thời điểm phát sinh response theo ISO-8601 UTC.
- `violations`: chỉ có ở lỗi validation; danh sách được sắp xếp theo `field`, `code`, `message` và loại bỏ bản ghi trùng.

Mỗi phần tử `violations` có `field`, `code` constraint và `message` công khai. Lỗi ở cấp object dùng field `$`. Response không chứa rejected value, exception class, stack trace, SQL, database detail hay cause chain.

## Error code hiện tại

| Trường hợp | HTTP status | `code` |
|---|---:|---|
| Bean hoặc method parameter validation thất bại | 400 | `VALIDATION_ERROR` |
| JSON không đọc được | 400 | `MALFORMED_REQUEST` |
| Thiếu hoặc sai request parameter | 400 | `INVALID_REQUEST` |
| Slot booking không còn khả dụng | 409 | `SLOT_UNAVAILABLE` |
| Idempotency key được dùng với payload khác | 409 | `IDEMPOTENCY_KEY_REUSED` |
| Booking đã đổi trạng thái hoặc mutation cạnh tranh | 409 | `BOOKING_STATE_CONFLICT` |
| Resource nghiệp vụ không tồn tại | 404 | `RESOURCE_NOT_FOUND` |
| Endpoint không tồn tại | 404 | `ENDPOINT_NOT_FOUND` |
| HTTP method không hỗ trợ | 405 | `METHOD_NOT_ALLOWED` |
| Media type không hỗ trợ | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Lỗi không xác định | 500 | `INTERNAL_ERROR` |

## Ví dụ

Validation:

```json
{
  "type": "urn:bookflow:problem:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/example",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-08-02T15:30:00Z",
  "violations": [
    {
      "field": "name",
      "code": "NotBlank",
      "message": "must not be blank"
    }
  ]
}
```

Resource không tồn tại:

```json
{
  "type": "urn:bookflow:problem:resource-not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "The requested resource was not found.",
  "instance": "/api/resources/123",
  "code": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-08-02T15:30:00Z"
}
```

Lỗi không xác định:

```json
{
  "type": "urn:bookflow:problem:internal-error",
  "title": "Internal server error",
  "status": 500,
  "detail": "An unexpected error occurred.",
  "instance": "/api/example",
  "code": "INTERNAL_ERROR",
  "timestamp": "2026-08-02T15:30:00Z"
}
```

## Quy ước cho client và backend

Frontend được phép phụ thuộc vào HTTP status, `code` và cấu trúc `violations`. `type` có thể dùng để nhận diện loại Problem Details. Không phân nhánh logic bằng `title`, `detail`, `message` hoặc nội dung hiển thị vì các chuỗi này có thể được cải thiện hay bản địa hóa trong tương lai.

`ResourceNotFoundException` chỉ nhận public detail đã được biên soạn an toàn; không đưa raw client input, database identifier hoặc dữ liệu tenant vào detail. Lỗi 500 được log ở server để điều tra nhưng client luôn nhận thông báo chung.

Khi ticket tương lai cần mapping mới:

1. Thêm mã và metadata vào `ApiErrorCode`.
2. Thêm mapping hẹp cho exception/trường hợp cụ thể trong `GlobalExceptionHandler`; không bắt `Throwable`.
3. Bảo đảm response giữ đúng schema và không lộ dữ liệu nội bộ.
4. Thêm MockMvc test kiểm tra status, `code`, content type, các field bắt buộc và các dữ liệu tuyệt đối không được xuất hiện.
5. Cập nhật bảng mapping trong tài liệu này.
