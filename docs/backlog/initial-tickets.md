# Ticket khởi đầu

> Trạng thái: **BF-001 đã hoàn thành sau kiểm tra**. Các ticket tiếp theo vẫn chưa được triển khai.

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

## BF-011 — Thiết kế multi-tenancy
- Mục tiêu: chốt chiến lược shared schema với `business_id`.
- Phạm vi: tenant context, authorization, chiến lược truy vấn và test isolation.
- Không nằm trong phạm vi: triển khai mọi module nghiệp vụ.
- Tiêu chí hoàn thành: quy tắc tenant có thể kiểm thử và được ghi nhận.
- Kiểm thử hoặc kiểm tra bắt buộc: threat/risk review và test matrix tenant isolation.
- Ticket phụ thuộc: BF-006, BF-010.

## BF-012 — CI nền tảng
- Mục tiêu: tự động hóa kiểm tra cơ bản.
- Phạm vi: GitHub Actions cho backend/frontend khi chúng tồn tại và kiểm tra tài liệu/script.
- Không nằm trong phạm vi: CD production.
- Tiêu chí hoàn thành: workflow chạy các kiểm tra có ý nghĩa, không lộ secret.
- Kiểm thử hoặc kiểm tra bắt buộc: validate workflow và chạy CI trên nhánh thử nghiệm.
- Ticket phụ thuộc: BF-004, BF-005, BF-007.
