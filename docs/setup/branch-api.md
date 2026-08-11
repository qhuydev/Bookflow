# Quản lý chi nhánh (BF-030)

BF-030 thêm `POST`, `GET`, `PATCH` và `DELETE` tại `/api/v1/businesses/{businessId}/branches`. `DELETE` chỉ archive, không hard delete.

Mọi endpoint cần Bearer JWT. Các request thay đổi dữ liệu còn cần CSRF cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN`.

## Schema và validation

Flyway V6 tạo bảng `branches`. `tenant_id` tham chiếu `businesses.id`; PostgreSQL thực thi unique `(tenant_id, code)`. Vì vậy hai business được dùng cùng code, nhưng không được trùng trong cùng business.

Code được trim, chuyển upper-case và chỉ gồm chữ hoa, số, dấu gạch ngang. Tên, `addressLine1`, `city`, `countryCode` là bắt buộc. Country code ISO alpha-2, email, phone và IANA timezone được kiểm tra khi có. Timezone bị bỏ qua lúc tạo sẽ kế thừa timezone hiện tại của business.

## Phân quyền và isolation

`TenantAuthorizationService` đọc lại membership/business active từ PostgreSQL trên từng request. Không dùng `userId`, `tenantId` từ body hay header.

| Permission | OWNER | ADMIN | STAFF |
| --- | --- | --- | --- |
| `BRANCH_VIEW` | Có | Có | Có |
| `BRANCH_MANAGE` | Có | Có | Không |

`404 RESOURCE_NOT_FOUND` áp dụng cho business/membership inactive, tenant khác, branch archived hoặc branch không thuộc business trên path. Membership active thiếu quyền quản lý nhận `403 TENANT_PERMISSION_DENIED`; JWT thiếu/sai là `401`; CSRF thiếu/sai là `403` trước tầng nghiệp vụ.

`PATCH` chỉ đổi field có trong body. Field bị cấm, body rỗng hoặc dữ liệu sai trả `400`; code trùng trong tenant trả `409` mà không lộ SQL. Archive chuyển trạng thái sang `ARCHIVED` trong transaction; archive lặp lại trả `204` idempotent. Không có employee, service, lịch, booking hay khôi phục branch archive trong BF-030.
