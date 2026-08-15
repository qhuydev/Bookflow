# Ticket khởi đầu

> Trạng thái: các checkpoint nền BF-001 đến BF-023 được giữ nguyên; BF-023 đã hoàn thành sau full Maven verification. BF-005 vẫn giữ trạng thái riêng đã ghi bên dưới.

## BF-001 — Khởi tạo monorepo và tài liệu nền (Hoàn thành)
- Mục tiêu: tạo bộ khung repository và quy tắc phát triển.
- Phạm vi: cấu trúc thư mục, tài liệu, script kiểm tra, Git configuration và template GitHub.
- Không nằm trong phạm vi: Spring Boot, Next.js, Docker Compose và dependency.
- Tiêu chí hoàn thành: đủ file được yêu cầu, Git branch `main`, kiểm tra khả dụng được ghi nhận.
- Kiểm thử hoặc kiểm tra bắt buộc: tree, Git status/branch, syntax script, script môi trường, link Markdown, ignore pattern và quét secret.
- Ticket phụ thuộc: không có.

## BF-002 — PostgreSQL Docker Compose và environment template (Hoàn thành)
- Mục tiêu: cung cấp PostgreSQL local có cấu hình mẫu an toàn.
- Phạm vi: Docker Compose PostgreSQL, volume và `.env.example`.
- Không nằm trong phạm vi: schema ứng dụng, Redis và backend.
- Tiêu chí hoàn thành: dịch vụ khởi động được theo tài liệu và không commit secret. Đã xác minh Docker, health check, kết nối và persistence của named volume.
- Kiểm thử hoặc kiểm tra bắt buộc: validate Compose, health check và kết nối local.
- Ticket phụ thuộc: BF-001.

## BF-003 — Redis local (Hoàn thành)
- Mục tiêu: bổ sung Redis local cho cache và giữ chỗ tạm thời.
- Phạm vi: service Redis, cấu hình mẫu và tài liệu.
- Không nằm trong phạm vi: Redis cluster hoặc dùng Redis làm khóa cuối cùng chống đặt trùng.
- Tiêu chí hoàn thành: Redis chạy và kiểm tra kết nối được. Đã xác minh Docker, authentication, AOF persistence và service isolation.
- Kiểm thử hoặc kiểm tra bắt buộc: health check và `PING`.
- Ticket phụ thuộc: BF-002.

## BF-004 — Khởi tạo Spring Boot (Hoàn thành)
- Mục tiêu: tạo ứng dụng backend Java 21 tối thiểu.
- Phạm vi: Spring Boot, cấu trúc module ban đầu và cấu hình chạy local.
- Không nằm trong phạm vi: nghiệp vụ booking hoàn chỉnh.
- Tiêu chí hoàn thành: build và test cơ bản chạy được. Đã xác minh Maven Wrapper, Java 21, executable JAR, application context và Actuator health.
- Kiểm thử hoặc kiểm tra bắt buộc: build, unit test và application context test.
- Ticket phụ thuộc: BF-001, BF-002.

## BF-005 — Khởi tạo Next.js (Đang thực hiện)
- Mục tiêu: tạo ứng dụng frontend Next.js và TypeScript strict tối thiểu.
- Phạm vi: App Router, CSS Modules, lint, unit test, cấu trúc thư mục và trang khởi đầu.
- Không nằm trong phạm vi: màn hình nghiệp vụ hoàn chỉnh, authentication, booking và API integration.
- Tiêu chí hoàn thành: lint, type check, unit test, production build và production smoke test chạy được; production dependency không còn lỗ hổng nghiêm trọng chưa xử lý.
- Kiểm thử hoặc kiểm tra bắt buộc: ESLint, type check, Vitest, production build và HTTP smoke test.
- Ticket phụ thuộc: BF-001.

> Kiểm tra chức năng và production audit BF-005 đã đạt sau khi thêm overrides tạm thời. Tuy nhiên, `npm ls postcss sharp` còn báo PostCSS của Vite là dependency không hợp lệ so với override toàn cục, nên ticket chưa được đánh dấu hoàn thành.

## BF-006 — Thiết lập Flyway (Hoàn thành)
- Mục tiêu: kiểm soát thay đổi database bằng migration.
- Phạm vi: tích hợp Flyway, vị trí migration và migration baseline.
- Không nằm trong phạm vi: toàn bộ schema nghiệp vụ.
- Tiêu chí hoàn thành: migration chạy tiến lên an toàn trên database trống, validate thành công và lần chạy lại không tạo migration trùng.
- Kiểm thử hoặc kiểm tra bắt buộc: unit/context build, integration test migration trên PostgreSQL thật, executable JAR và runtime health.
- Ticket phụ thuộc: BF-002, BF-004.

> Đã xác minh V1 trên PostgreSQL 17 thật, Flyway validate thành công, lần migrate thứ hai không tạo migration mới, integration schema được cleanup và runtime health trả `UP`.

## BF-007 — Testcontainers (Hoàn thành)
- Mục tiêu: tạo nền kiểm thử tích hợp với PostgreSQL thực.
- Phạm vi: PostgreSQL Testcontainer, Spring Boot Service Connection và Flyway integration test trong lifecycle Maven mặc định.
- Không nằm trong phạm vi: bộ test nghiệp vụ đầy đủ.
- Tiêu chí hoàn thành: `clean test` không tạo container; `clean verify` tự chạy integration test trên database tạm và container được cleanup.
- Kiểm thử hoặc kiểm tra bắt buộc: hai lần `clean verify`, Flyway validate/migrate lại, container lifecycle và runtime local regression.
- Ticket phụ thuộc: BF-004, BF-006.

> Đã xác minh `clean test` không tạo container; hai lần `clean verify` tạo hai PostgreSQL Testcontainer độc lập, chạy `FlywayMigrationIT` không skipped và cleanup hoàn toàn. PostgreSQL/Redis Compose không bị ảnh hưởng.

## BF-008 — Global exception handling (Hoàn thành)
- Mục tiêu: chuẩn hóa lỗi API.
- Phạm vi: error response, handler validation và mapping lỗi phổ biến.
- Không nằm trong phạm vi: mọi lỗi nghiệp vụ tương lai.
- Tiêu chí hoàn thành: API trả lỗi nhất quán, không lộ thông tin nhạy cảm.
- Kiểm thử hoặc kiểm tra bắt buộc: MockMvc test cho validation, malformed request, not found, lỗi HTTP phổ biến và fallback 500; full Maven verification và runtime smoke test.
- Ticket phụ thuộc: BF-004.

> Đã xác minh MockMvc tests, full Maven verification và runtime profile `local`. Actuator health trả `200/UP`; endpoint không tồn tại trả `404 application/problem+json` với code `ENDPOINT_NOT_FOUND`, không lộ chi tiết nội bộ. PostgreSQL/Redis Compose không bị ảnh hưởng.

## BF-009 — OpenAPI (Hoàn thành)
- Mục tiêu: cung cấp mô tả API có thể truy cập.
- Phạm vi: OpenAPI base, metadata và cách bảo vệ tài liệu nếu cần.
- Không nằm trong phạm vi: đặc tả đầy đủ cho toàn bộ endpoint tương lai.
- Tiêu chí hoàn thành: endpoint tài liệu hợp lệ.
- Kiểm thử hoặc kiểm tra bắt buộc: kiểm tra sinh OpenAPI và endpoint docs.
- Ticket phụ thuộc: BF-004, BF-008.

> Đã xác minh OpenAPI JSON và Swagger UI bằng HTTP test, full Maven verification và runtime profile `local`. Metadata đúng contract, tài liệu không chứa cấu hình nội bộ, regression BF-008 vẫn đạt và PostgreSQL/Redis Compose không bị ảnh hưởng.

## BF-010 — Thiết kế authentication (Hoàn thành)
- Mục tiêu: chốt thiết kế xác thực và refresh token.
- Phạm vi: luồng, threat model, dữ liệu và acceptance criteria.
- Không nằm trong phạm vi: triển khai hoàn chỉnh endpoint.
- Tiêu chí hoàn thành: thiết kế được ghi bằng tài liệu/ADR và được review.
- Kiểm thử hoặc kiểm tra bắt buộc: rà soát security và test strategy.
- Ticket phụ thuộc: BF-002, BF-004, BF-009.

> Deliverable là ADR trạng thái Accepted, authentication security review, threat model và test strategy. BF-010 chưa triển khai Spring Security, endpoint, schema hoặc migration authentication.

## BF-011 — Thiết kế multi-tenancy (Hoàn thành)
- Mục tiêu: chốt chiến lược shared schema với `business_id`.
- Phạm vi: tenant context, authorization, chiến lược truy vấn và test isolation.
- Không nằm trong phạm vi: triển khai mọi module nghiệp vụ.
- Tiêu chí hoàn thành: quy tắc tenant có thể kiểm thử và được ghi nhận.
- Kiểm thử hoặc kiểm tra bắt buộc: threat/risk review và test matrix tenant isolation.
- Ticket phụ thuộc: BF-006, BF-010.

> Deliverable là ADR trạng thái Accepted, security review, membership/role model, tenant isolation invariants và test strategy. BF-011 chưa triển khai API, entity, migration, Spring Security hoặc tenant context runtime.

## BF-012 — CI nền tảng
- Mục tiêu: tự động hóa kiểm tra cơ bản.
- Phạm vi: GitHub Actions cho backend/frontend khi chúng tồn tại và kiểm tra tài liệu/script.
- Không nằm trong phạm vi: CD production.
- Tiêu chí hoàn thành: workflow chạy các kiểm tra có ý nghĩa, không lộ secret.
- Kiểm thử hoặc kiểm tra bắt buộc: validate workflow và chạy CI trên nhánh thử nghiệm.
- Ticket phụ thuộc: BF-004, BF-005, BF-007.

## BF-013 — Authentication database schema (Hoàn thành)
- Mục tiêu: thiết lập schema PostgreSQL tối thiểu cho xác thực, session và refresh token theo ADR BF-010.
- Phạm vi: Flyway migration V2 cho `users`, `auth_sessions`, `refresh_tokens`, constraint, index và integration test Testcontainers.
- Không nằm trong phạm vi: API, JPA entity/repository, Spring Security, JWT, giao diện, membership và schema tenant.
- Tiêu chí hoàn thành: migration tạo đúng bảng xác thực; email chuẩn hóa và token hash là duy nhất; quan hệ user/session/token, trạng thái và thời hạn bị ràng buộc; không tạo bảng nghiệp vụ.
- Kiểm thử hoặc kiểm tra bắt buộc: `mvn clean test`, `mvn clean verify`, Flyway validate/migrate lại và kiểm tra constraint trên PostgreSQL Testcontainer.
- Ticket phụ thuộc: BF-006, BF-007, BF-010, BF-011.

> Đã xác minh trên PostgreSQL 17 Testcontainer: V1 và V2 chạy thành công, validate đạt, chạy migrate lại không có migration mới; unique email chuẩn hóa/token hash, quan hệ session-token và các constraint thời hạn được kiểm thử.

## BF-014 — User Registration và Argon2id Password Hashing (Hoàn thành)
- Mục tiêu: cho phép đăng ký user toàn cục an toàn bằng email/password.
- Phạm vi: `POST /api/v1/auth/register`, chuẩn hóa email, password policy, Argon2id, JDBC persistence, OpenAPI và test Testcontainers.
- Không nằm trong phạm vi: login, JWT, refresh token, session, logout, tenant, membership, authorization, Redis và frontend.
- Tiêu chí hoàn thành: chỉ một user được tạo cho mỗi normalized email; password chỉ được lưu dưới dạng Argon2id hash; response và lỗi không lộ dữ liệu nhạy cảm.
- Kiểm thử hoặc kiểm tra bắt buộc: `mvn clean test`, `mvn clean verify`, kiểm tra hash, duplicate email, concurrent registration và không tạo session/token.
- Ticket phụ thuộc: BF-008, BF-009, BF-010, BF-013.

> Đã xác minh với PostgreSQL 17 Testcontainer: endpoint trả `201`, email được chuẩn hóa, hash Argon2id được kiểm tra, duplicate/concurrent registration trả đúng một `201` và một `409`, không tạo `auth_sessions` hoặc `refresh_tokens`.

## BF-015 — User Login và Authentication Session (Hoàn thành)

- Mục tiêu: xác thực user và cấp session/token theo ADR BF-010.
- Phạm vi: CSRF endpoint, login, Argon2id verify, JWT RS256, session PostgreSQL, refresh cookie HttpOnly và Testcontainers.
- Không nằm trong phạm vi: refresh rotation, logout, authorization, rate limiting và frontend.
- Tiêu chí hoàn thành: login yêu cầu CSRF, không trả/lưu raw refresh token và các test Maven đạt.
- Kiểm thử: `clean test`, `clean verify`, Testcontainers login/CSRF/session/token hash và `git diff --check`.
- Ticket phụ thuộc: BF-010, BF-013, BF-014.

> Runtime smoke test local với RSA PEM do người dùng tự thực hiện; ticket không tạo hoặc commit key.

## BF-017/018/019 — Session, Logout và Refresh Rotation (Completed)

- Đã hoàn thành refresh rotation một lần, phát hiện reuse, logout hiện tại, logout-all bằng JWT principal, khóa PostgreSQL và bảo vệ CSRF.
- Không nằm trong phạm vi: frontend, rate limiting, MFA hoặc thay đổi migration đã áp dụng.

## BF-020 — Forgot/Reset Password (Hoàn thành)

- Mục tiêu: khôi phục mật khẩu bằng token một lần mà không làm lộ account.
- Phạm vi: forgot/reset API, SHA-256 token hash, TTL, notification port, Flyway V3, revoke authentication artifacts và PostgreSQL Testcontainers.
- Không nằm trong phạm vi: email provider, frontend, MFA hoặc thay đổi migration cũ.
- Tiêu chí hoàn thành: raw token chỉ đi qua notification port; reset atomic, dùng một lần, chống race và rollback đầy đủ; mọi token không hợp lệ dùng cùng public error.
- Kiểm thử: enumeration, storage, expiration/reuse, concurrency, rollback, CSRF và migration constraint/index.
- Ticket phụ thuộc: BF-014, BF-017/018/019.

## BF-021 — Rate Limiting & Security Hardening (Hoàn thành)

- Mục tiêu: bảo vệ authentication endpoint trước abuse mà không biến Redis thành nguồn sự thật xác thực.
- Phạm vi: Redis atomic counter/TTL theo IP và account, privacy-safe key, trusted proxy, `429 Retry-After` và fail-closed.
- Không nằm trong phạm vi: production proxy deployment, CAPTCHA, WAF hoặc in-memory limiter.
- Tiêu chí hoàn thành: key không chứa identifier raw; concurrency chính xác; không tin forwarded header mặc định; Redis failure có policy rõ ràng.
- Kiểm thử: Redis Testcontainers cho TTL/atomicity/concurrency/key privacy/failure, cùng regression JWT/rotation/CSRF.
- Ticket phụ thuộc: BF-020 và Redis local.

## BF-022 — Authentication Final Audit (Hoàn thành)

- Mục tiêu: audit cuối chuỗi BF-013 đến BF-021 theo invariant bảo mật và dữ liệu.
- Phạm vi: test coverage, JWT/CSRF, transaction/concurrency, token storage, Redis key privacy và Flyway V3.
- Không nằm trong phạm vi: tính năng authentication mới, frontend, MFA hoặc authorization nghiệp vụ.
- Tiêu chí hoàn thành: full `clean verify` không fail/error/skip bất thường; không có secret; migration/constraint/index và regression bảo mật đều đạt.
- Kiểm thử: toàn bộ Maven verification, `git diff --check` và secret scan repository.
- Ticket phụ thuộc: BF-013 đến BF-021.

## BF-023 — Business & Business Membership Database Schema (Hoàn thành)

- Mục tiêu: tạo schema shared-schema cho business tenant và membership của user global.
- Phạm vi: Flyway V4, business/membership model, constraint/index/FK và PostgreSQL Testcontainers test.
- Không nằm trong phạm vi: API, invitation, tenant context, authorization filter, role management hoặc bảng booking.
- Tiêu chí hoàn thành: slug, type/status, FK, membership unique, delete behavior và lookup index được database thực thi.
- Kiểm thử: migration database sạch, many-to-many membership, duplicate/FK/status rejection, index và `ON DELETE RESTRICT`.

## BF-024 — Create Business API & Owner Membership (Hoàn thành)

- Mục tiêu: người dùng đã xác thực tạo business và membership `OWNER/ACTIVE` ban đầu trong cùng transaction.
- Phạm vi: JWT/CSRF cho `POST /api/v1/businesses`, validation, JDBC transaction, map slug unique sang `409` và Testcontainers integration test.
- Không nằm trong phạm vi: quản lý membership, invitation, chuyển owner, tenant context nghiệp vụ hoặc authorization theo role.
- Tiêu chí hoàn thành: business và owner membership cùng commit/rollback, slug cạnh tranh chỉ một `201`, tenant ID khớp business ID và full `clean verify` pass.
- Kiểm thử: JWT/CSRF, validation, persistence, rollback PostgreSQL, unique/concurrency và regression authentication/migration.

## BF-025 — Xem business của user hiện tại (Hoàn thành)

- Mục tiêu: cho user đã xác thực xem các business active mà họ có membership active.
- Phạm vi: `GET /api/v1/businesses`, `GET /api/v1/businesses/{businessId}`, JDBC tenant filtering, response membership hiện tại và Testcontainers.
- Không nằm trong phạm vi: pagination, update/delete business, membership management, tenant context toàn cục và authorization theo role.
- Tiêu chí hoàn thành: không đọc chéo tenant, inactive business/membership bị ẩn, UUID sai là `400`, access không hợp lệ/không thuộc user là `404` trung tính và full `clean verify` pass.
- Kiểm thử: unit service, PostgreSQL Testcontainers cho isolation/status/empty list, JWT/UUID error và regression BF-023/BF-024/authentication.
- Ticket phụ thuộc: BF-011, BF-013, BF-022.

## BF-026 — Tenant authorization dùng chung (Hoàn thành)

- Mục tiêu: resolve user JWT và membership/business active từ PostgreSQL cho các service tenant-scoped.
- Phạm vi: `TenantAuthorizationService`, JDBC lookup trực tiếp và mapping access ngoài tenant sang `404`.
- Không nằm trong phạm vi: tenant switch, ThreadLocal context, header tenant, migration hoặc API membership.
- Tiêu chí hoàn thành: membership revoke/suspend và business inactive mất quyền ở request sau dù JWT còn hạn.

## BF-027 — Role authorization matrix (Hoàn thành)

- Mục tiêu: áp dụng permission tường minh cho `OWNER`, `ADMIN`, `STAFF` theo ADR 0002.
- Phạm vi: enum permission, matrix role, `403` khi membership active không đủ permission và test matrix.
- Không nằm trong phạm vi: endpoint quản trị business hoặc membership mới.
- Tiêu chí hoàn thành: không dùng so sánh ordinal role; permission deny không bị map thành `404` hoặc `401`.

## BF-028 — Tenant security review và tài liệu Giai đoạn 3 (Hoàn thành)

- Mục tiêu: kiểm thử isolation/revocation và chuẩn hóa tài liệu endpoint–role.
- Phạm vi: Testcontainers security regression, tài liệu authorization và bảng matrix.
- Không nằm trong phạm vi: Redis authorization, frontend, invitation, tenant switch hoặc schema mới.
- Tiêu chí hoàn thành: full `clean verify` pass, không cross-tenant read và tài liệu nêu rõ `401`/`403`/`404`.

## BF-029 — Cập nhật cấu hình business (Hoàn thành)

- Mục tiêu: cho `OWNER` hoặc `ADMIN` cập nhật từng phần cấu hình của business active.
- Phạm vi: `PATCH /api/v1/businesses/{businessId}`, JDBC transaction, tenant authorization `BUSINESS_CONFIGURATION_MANAGE`, validation, slug unique, Flyway V5 và PostgreSQL Testcontainers.
- Không nằm trong phạm vi: đổi status/owner/membership, branch, employee, service, invitation, frontend hoặc tenant context toàn cục.
- Tiêu chí hoàn thành: partial update không làm mất dữ liệu cũ; `STAFF` là `403`; tenant/business/membership inactive là `404`; slug trùng là `409`; full `clean verify` pass.
- Kiểm thử: unit validation, Testcontainers cho OWNER/ADMIN, STAFF/tenant khác/inactive, partial update, validation, JWT/CSRF và slug conflict.

## BF-030 — Schema và CRUD/archive chi nhánh (Hoàn thành)

- Mục tiêu: tạo và quản lý branch business bằng JDBC, soft archive và tenant authorization.
- Phạm vi: Flyway V6, endpoint branch CRUD/archive, `BRANCH_VIEW`/`BRANCH_MANAGE`, validation và PostgreSQL Testcontainers.
- Không nằm trong phạm vi: employee, service, lịch, booking, public API, Redis, frontend và khôi phục branch archive.
- Tiêu chí hoàn thành: unique code theo tenant, archive không hard delete, isolation và CSRF/JWT đúng, full `clean verify` pass.

---

## Cập nhật trạng thái hiện tại — Giai đoạn business catalog

Các ticket dưới đây phản ánh source và kết quả regression mới nhất. Full Maven `clean verify` với PostgreSQL Testcontainers đã chạy thành công trong BF-038.

## BF-030 — Schema và CRUD/archive chi nhánh (Hoàn thành)

- Đã có Flyway V6, module Branch CRUD/archive, JWT/CSRF và tenant authorization.
- Flyway V6, CRUD/archive, tenant authorization và regression đều đã được xác minh.

## BF-031 — Employee CRUD + archive (Hoàn thành)

- Đã có Flyway V7, module Employee, archive mềm, code unique theo tenant và permission Employee.
- CRUD/archive, code unique theo tenant và permission Employee đã qua full regression.

## BF-032 — Business member và liên kết user–employee (Hoàn thành)

- Flyway V8 đã bổ sung `employees.user_id`, FK tới `users` và unique `(tenant_id, user_id)`.
- API member/role/revoke/link đã được xác minh bằng PostgreSQL Testcontainers; BF-038 sửa lỗi link thành công nhưng service vẫn trả `404`.

## BF-033 — Gán Employee vào nhiều Branch (Hoàn thành)

- Đã có bảng assignment tenant-scoped trong V7 và API gán/bỏ gán employee–branch.
- Assignment tenant-scoped, trạng thái ACTIVE và isolation đã qua full regression.

## BF-034 — Service CRUD + archive (Hoàn thành)

- Flyway V9 và module Service catalog/soft archive đã được thêm.
- Service CRUD/archive, validation, role và tenant isolation đã được xác minh trên PostgreSQL Testcontainers.

## BF-035 — Gán Service cho Branch và Employee (Hoàn thành)

- V9 có `branch_services`, `employee_services`, composite FK tenant và rule branch active chung cho employee-service.
- Assignment branch/employee, composite FK tenant và rule branch ACTIVE chung đã qua regression.

## BF-036 — Public Catalog API theo business slug (Hoàn thành)

- Đã có public endpoint theo slug, module controller/service/repository, OpenAPI, tài liệu và unit test boundary/privacy.
- Public endpoint, filter, ACTIVE-only và private-data boundary đã được xác minh bằng Testcontainers và full `clean verify`.

## BF-037 — Frontend App Router, authentication và catalog integration (Hoàn thành về implementation)

- App Router, auth/CSRF/refresh, protected dashboard, BusinessProvider, CatalogProvider và Public Catalog đã nối backend thật.
- Dashboard không dùng mock làm nguồn dữ liệu cho Business/Branch/Employee/Member/Service; landing page vẫn giữ nội dung demo tĩnh có chủ đích.
- TypeScript, production build và ESLint được kiểm tra trong BF-038. Browser end-to-end đầy đủ vẫn là mục kiểm tra thủ công riêng.

## BF-038 — Final Security Regression, Integration Test, Tooling & Documentation (Partial)

- Backend unit/integration regression, tenant/role/catalog/Public Catalog security, frontend typecheck/build/lint và tài liệu tổng kết Giai đoạn 4 đã được thực hiện.
- Trạng thái giữ `Partial` vì chưa chạy trọn chuỗi browser smoke test (session restore, tenant switch và toàn bộ CRUD/assignment trên UI) bằng browser automation hoặc kiểm thử thủ công có ghi nhận.
- Ticket tiếp theo sau khi hoàn tất smoke test là Giai đoạn 5 — Schedule & Availability; BF-038 không triển khai chức năng Giai đoạn 5.

## BF-039 — Schedule Management Foundation (Hoàn thành)

- Mục tiêu: cung cấp nền quản lý lịch làm việc local theo employee và branch cho Giai đoạn 5.
- Phạm vi: Flyway V10, working schedule rules, breaks, exceptions, Spring JDBC, REST/OpenAPI, tenant isolation và role authorization.
- Quy tắc chính: employee phải được gán vào branch ACTIVE; split shift được phép; interval dùng `[start, end)`; rule/break overlap trả `409`; ngày dùng `LocalDate`, giờ dùng `LocalTime`.
- Authorization: `OWNER`/`ADMIN` được quản lý; `STAFF` chỉ được xem; membership/business không active hoặc resource ngoài tenant được ẩn bằng `404`.
- Kiểm thử: unit validation và PostgreSQL Testcontainers cho CRUD, overlap, effective date, assignment, composite FK, tenant isolation, role và revocation; `mvn test` và `clean verify` đều PASS.
- Không nằm trong phạm vi: Availability Engine, public slot API, frontend schedule UI và booking.
- Ticket phụ thuộc: BF-030, BF-031, BF-033, BF-038.
- Ticket tiếp theo: BF-040 — Pure Availability Engine.

## BF-040 — Pure Availability Engine (Hoàn thành)

- Mục tiêu: tính deterministic các slot có thể đặt trong một ngày từ schedule BF-039 và dữ liệu busy đã được load sẵn.
- Phạm vi: half-open interval, working rule filtering, break/TIME_OFF/busy subtraction, WORKING_OVERRIDE, duration và buffer, lead time, booking horizon, configurable slot step, `Clock`, `ZoneId` và DST.
- Kiến trúc: pure Java domain logic trong `schedules.availability`; không Spring, HTTP, Security, JDBC, repository, PostgreSQL hoặc Redis.
- Exception precedence: full-day `TIME_OFF` thắng toàn bộ; nếu không có full-day off thì normal schedule và `WORKING_OVERRIDE` được hợp nhất trước khi trừ partial `TIME_OFF`.
- Time policy: output là customer-visible start/end dạng `Instant`; buffer chỉ dùng cho occupied interval. DST gap dịch tới thời điểm hợp lệ kế tiếp; DST overlap chọn offset sớm hơn và không sinh slot mơ hồ hai lần.
- Horizon: ngày hiện tại đến `today + maxBookingAdvanceDays` được tính inclusive theo timezone của branch; lead time được làm tròn tự nhiên lên slot grid.
- Kiểm thử: 20 pure unit tests cho interval/engine và full Maven/Testcontainers regression đều PASS.
- Không nằm trong phạm vi: availability repository/service/controller, Public Availability API, frontend, booking, concurrency và cache.
- Ticket phụ thuộc: BF-039.
- Ticket tiếp theo: BF-041 — Availability Service + Public API.

## BF-041 — Availability Service + Public API (Hoàn thành)

- Mục tiêu: nối Schedule Management BF-039 với Pure Availability Engine BF-040 để trả slot có thể đặt qua API public.
- Phạm vi: resource validation tenant-safe, JDBC batch query, application orchestration, employee filter/aggregation, timezone, duration/buffer, lead time/horizon, busy interval boundary, REST/OpenAPI và Testcontainers.
- Endpoint: `GET /api/v1/public/businesses/{slug}/availability` với `branchId`, `serviceId`, `date` bắt buộc và `employeeId` tùy chọn.
- Query strategy: một resource lookup, một eligible-employee query và tối đa ba batch query cho rules/breaks/exceptions; không query theo employee hoặc theo slot.
- Public safety: chỉ business/branch/service/employee `ACTIVE` và assignment hợp lệ; resource sai tenant/inactive được ẩn bằng `404`; response không chứa tenant, membership hay schedule internals.
- Policy: timezone lấy từ Branch; slot step cấu hình mặc định 15 phút; lead time hiện là application-level config mặc định 0; horizon lấy từ `Business.maxBookingAdvanceDays`; không có slot trả `200` với `slots=[]`.
- Kiểm thử: unit orchestration, pure engine regression và PostgreSQL Testcontainers cho public access, aggregation/filter, schedule/break/exception, buffer, lead time, horizon, timezone, tenant/resource hiding.
- Không nằm trong phạm vi: frontend, booking, booking concurrency, booking-backed busy intervals và cache.
- Ticket phụ thuộc: BF-039, BF-040.
- Ticket tiếp theo: BF-042 — Dashboard Schedule UI + Public Availability UI.

## BF-042 — Dashboard Schedule UI + Public Availability UI (Partial)

- Mục tiêu: nối frontend Next.js với Schedule API BF-039 và Public Availability API BF-041 mà không triển khai booking.
- Dashboard: employee có deep-link `/dashboard/employees/{employeeId}/schedule`; trang hiển thị branch, working rules, breaks và exceptions từ backend thật.
- Mutation: OWNER/ADMIN có form create/update/delete; STAFF chỉ đọc. Form dùng `noValidate`, validation React và hiển thị Problem Detail/`SCHEDULE_CONFLICT` tại chỗ; mutation thành công luôn refetch server state.
- Public flow: business slug → branch → service → employee tùy chọn → date → một request availability. Khi chọn tất cả nhân viên, frontend không gọi API theo từng employee.
- Slot UI: format theo timezone response, hỗ trợ loading/empty/error và selected-slot state; CTA booking bị vô hiệu hóa rõ ràng.
- Kiểm tra tự động: TypeScript, ESLint, Next.js production build và `git diff --check` PASS.
- Trạng thái `Partial`: chưa thực hiện browser smoke Dashboard/Public bằng tài khoản và dữ liệu local thật, do đó chưa xác nhận persistence/reload và network flow qua trình duyệt.
- Không nằm trong phạm vi: booking, giữ chỗ, concurrency, payment, booking-backed busy intervals và cache.
- Ticket phụ thuộc: BF-039, BF-041.
- Bước tiếp theo sau browser smoke: BF-043 — Stage 5 Regression, Browser E2E & Documentation.

## BF-044 — Booking Domain + Schema + State Machine (Hoàn thành)

- Mục tiêu: tạo nền dữ liệu và domain cho booking trước khi mở API tạo booking và xử lý cạnh tranh.
- Phạm vi: Flyway V11 với `bookings`, `booking_items`, `booking_status_history`; aggregate typed bằng UUID/Instant/BigDecimal; state machine; snapshot dịch vụ; Spring JDBC repository và transaction boundary.
- Trạng thái ban đầu: `PENDING_CONFIRMATION` vì BF-044 chưa có payment flow; booking mới có hold expiry rõ ràng. `PENDING_PAYMENT` vẫn thuộc state machine cho luồng payment sau.
- Slot-occupying statuses: `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `CONFIRMED`, `IN_PROGRESS`; policy chỉ định nghĩa tại `BookingStatus.occupiesSlot()`.
- Tenant integrity: composite FK chặn booking/item tham chiếu branch, employee hoặc service thuộc tenant khác; mọi lookup repository scope bằng `tenant_id + booking_id`.
- Transaction: booking, items và initial history được ghi cùng transaction; status update dùng expected-status conditional update và history cùng transaction.
- Kiểm thử: unit domain/state machine và PostgreSQL Testcontainers cho migrate database sạch, snapshot độc lập, Instant round-trip, tenant FK, rollback creation/transition, indexes và absence của exclusion constraint.
- Không nằm trong phạm vi: public Create Booking API, availability re-check, exclusion constraint, idempotency, concurrent booking, expiry worker, cancel/reschedule, frontend, payment và notification.
- Ticket phụ thuộc: BF-039, BF-040, BF-041.
- Ticket tiếp theo: BF-045 — Create Booking + Exclusion Constraint + Idempotency.

## BF-045 — Create Booking + PostgreSQL Concurrency Guard + Idempotency (Hoàn thành)

- Mục tiêu: tạo booking public từ một slot cụ thể mà backend tự xác minh/tính lại toàn bộ dữ liệu và PostgreSQL là lớp chống đặt trùng cuối cùng.
- Endpoint: `POST /api/v1/public/businesses/{slug}/bookings`; guest không cần JWT nhưng mutation bắt buộc CSRF và `Idempotency-Key`.
- Phạm vi: Flyway V12, GiST exclusion constraint `[start,end)`, `booking_idempotency_keys`, server-side availability re-check, service snapshot, `SLOT_UNAVAILABLE`, booking-backed `BusyIntervalProvider`, OpenAPI và concurrency tests.
- Occupied range: `bookings.start_at/end_at` lưu khoảng nhân viên bị chiếm gồm buffer; response trả khoảng service khách nhìn thấy được dựng từ snapshot.
- Idempotency: key scope theo tenant, fingerprint SHA-256 từ request semantic đã normalize; key/payload giống trả cùng booking, payload khác trả `409 IDEMPOTENCY_KEY_REUSED`; key và booking commit/rollback trong cùng transaction.
- Kiểm thử: PostgreSQL 17 Testcontainers xác minh constraint thật, 20 request cùng slot chỉ một thành công, retry cùng key chỉ một row, boundary chạm nhau hợp lệ, employee khác không xung đột và Public Availability trừ active booking.
- Không nằm trong phạm vi: expiry worker, cancel/reschedule, auto employee, frontend Booking UI, payment, notification và cache.
- Ticket phụ thuộc: BF-039, BF-040, BF-041, BF-044.
- Ticket tiếp theo: BF-046 — Booking Expiry + Cancel + Atomic Reschedule.

## BF-046 — Booking Expiry + Cancel + Atomic Reschedule (Hoàn thành)

- Mục tiêu: hoàn thiện lifecycle sau create bằng expiry tự động, cancel an toàn và đổi lịch atomic.
- Phạm vi: Flyway V13, expiry index/audit reschedule, worker batch cấu hình được, `FOR UPDATE SKIP LOCKED`, customer/business cancel, server-side availability re-check có self-exclusion, reschedule audit và OpenAPI.
- Expiry: chỉ `PENDING_PAYMENT`/`PENDING_CONFIRMATION` có `expires_at <= Clock.instant()` chuyển sang `EXPIRED`; status và history cùng transaction; nhiều worker chỉ tạo một transition/history.
- Cancel: customer JWT chỉ hủy booking có `customer_user_id` của mình; OWNER/ADMIN hủy theo tenant bằng `BOOKING_MANAGE`; STAFF bị từ chối. Guest booking chưa có access token nên chưa có guest-cancel endpoint giả.
- Reschedule: giữ nguyên snapshot tên/giá/currency/duration/buffer, tính lại occupied range, loại booking hiện tại khỏi busy intervals và vẫn để exclusion constraint quyết định race cuối cùng. Lỗi target giữ nguyên booking cũ.
- Kiểm thử: fixed Clock, batch lớn hơn batch size, rollback history/audit, slot release, customer/business authorization, cross-tenant hiding, self-overlap, snapshot, break, worker/cancel/reschedule races và PostgreSQL Testcontainers.
- Không nằm trong phạm vi: auto employee, frontend Booking UI, payment, notification, refund, cache và guest booking access token.
- Ticket phụ thuộc: BF-039, BF-040, BF-041, BF-044, BF-045.
- Ticket tiếp theo: BF-047 — Auto Employee Selection + Booking Frontend.

## BF-047 — Auto Employee Selection + Booking Frontend (Đang thực hiện)

- Mục tiêu: hoàn thiện luồng public từ chọn slot đến tạo booking thật, đồng thời cho phép backend tự chọn nhân viên khi khách chọn “Tất cả nhân viên”.
- Backend: `employeeId` là tùy chọn. Nếu được gửi, flow BF-045 được giữ nguyên; nếu bỏ trống, candidate `ACTIVE` đúng tenant/branch/service được sắp theo số booking active tương lai tăng dần rồi UUID để tie-break ổn định.
- Concurrency: availability aggregate chỉ là pre-check. Mỗi candidate được insert trong savepoint; conflict từ exclusion constraint rollback riêng attempt và thử candidate kế tiếp. Hết candidate trả `409 SLOT_UNAVAILABLE`; không dùng Redis lock hoặc `synchronized`.
- Idempotency: fingerprint phản ánh lựa chọn semantic của khách, gồm cả việc `employeeId` bị bỏ trống nhưng không chứa employee do server chọn. Replay cùng key/payload trả đúng booking và employee ban đầu; payload khác trả `IDEMPOTENCY_KEY_REUSED`.
- Frontend: Public Catalog có form `noValidate`, validation nhìn thấy được, CSRF, `Idempotency-Key` ổn định theo logical submit, chống double-submit, success summary, xử lý conflict và refetch availability. Không gọi availability theo từng employee và không có Booking mock fallback.
- Kiểm thử tự động: unit/context test, PostgreSQL Testcontainers concurrency, TypeScript, ESLint và Next.js production build đã đạt trong quá trình triển khai.
- Trạng thái `Đang thực hiện`: browser smoke bắt buộc với backend/frontend và dữ liệu local thật chưa được xác nhận bằng một browser automation khả dụng trong môi trường Codex; ticket chưa được đánh dấu hoàn thành.
- Không nằm trong phạm vi: payment, notification, refund, customer booking management, cache và generic idempotency framework.
- Ticket phụ thuộc: BF-041, BF-044, BF-045, BF-046.
- Ticket tiếp theo sau khi browser smoke đạt: BF-048 — Stage 6 Final Concurrency Regression + Documentation.
