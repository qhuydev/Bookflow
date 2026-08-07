# Security review: Multi-tenancy và tenant isolation

- **Ticket:** BF-011
- **Ngày review:** 2026-08-05
- **Phạm vi:** membership, tenant context, authorization boundary và data isolation
- **Quyết định nguồn:** [ADR 0002](../adr/0002-multi-tenancy-and-membership.md)

BF-011 là thiết kế. Các mitigation dưới đây là yêu cầu cho ticket triển khai, không phải xác nhận runtime hiện đã cô lập tenant.

## Security goals và trust boundaries

### Security goals

- User chỉ đọc/sửa dữ liệu business có active membership.
- User có nhiều business chỉ thao tác trong active membership của đúng session.
- Role tenant không thể nâng quyền hoặc thay đổi membership ngoài phạm vi.
- Resource UUID, query, mutation, join, job và audit không tạo đường truy cập chéo tenant.
- Database giữ invariant tenant ownership ngay cả khi application có bug ở một lớp.

### Trust boundaries

- Client → API: tenant ID do client cung cấp là untrusted candidate, không là authority.
- API → PostgreSQL: PostgreSQL là source of truth cho session, membership, role và tenant status.
- API → Redis: cache/rate limit không quyết định quyền tenant.
- Application layers: controller, service, repository, async consumer đều phải giữ `TenantContext` đã xác thực.
- Operations: global support không mặc định là bypass tenant boundary.

## Abuse cases

1. User A đổi `tenant_id` trong body/header/path để đọc booking của tenant B.
2. User A đoán UUID resource B và gọi update/delete trực tiếp.
3. Repository có `findById` hoặc native query thiếu predicate tenant.
4. Child resource tenant A được gắn với parent tenant B qua foreign key chỉ có ID.
5. ADMIN tự cấp OWNER/ADMIN hoặc thay membership tenant khác.
6. Membership bị revoke nhưng active session/cache vẫn cấp access.
7. Hai request switch/revoke hoặc owner-transfer chạy đồng thời tạo context sai/tenant không owner.
8. Error/log/audit trả tenant name, ID hoặc metadata không thuộc context hiện tại.
9. Bulk update/delete thiếu tenant predicate làm ảnh hưởng nhiều business.
10. Job/event consumer nhận tenant ID giả hoặc dùng context trước đó từ worker pool.

## Risk and mitigation matrix

| Threat | Scenario/asset | Likelihood | Impact | Mitigation bắt buộc | Residual risk | Test dự kiến | Owner |
|---|---|---:|---:|---|---|---|---|
| Forged tenant input | Client gửi tenant B vào header/body/query | High | Critical | Không generic tenant header; server resolve từ `sid` và active membership; reject client-owned tenant field | Candidate switch vẫn cần validate | HTTP forged input test | Tenant implementation |
| IDOR/BOLA | UUID tenant B được đoán/nhặt từ URL | High | Critical | Scope mọi read/mutation bằng `tenant_id`; cross-tenant trả not-found | UUID leak từ nơi khác vẫn tạo request | Two-tenant HTTP/read/write/delete test | Tenant implementation |
| Missing predicate | Repository/native SQL không có tenant condition | Medium | Critical | TenantContext-required repository API, review, architecture/static test, DB composite constraints | Human error ở query mới | Static query/repository scan + integration | Tenant implementation |
| Cross-tenant join | Child A tham chiếu parent B | Medium | Critical | `tenant_id` ở cả hai bảng, composite FK `(tenant_id,parent_id)` | Migration constraint bị thiếu ở table mới | Testcontainers FK rejection test | Data migration ticket |
| Bulk mutation | Update/delete theo status hoặc ID không scope | Medium | Critical | `WHERE tenant_id = :contextTenantId`, verify affected rows | Native/admin SQL abuse | Integration affected-row tests | Tenant implementation |
| Membership role escalation | ADMIN tự cấp/chỉnh owner/admin | Medium | Critical | Explicit policy matrix, server-side target-role checks, owner-only transfer | Owner account compromise | HTTP role transition matrix | Membership implementation |
| Ownerless/two-owner tenant | Concurrent transfer/demotion | Low | Critical | Transaction, row lock/constraint, exact one active OWNER | Failed transaction/retry UX | Barrier transfer test | Membership implementation |
| Stale membership | Revoked/suspended role vẫn access | Medium | High | PostgreSQL lookup mỗi request scoped; cache only with invalidation/version and fail closed | In-flight request có thể hoàn tất | Revoke then next request test | Tenant implementation |
| Invalid tenant switch | User chọn business không có membership | High | High | Candidate validated by `sub`, `sid`, active membership, active tenant in transaction | Generic denial still leaks timing minimally | Switch negative test | Tenant implementation |
| Switch/revoke race | Switch succeeds while membership revoked | Medium | High | Conditional update and resolver recheck state; audit both outcomes | Retry ordering complexity | Executor/barrier integration test | Tenant implementation |
| Tenant enumeration | Error tells whether business/resource B exists | Medium | Medium | Generic access error for switch; scoped resource returns `RESOURCE_NOT_FOUND`; no tenant metadata | User may infer from own invitation flows | HTTP response/body negative test | API implementation |
| Context leakage in async work | Worker reuses tenant context or trusts event value | Medium | Critical | Event has server-created tenant id; consumer re-resolves/validates; never ThreadLocal leak across jobs | Operational bug | Worker/context cleanup test | Async/audit ticket |
| Audit/log leakage | Logs expose foreign tenant data or raw client ID | Medium | High | Log only verified tenant id where justified, redact data, structured audit | Operator access remains sensitive | Captured-log tests | Platform/audit ticket |
| Cache authority | Redis stale/evicted membership becomes access decision | Medium | High | PostgreSQL authority; cache version/invalidate; cache failure never expands access | Performance degradation on cache miss | Cache stale/fail-closed test | Platform ticket |
| RLS misconception | Team assumes DB policy protects unscoped query | Medium | High | RLS not assumed; predicates/constraints mandatory; RLS only later with ADR | Defense in depth absent initially | Architecture review/test matrix | Tenant implementation |
| System-admin bypass | Platform support path accesses arbitrary tenant silently | Low | Critical | No implicit bypass; separate break-glass ADR, JIT approval, immutable audit | Future support tooling | Negative authorization tests | Operations ticket |
| Tenant disabled | Disabled business remains active via existing context | Medium | High | Resolver verifies tenant status each scoped request; disable invalidates contexts | In-flight access window | Disable tenant integration test | Business management ticket |
| Membership duplicate | Same user has conflicting roles in one tenant | Medium | Medium | Unique `(user_id, tenant_id)` | Legacy/import data needs cleanup | Constraint migration test | Data migration ticket |

## Security review checklist

- [x] Tenant được định nghĩa là business; `tenant_id` là canonical isolation column.
- [x] User global có thể có nhiều membership, unique theo `(user_id, tenant_id)`.
- [x] Role `OWNER`, `ADMIN`, `STAFF` và anti-escalation rule đã chốt.
- [x] Tenant context xuất phát từ session/membership server-side, không từ request field.
- [x] Business switch chỉ nhận candidate và validate trong transaction.
- [x] Read/create/update/delete/join/async/audit đều có tenant invariant.
- [x] Composite FK, index và mutation affected-row checks đã được thiết kế.
- [x] PostgreSQL là authority; Redis cache không mở rộng access.
- [x] Tenant enumeration, IDOR, bulk mutation, role escalation và race được threat-model.
- [x] RLS không bị hiểu nhầm là control duy nhất.
- [x] Test matrix có unit, HTTP, Testcontainers, static architecture và concurrency test.
- [x] Không có API, schema, migration hoặc runtime security control được tuyên bố đã tồn tại.

## Test matrix cho ticket triển khai

| Nhóm | Scenario | Kỳ vọng |
|---|---|---|
| Unit | Resolve `sid` với session/membership active | TenantContext bất biến đúng user, tenant, role |
| Unit | Session user khác membership user | Context bị từ chối |
| Unit | Membership suspended/revoked hoặc tenant disabled | Context bị từ chối, không fallback cache |
| Unit | Role policy | ADMIN không cấp OWNER/ADMIN; STAFF không quản lý membership |
| Unit | Client payload có `tenant_id` | Bị bỏ qua/reject; server set context tenant |
| Architecture | Repository/service tenant-owned | Không có public unscoped `findById`, query/mutation phải nhận context/tenant |
| HTTP | Switch tenant hợp lệ | Chỉ cập nhật active membership của session hiện tại |
| HTTP | Switch tenant không thuộc user | Safe `TENANT_ACCESS_DENIED`, không lộ tenant |
| HTTP | No active tenant context | `TENANT_CONTEXT_REQUIRED`, không tự chọn tenant ngầm |
| HTTP | Resource UUID tenant khác | 404 ProblemDetail an toàn, không data leak |
| Integration | Hai tenant, hai user | Read/create/update/delete chỉ thấy row đúng tenant |
| Integration | Một user hai memberships | Switch chỉ thay context session, không thay quyền business khác |
| Integration | Composite FK | Không thể link child tenant A tới parent tenant B |
| Integration | Unique membership | Không thể tạo duplicate user/tenant membership |
| Integration | Bulk mutation | Tenant A mutation không đổi row tenant B |
| Integration | Revoke/disable | Request sau revoke/disable không còn access |
| Integration | Owner transfer | Không commit trạng thái zero/hai active owner |
| Concurrency | Switch đồng thời revoke | State cuối không có active context membership revoked |
| Concurrency | Owner transfer/demotion | Transaction/database invariant luôn giữ |
| Async | Event/job tenant-scoped | Consumer resolve tenant mới, không mang context cũ worker thread |
| Logging | Error/audit/log capture | Không lộ foreign tenant resource hoặc untrusted tenant input |

Concurrency test phải dùng executor/barrier hoặc tương đương để transaction thực sự cạnh tranh. Không dùng lời gọi tuần tự hoặc sleep dài giả lập race.

## Residual risks và deferred work

- Chưa có RLS defense-in-depth; application predicate và composite constraint phải được review nghiêm ngặt.
- Tenant-scoped lookup tăng tải PostgreSQL; cache chỉ được thêm sau khi có invalidation/version và test fail-closed.
- System Admin support/break-glass, invitation lifecycle, customer/guest access và module permission chi tiết chưa được thiết kế.
- Existing access JWT còn hiệu lực theo TTL, nhưng tenant resolver rechecks membership/session cho endpoint scoped.
- Migration rollout cho dữ liệu legacy phải có backfill, NOT NULL validation và zero-cross-tenant-data plan riêng.

## Official references

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [OWASP Insecure Direct Object Reference Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html)
- [PostgreSQL Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
