# Public Catalog API (BF-036)

Các endpoint dưới đây không cần JWT hoặc CSRF và chỉ trả dữ liệu công khai của business `ACTIVE`.

- `GET /api/v1/public/businesses/{slug}`: `slug`, `name`, `timeZone`, `currency`.
- `GET /api/v1/public/businesses/{slug}/branches`: branch active, gồm ID, code, tên, địa chỉ và timezone.
- `GET /api/v1/public/businesses/{slug}/services?branchId={uuid}`: service active; `branchId` là tùy chọn và chỉ trả service đã gán branch đó.
- `GET /api/v1/public/businesses/{slug}/employees?branchId={uuid}&serviceId={uuid}`: `branchId` bắt buộc; `serviceId` tùy chọn. Khi có service, employee phải được gán cả branch và service.

Ví dụ service:

```json
{"id":"uuid","name":"Cắt tóc","description":"...","price":150000.00,"currency":"VND","durationMinutes":45,"bufferBeforeMinutes":0,"bufferAfterMinutes":5}
```

Slug được trim và chuyển lowercase. Slug, branch, service hoặc filter không thuộc business/inactive trả `404`; UUID query sai định dạng trả `400` theo ProblemDetail chung.

API không công khai `tenantId`, membership, user ID, email/phone employee, status nội bộ hoặc audit timestamps. Không có availability, lịch hay booking trong BF-036.
