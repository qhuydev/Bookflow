# Business và Business Membership schema (BF-023/BF-024)

BF-023 tạo nền dữ liệu PostgreSQL cho business tenant và membership của user global. BF-024 dùng schema này cho API tạo business và membership `OWNER/ACTIVE` ban đầu trong cùng một transaction. Chưa có invitation, Spring Security authorization theo role, tenant context runtime hoặc bảng nghiệp vụ như branch/booking.

## Business là tenant

`businesses.id` là UUID tenant. Bảng lưu tên, slug duy nhất, loại business, IANA time zone có kiểm tra định dạng, trạng thái và audit timestamp.

| Trường | Quy tắc |
|---|---|
| `slug` | unique, lowercase, chỉ chữ số/chữ thường/gạch nối |
| `business_type` | `SALON`, `SPA`, `CLINIC`, `TUTORING_CENTER`, `STUDIO`, `OTHER` |
| `status` | `ACTIVE`, `SUSPENDED`, `CLOSED` |
| `time_zone` | `UTC` hoặc chuỗi có hình dạng IANA; ticket API sau phải xác thực IANA đầy đủ |

## Membership

`business_memberships` liên kết `users.id` với `businesses.id`. Theo ADR 0002, cột được đặt tên `tenant_id`: đây chính là ID của business và là tên chuẩn dùng cho mọi bảng business-owned tiếp theo. Không tạo thêm `business_id` song song.

| Trường | Quy tắc |
|---|---|
| `role` | `OWNER`, `ADMIN`, `STAFF`; chỉ có hiệu lực trong một tenant |
| `status` | `ACTIVE`, `SUSPENDED`, `REVOKED` |
| `(user_id, tenant_id)` | unique: một user chỉ có một membership trong một business, nhưng được thuộc nhiều business |
| `revoked_at`, `revoked_by` | audit khi membership bị thu hồi |

## Foreign key, index và xóa dữ liệu

Tất cả FK từ membership đến business/user dùng `ON DELETE RESTRICT`. Xóa user hoặc business có membership sẽ bị PostgreSQL từ chối thay vì cascade âm thầm. Các index phục vụ lookup gồm `idx_business_memberships_tenant_status_role`, `idx_business_memberships_user_status` và unique index `(user_id, tenant_id)`; `businesses.slug` có unique index.

## Invariant để lại cho ticket sau

Database hiện không ép “mỗi business luôn có ít nhất một OWNER”, vì đây là invariant nhiều hàng không nên giải bằng trigger phức tạp trong BF-023. BF-024 tạo business và OWNER membership trong một transaction để giữ invariant ban đầu. Ticket quản lý membership/authorization sau này phải tiếp tục dùng transaction, locking và policy role khi chuyển owner, hạ quyền hoặc revoke.

Khi bảng nghiệp vụ được thêm, chúng phải có `tenant_id NOT NULL`, FK tới `businesses(id)` và các composite FK theo ADR 0002 để chặn liên kết chéo tenant.
