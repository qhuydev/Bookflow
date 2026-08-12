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

## BF-030 — Schema và CRUD/archive chi nhánh (Đang thực hiện)

- Mục tiêu: tạo và quản lý branch business bằng JDBC, soft archive và tenant authorization.
- Phạm vi: Flyway V6, endpoint branch CRUD/archive, `BRANCH_VIEW`/`BRANCH_MANAGE`, validation và PostgreSQL Testcontainers.
- Không nằm trong phạm vi: employee, service, lịch, booking, public API, Redis, frontend và khôi phục branch archive.
- Tiêu chí hoàn thành: unique code theo tenant, archive không hard delete, isolation và CSRF/JWT đúng, full `clean verify` pass.

---

## Cập nhật trạng thái hiện tại — Giai đoạn business catalog

Các ticket dưới đây phản ánh đúng worktree hiện tại. Chưa ticket nào trong nhóm này được đánh dấu hoàn thành vì chưa chạy lại toàn bộ `clean verify` sau khi các thay đổi chưa commit được ghép chung.

## BF-030 — Schema và CRUD/archive chi nhánh (In progress)

- Đã có Flyway V6, module Branch CRUD/archive, JWT/CSRF và tenant authorization.
- Còn cần xác minh lại full regression cùng các migration V7–V9 trước khi chuyển Completed.

## BF-031 — Employee CRUD + archive (In progress)

- Đã có Flyway V7, module Employee, archive mềm, code unique theo tenant và permission Employee.
- Còn cần full regression chung trước khi chuyển Completed.

## BF-032 — Business member và liên kết user–employee (In progress)

- Flyway V8 đã bổ sung `employees.user_id`, FK tới `users` và unique `(tenant_id, user_id)`.
- API quản lý member, role và liên kết employee chưa được xác minh hoàn chỉnh bằng test trong worktree hiện tại.

## BF-033 — Gán Employee vào nhiều Branch (In progress)

- Đã có bảng assignment tenant-scoped trong V7 và API gán/bỏ gán employee–branch.
- Còn cần full regression chung trước khi chuyển Completed.

## BF-034 — Service CRUD + archive (In progress)

- Flyway V9 và module Service catalog/soft archive đã được thêm.
- Còn thiếu kiểm thử và xác minh đầy đủ theo acceptance criteria.

## BF-035 — Gán Service cho Branch và Employee (In progress)

- V9 có `branch_services`, `employee_services`, composite FK tenant và rule branch active chung cho employee-service.
- Còn thiếu kiểm thử và xác minh đầy đủ theo acceptance criteria.

## BF-036 — Public Catalog API theo business slug (In progress)

- Đã có public endpoint theo slug, module controller/service/repository, OpenAPI, tài liệu và unit test boundary/privacy.
- Chưa chạy Testcontainers integration test và `clean verify`; chưa được đánh dấu Completed.
