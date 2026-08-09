# Tenant authorization và permission matrix (BF-026–BF-028)

BookFlow xác thực JWT trước, rồi phân quyền tenant bằng trạng thái hiện tại trong PostgreSQL. JWT chỉ cung cấp identity (`sub`); không mang `tenant_id`, membership hoặc role để tránh quyền cũ còn hiệu lực đến khi token hết hạn.

## Cơ chế chung

`TenantAuthorizationService` là điểm dùng chung cho service tenant-scoped. Mỗi lần kiểm tra, nó xác minh user global còn `ACTIVE`, dùng JDBC `JOIN business_memberships` với `businesses`, chỉ chấp nhận membership/business `ACTIVE`, rồi kiểm tra permission theo matrix role.

`tenant_id` trong membership chính là `businesses.id`. Không dùng `ThreadLocal`, `X-Tenant-Id`, body/query tenant ID hay role/tenant claim do client khai báo. Business không nhìn thấy từ user hiện tại trả `404` trung tính; membership hợp lệ nhưng không có permission trả `403`.

## Permission matrix

| Permission | OWNER | ADMIN | STAFF |
|---|:---:|:---:|:---:|
| `BUSINESS_VIEW` | Có | Có | Có |
| `BUSINESS_CONFIGURATION_MANAGE` | Có | Có | Không |
| `MEMBERSHIP_STAFF_MANAGE` | Có | Có | Không |
| `MEMBERSHIP_PRIVILEGED_MANAGE` | Có | Không | Không |
| `BUSINESS_CLOSE` | Có | Không | Không |

BF-025 dùng `BUSINESS_VIEW`, nên cả ba role active đều đọc được business của chính mình. Các permission quản trị đã được định nghĩa và kiểm thử ở service để endpoint tương lai tái sử dụng; ticket này không thêm API quản trị, invitation hay thay đổi membership.

## Quy tắc HTTP

| Tình huống | Status |
|---|---:|
| JWT thiếu, sai hoặc hết hạn | `401` từ Spring Security |
| UUID path sai | `400` ProblemDetail |
| Business không tồn tại, tenant khác, business inactive, membership suspended/revoked | `404 RESOURCE_NOT_FOUND` |
| Membership active nhưng permission không đủ | `403 TENANT_PERMISSION_DENIED` |

Việc revoke/suspend membership hoặc deactivate business có hiệu lực ở request tiếp theo, kể cả access token cũ vẫn còn hạn, vì lookup diễn ra trực tiếp trên PostgreSQL. Không dùng Redis như authority authorization.
