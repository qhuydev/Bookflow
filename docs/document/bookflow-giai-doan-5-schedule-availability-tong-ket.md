# Giai đoạn 5 — Schedule và Availability

## Trạng thái

BF-039 — Schedule Management Foundation: **Completed**.

BF-040 — Pure Availability Engine: **Completed**.

BF-041 — Availability Service + Public API: **Completed**.

BF-042 — Dashboard Schedule UI + Public Availability UI: **Partial** — implementation và automated frontend checks hoàn tất, browser smoke chưa chạy.

## Mục tiêu BF-039

BF-039 cung cấp dữ liệu và API quản lý lịch làm việc local theo cặp employee–branch. PostgreSQL là nguồn sự thật cho tenant isolation, quan hệ employee–branch và các constraint cơ bản. Ticket chưa tính slot khả dụng và chưa xử lý booking.

## Schema V10

### `working_schedule_rules`

- Khóa tenant: `tenant_id`.
- Phạm vi: `branch_id`, `employee_id`, `weekday`.
- Khoảng local: `start_local_time`, `end_local_time`.
- Hiệu lực: `effective_from`, `effective_to` nullable.
- Composite FK `(tenant_id, employee_id, branch_id)` tới `employee_branch_assignments` ngăn liên kết chéo tenant hoặc employee chưa được gán branch.

### `schedule_breaks`

- Thuộc một working rule qua composite FK `(tenant_id, schedule_rule_id)`.
- Lưu khoảng nghỉ local và bị xóa cascade khi rule bị xóa.

### `schedule_exceptions`

- Thuộc employee–branch đã được gán.
- `TIME_OFF`: có thể cả ngày (không có start/end) hoặc một phần ngày.
- `WORKING_OVERRIDE`: bắt buộc có start/end.
- `note` là tùy chọn, tối đa 500 ký tự.

Mọi index truy vấn chính đều bắt đầu bằng `tenant_id`.

## API

Base tenant/employee:

```text
/api/v1/businesses/{businessId}/employees/{employeeId}
```

Working rules:

```text
POST   /schedule-rules
GET    /schedule-rules
GET    /schedule-rules/{ruleId}
PATCH  /schedule-rules/{ruleId}
DELETE /schedule-rules/{ruleId}
```

Breaks:

```text
POST   /schedule-rules/{ruleId}/breaks
GET    /schedule-rules/{ruleId}/breaks
GET    /schedule-rules/{ruleId}/breaks/{breakId}
PATCH  /schedule-rules/{ruleId}/breaks/{breakId}
DELETE /schedule-rules/{ruleId}/breaks/{breakId}
```

Exceptions:

```text
POST   /schedule-exceptions
GET    /schedule-exceptions
GET    /schedule-exceptions/{exceptionId}
PATCH  /schedule-exceptions/{exceptionId}
DELETE /schedule-exceptions/{exceptionId}
```

Ví dụ working rule:

```json
{
  "branchId": "00000000-0000-0000-0000-000000000001",
  "weekday": "MONDAY",
  "startLocalTime": "09:00",
  "endLocalTime": "18:00",
  "effectiveFrom": "2026-08-01",
  "effectiveTo": null
}
```

Ví dụ full-day time off:

```json
{
  "branchId": "00000000-0000-0000-0000-000000000001",
  "date": "2026-08-20",
  "type": "TIME_OFF",
  "startLocalTime": null,
  "endLocalTime": null,
  "note": "Nghỉ phép"
}
```

## Overlap policy

Khoảng thời gian dùng quy ước half-open `[start, end)`. Vì vậy `09:00–12:00` và `12:00–18:00` chạm biên nhưng không overlap. Split shift được phép.

Working rule conflict chỉ xảy ra khi đồng thời trùng employee, branch, weekday, effective date range và local time interval. Repository dùng PostgreSQL transaction advisory lock theo tenant/employee/branch/weekday trước khi kiểm tra và ghi, tránh hai mutation cùng vượt qua kiểm tra overlap. Break cùng rule cũng không được overlap và phải nằm hoàn toàn trong working rule.

## Timezone model

BF-039 lưu ngày bằng `LocalDate` và giờ bằng `LocalTime`; không chuyển schedule sang UTC. Timezone tiếp tục lấy từ Branch/Business. BF-040 kết hợp `LocalDate + LocalTime + ZoneId` thành `Instant` trong pure engine theo policy DST được mô tả bên dưới.

## Tenant isolation và authorization

- Mọi repository query scope bằng `tenant_id` và resource ID.
- Composite FK ngăn employee/branch khác tenant ở database.
- `OWNER`, `ADMIN`: `SCHEDULE_VIEW`, `SCHEDULE_MANAGE`.
- `STAFF`: chỉ `SCHEDULE_VIEW`.
- Membership bị revoke/suspend hoặc business inactive mất quyền ngay ở request tiếp theo.
- Resource ngoài tenant hoặc employee/branch không active được ẩn bằng `404`; thiếu quyền mutation trả `403`.

## Error contract

- `400`: body/date/time sai, interval không hợp lệ, break nằm ngoài rule.
- `401`: thiếu hoặc sai JWT.
- `403`: membership active nhưng role thiếu quyền.
- `404`: business/resource/assignment không tồn tại hoặc bị tenant scope ẩn.
- `409`: working rule hoặc break overlap.

Response lỗi dùng Problem Detail hiện có và không lộ SQL, stack trace hoặc tenant internals.

## Kiểm thử đã chạy

- Unit validation cho time/date range, break containment và exception semantics.
- PostgreSQL 17 Testcontainers cho migration database sạch, CRUD ba nhóm resource, split shift, touching boundary, overlap, effective range, employee–branch assignment, composite FK, tenant isolation, OWNER/ADMIN/STAFF và membership revocation.
- `mvn test`: PASS.
- `mvn clean verify`: PASS.
- `git diff --check`: chạy ở bước kiểm tra cuối của BF-039.

## Lỗi đã phát hiện trong quá trình triển khai

Lần integration đầu phát hiện JDBC cố map kết quả `pg_advisory_xact_lock` (kiểu PostgreSQL `void`) sang `Long`. Repository đã được sửa để chỉ thực thi callback khóa, sau đó suite BF-039 và full regression đều PASS.

## BF-040 — Pure Availability Engine

### Kiến trúc

Engine nằm trong package `com.bookflow.schedules.availability` và là pure Java domain logic. Nó nhận toàn bộ working rules, breaks, exceptions và busy intervals đã được load sẵn; không gọi Spring, HTTP, Security, JDBC, repository, PostgreSQL hoặc Redis.

Các core type:

- `TimeInterval`: interval `Instant` bất biến theo `[start, end)`.
- `AvailabilityInput`: ngày, timezone, branch/employee scope, schedule input, duration/buffer, lead time, horizon và slot step.
- `AvailabilitySlot`: customer-visible start/end; không bao gồm buffer trong output.
- `AvailabilityResult`: ngày, timezone và danh sách slot ổn định.
- `AvailabilityEngine`: pipeline normalize, subtract và generate slot; nhận `Clock` qua constructor.

### Interval và pipeline

`TimeInterval` cung cấp `overlaps`, `contains`, `intersection`, `subtract`, `merge` và `subtractMany`. Touching boundaries không overlap. Intervals chỉ được sort/normalize theo từng bước; engine không iterate từng giây/phút và không truy cập external state trong vòng lặp.

Pipeline:

1. Lọc working rule theo branch, employee, weekday và effective date.
2. Chuyển `LocalDate + LocalTime + ZoneId` thành `Instant`.
3. Trừ break khỏi đúng rule.
4. Thêm `WORKING_OVERRIDE`.
5. Trừ partial `TIME_OFF`; full-day `TIME_OFF` trả zero slot.
6. Trừ `busyIntervals` do caller cung cấp.
7. Sinh candidate theo slot grid cấu hình và chỉ giữ occupied interval vừa hoàn toàn trong free interval.

Engine tự lọc schedule theo branch/employee được request. BF-041 chịu trách nhiệm load đúng tenant-scoped data; `busyIntervals` chỉ là input abstraction, chưa có Booking repository/domain.

### Exception precedence

Chính sách an toàn và deterministic:

- Full-day `TIME_OFF` có ưu tiên cao nhất và loại toàn bộ availability của ngày, kể cả override.
- Nếu không có full-day off, normal schedule và `WORKING_OVERRIDE` được hợp nhất.
- Partial `TIME_OFF` được trừ sau cùng khỏi tập working intervals đó.

### Duration và buffers

Với customer start `S`, service duration `D`, buffer before `B1` và buffer after `B2`, resource phải rảnh trong:

```text
[S - B1, S + D + B2)
```

Slot output vẫn là `[S, S + D)` để buffer không làm thay đổi giờ khách nhìn thấy.

### Slot step, lead time và horizon

- Slot step là input `Duration`; mặc định sản phẩm có thể truyền 15 phút nhưng engine không hard-code.
- Grid neo vào đầu ngày local. Earliest allowed instant là `Clock.instant() + leadTime`; candidate đầu tiên trên grid không sớm hơn mốc này được chọn.
- Horizon tính theo ngày local của `ZoneId`: ngày quá khứ và ngày sau `today + maxBookingAdvanceDays` trả zero slot; đúng ngày biên được phép.

### Timezone và DST

Engine không dùng fixed UTC offset. Mỗi local boundary được resolve bằng `ZoneRules`:

- DST gap: dịch tới thời điểm local hợp lệ đầu tiên sau transition; candidate trùng instant được loại.
- DST overlap: chọn offset sớm hơn; không sinh hai slot cho cùng local time mơ hồ.

Policy được test bằng `America/New_York` cho cả spring-forward và fall-back. `Asia/Ho_Chi_Minh` tiếp tục hoạt động theo offset thực tế của `ZoneId`.

### Deterministic tests

- 18 test `AvailabilityEngineTest` bao phủ normal/split shift, breaks, duration, buffers, full/partial time off, override, busy interval, touching boundary, lead-time rounding, horizon inclusive, multiple unsorted intervals, empty schedule, branch filtering, fixed `Clock` và DST.
- 2 test `TimeIntervalTest` kiểm tra half-open semantics, subtraction và deterministic merge.
- `mvn test`: 56 tests PASS.
- `mvn clean verify`: full unit và PostgreSQL Testcontainers regression PASS; BF-039 không regression.

### Known limitations

- Engine tính một ngày cho một employee + branch mỗi lần gọi.
- Caller phải cung cấp busy intervals và catalog/schedule data; chưa có orchestration/query layer.
- DST overlap cố ý chọn một occurrence sớm hơn thay vì trả hai slot cùng local label.
- Chưa có API/public formatting hoặc cache.

## BF-041 — Availability Service + Public API

### Request flow và boundary

```text
PublicAvailabilityController
  → AvailabilityQueryService
  → AvailabilityQueryRepository / BusyIntervalProvider
  → AvailabilityEngine
```

Controller chỉ bind path/query, public DTO và OpenAPI. Query service xác thực relationship public, build `AvailabilityInput`, gọi engine theo từng eligible employee rồi aggregate. Repository chỉ chứa Spring JDBC; BF-040 vẫn pure và không có Spring/JDBC/HTTP dependency.

Endpoint là `GET /api/v1/public/businesses/{slug}/availability`; `branchId`, `serviceId`, `date` bắt buộc, `employeeId` tùy chọn. Response gồm `date`, `timeZone`, `branchId`, `serviceId` và các slot `start/end/employeeIds` đã sort, không lộ tenant hoặc schedule internals.

### Query strategy và eligible employees

- Một join lookup xác thực business, branch, service active và service–branch assignment.
- Một query lấy toàn bộ employee active có cả employee–branch và employee–service assignment; employee filter được áp dụng ngay trong SQL nếu có.
- Một query rules đã giới hạn tenant, branch, employee set, weekday và effective date.
- Một query breaks theo toàn bộ rule ID và một query exceptions theo employee set/date.
- Không query theo employee, working rule hoặc slot; số query bounded cho mỗi request.

Resource sai tenant, inactive hoặc relationship không hợp lệ trả `404`. Resource hợp lệ nhưng không có eligible employee hay availability trả `200` với `slots=[]`.

### Time policy và mapping

- Timezone lấy trực tiếp từ Branch; query chỉ chọn branch active cùng business.
- Duration, buffer before/after lấy từ Service và map sang engine, không tính lại trong service layer.
- Slot step là cấu hình tập trung `bookflow.availability.slot-step-minutes`, mặc định 15.
- Lead time là `bookflow.availability.default-lead-time-minutes`, mặc định 0; đây là policy application-level tạm thời vì business schema chưa có field tương ứng.
- Horizon lấy từ `businesses.max_booking_advance_days`; inclusive policy vẫn do BF-040 quyết định.
- `LocalDate`, `LocalTime`, `ZoneId` được map sang `Instant` bởi engine; response chuyển lại ISO offset datetime kèm `timeZone`.

### Busy intervals, aggregation và public safety

`BusyIntervalProvider` là boundary duy nhất cho dữ liệu bận. Adapter hiện tại chủ động trả empty vì Booking domain chưa tồn tại; phase Booking sau sẽ cung cấp adapter từ active bookings mà không thay đổi controller/engine.

Khi có `employeeId`, employee phải active và đủ assignment, mọi slot chỉ chứa ID đó. Khi bỏ filter, service tính từng employee từ cùng batch data và gộp các slot có cùng start/end; `employeeIds` được sort deterministic và không có slot trùng.

API public không cần JWT/CSRF, nhưng chỉ đọc dữ liệu active và không trả tenant ID, membership, user ID, private employee fields, rule/break/exception ID hoặc audit fields. Problem Detail hiện tại xử lý `400` cho query sai và `404` tenant-safe.

### Kiểm thử BF-041

- Unit test orchestration cho slug normalization, invalid relationship và empty aggregate hợp lệ.
- PostgreSQL Testcontainers cho public access, normal/split schedule, break, partial/full-day `TIME_OFF`, `WORKING_OVERRIDE`, duration/buffer, lead time, inclusive horizon, branch timezone, employee filter/aggregation và tenant/resource hiding.
- BF-039 API/Testcontainers và 20 pure BF-040 tests tiếp tục thuộc full regression.

## BF-042 — Dashboard Schedule UI + Public Availability UI

### Frontend architecture và Schedule integration

Schedule API có boundary riêng tại `lib/api/schedules.ts`; Public Availability dùng `lib/api/availability.ts`. Protected schedule requests tiếp tục đi qua `protectedRequest`, Bearer access token in-memory, CSRF và retry 401 đúng một lần. Public availability dùng GET không JWT/CSRF.

Mỗi employee có action **Lịch làm việc** dẫn tới deep-link:

```text
/dashboard/employees/{employeeId}/schedule
```

Trang lấy employee, assigned branches và role từ server-backed providers hiện tại. Rules, exceptions và breaks được load từ backend; sau mọi mutation thành công, toàn bộ schedule state được refetch. Resource key business–employee ngăn dữ liệu tenant/employee cũ hiển thị khi chuyển business hoặc deep-link.

OWNER và ADMIN thấy controls thêm/sửa/xóa. STAFF vẫn mở được trang và xem dữ liệu nhưng không thấy mutation controls; backend BF-039 tiếp tục là lớp authorization cuối cùng.

### Working rule, break và exception UI

- Working rule form: branch hiện tại, weekday đúng enum backend, start/end, effective from/to tùy chọn; split shift không bị frontend chặn.
- Break form: start/end, backend quyết định containment và overlap.
- Exception form: `TIME_OFF` full-day/partial và `WORKING_OVERRIDE`; full-day gửi start/end `null`, optional note rỗng gửi `null`.
- Tất cả form dùng `noValidate`, validation React hiển thị rõ; `409 SCHEDULE_CONFLICT`, `403`, `404` và Problem Detail khác giữ form mở.

### Public Availability flow

Public Catalog giữ visual hiện tại và mở rộng thành:

```text
Branch → Service → Employee tùy chọn → Date → Available Slots
```

Đổi branch reset service/employee/slots; đổi service reset employee/slots. Lựa chọn **Tất cả nhân viên** bỏ `employeeId` khỏi request và chỉ gọi một endpoint aggregate BF-041. Frontend không tính duration, buffer, break, exception, lead time hoặc horizon.

Slot dùng `start/end` từ backend và format bằng `timeZone` response. Với aggregate slot, UI có thể hiển thị số employee nhưng không lộ UUID. Người dùng có thể chọn slot để xem start–end; CTA tiếp tục bị disable và không tạo booking hay giữ chỗ.

Loading, empty `200 slots=[]`, Problem Detail và selection-invalid `404` có state riêng. Dashboard Public link tiếp tục dùng `selectedBusiness.slug`, không hard-code slug.

### Verification BF-042

- TypeScript strict: PASS.
- ESLint: PASS.
- Next.js production build: PASS, gồm route động schedule và public slug route.
- `git diff --check`: chạy ở bước kiểm tra cuối.
- Browser smoke Dashboard/Public: chưa chạy trong phiên này; BF-042 giữ `Partial` và không tuyên bố persistence/network/browser PASS.

## Chưa làm

- Booking, concurrency booking và payment.
- Adapter lấy busy interval từ active bookings.
- Cache availability.

## Bước tiếp theo

BF-043 — Stage 5 Regression, Browser E2E & Documentation sau khi hoàn tất browser smoke BF-042.
