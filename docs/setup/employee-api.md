# API nhân viên và gán chi nhánh (BF-031, BF-033)

Employee luôn thuộc một business qua `tenant_id`; client không gửi tenant ID trong request body. Backend lấy user từ JWT và kiểm tra membership hiện tại trong PostgreSQL cho từng request.

## Endpoint

- `POST /api/v1/businesses/{businessId}/employees`: tạo employee, trả `201`.
- `GET /api/v1/businesses/{businessId}/employees`: danh sách employee `ACTIVE` ổn định.
- `GET /api/v1/businesses/{businessId}/employees/{employeeId}`: chi tiết employee `ACTIVE`.
- `PATCH /api/v1/businesses/{businessId}/employees/{employeeId}`: cập nhật từng phần.
- `DELETE /api/v1/businesses/{businessId}/employees/{employeeId}`: archive mềm, trả `204`.
- `PUT`/`DELETE /api/v1/businesses/{businessId}/employees/{employeeId}/branches/{branchId}`: gán/bỏ gán idempotent, trả `204`.
- `GET /api/v1/businesses/{businessId}/employees/{employeeId}/branches`: danh sách branch ID active đang gán.

`code` được trim, chuyển uppercase và unique theo business. Employee archive không bị xóa vật lý và không còn xuất hiện ở GET. Assignment chỉ hợp lệ khi employee và branch đều `ACTIVE`, cùng tenant; PostgreSQL dùng composite foreign key để chặn gán chéo tenant.

| Permission | OWNER | ADMIN | STAFF |
|---|:---:|:---:|:---:|
| `EMPLOYEE_VIEW` | Có | Có | Có |
| `EMPLOYEE_MANAGE` | Có | Có | Không |

JWT thiếu/sai trả `401`; membership/business/resource không nhìn thấy trả `404`; membership active nhưng thiếu quyền mutation trả `403`; request hoặc UUID không hợp lệ trả `400`; code trùng trong cùng business trả `409`.

Các mutation giữ CSRF hiện có. Trong Swagger, Authorize bằng access token rồi gọi `GET /api/v1/auth/csrf` và cung cấp header `X-XSRF-TOKEN`.
