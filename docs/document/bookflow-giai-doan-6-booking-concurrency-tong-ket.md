# Giai đoạn 6 — Booking và concurrency

## Trạng thái

BF-044 — Booking Domain + Schema + State Machine: **Completed**.

BF-045 — Create Booking + PostgreSQL Concurrency Guard + Idempotency: **Completed**.

Expiry worker, cancel/reschedule, auto employee và frontend Booking UI chưa được triển khai.

## Booking aggregate

Aggregate `Booking` giữ UUID booking, tenant, branch, employee tùy chọn, customer/guest snapshot, khoảng chiếm dụng thực, trạng thái, tổng tiền, currency, thời hạn hold, booking items, status history và audit timestamps.

Factory `Booking.create(...)` là nơi duy nhất xác định trạng thái ban đầu và các invariant cơ bản. BF-044 khởi tạo `PENDING_CONFIRMATION` vì chưa có payment flow; hold duration bắt buộc và được tính bằng `Clock` injectable. Registered customer dùng `userId`; guest phải có email hoặc phone và luôn có tên snapshot.

## Schema V11

### `bookings`

- Dùng `tenant_id = businesses.id`; không lưu thêm `business_id` trùng nghĩa.
- `branch_id` bắt buộc, `employee_id` tùy chọn để chuẩn bị auto-assignment sau này.
- Customer có `customer_user_id` tùy chọn cùng name/email/phone snapshot.
- `start_at`, `end_at`, `expires_at`, `created_at`, `updated_at` dùng `TIMESTAMPTZ`.
- Currency dùng mã ba chữ cái và tiền dùng `NUMERIC(19,2)`.
- Check constraints bảo vệ time range, customer reference, status, amount, currency và hold expiry.

### `booking_items`

Một booking hỗ trợ nhiều service theo `position`. Mỗi item giữ:

- source `service_id` để trace;
- tên, giá, currency và duration snapshot;
- buffer before/after snapshot;
- tenant, booking và created timestamp.

Booking history không đọc lại tên, giá hay duration hiện tại từ bảng `services`. Test đã đổi Service sau khi booking được tạo và xác nhận snapshot cũ không đổi.

### `booking_status_history`

Mỗi record chứa `from_status`, `to_status`, actor tùy chọn, reason tùy chọn và `changed_at`. Creation ghi `null → PENDING_CONFIRMATION`. Status update và history insert nằm trong cùng transaction.

## BookingStatus và slot policy

Các trạng thái typed:

- `PENDING_PAYMENT`
- `PENDING_CONFIRMATION`
- `CONFIRMED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED_BY_CUSTOMER`
- `CANCELLED_BY_BUSINESS`
- `NO_SHOW`
- `EXPIRED`

Chỉ `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `CONFIRMED`, `IN_PROGRESS` chiếm slot. Java lấy policy từ `BookingStatus.occupiesSlot()`; migration V12 bắt buộc phải liệt kê literal trong partial constraint và integration test kiểm tra danh sách này khớp enum.

## State machine

Transition matrix hiện tại:

```text
PENDING_PAYMENT
  → PENDING_CONFIRMATION | CANCELLED_BY_CUSTOMER | CANCELLED_BY_BUSINESS | EXPIRED

PENDING_CONFIRMATION
  → CONFIRMED | CANCELLED_BY_CUSTOMER | CANCELLED_BY_BUSINESS | EXPIRED

CONFIRMED
  → IN_PROGRESS | CANCELLED_BY_CUSTOMER | CANCELLED_BY_BUSINESS | NO_SHOW

IN_PROGRESS
  → COMPLETED
```

Các trạng thái terminal không được quay lại active. `BookingStateMachine` là boundary tập trung; controller tương lai không được tự gán status.

## Instant/TIMESTAMPTZ

Schedule BF-039 tiếp tục dùng `LocalDate` và `LocalTime`. Booking là một occurrence thực tế nên domain dùng `Instant`, PostgreSQL dùng `TIMESTAMPTZ`. Integration test xác nhận round-trip giữ đúng instant.

## Tenant integrity

- `bookings` dùng composite FK `(tenant_id, branch_id)` và `(tenant_id, employee_id)`.
- `booking_items` dùng composite FK tới booking và service cùng tenant.
- `booking_status_history` dùng composite FK tới booking cùng tenant.
- Repository lookup luôn dùng `(tenant_id, booking_id)`.

Testcontainers đã chứng minh cross-tenant branch, employee và service item bị PostgreSQL từ chối. Không dựa vào Redis để bảo vệ consistency.

## Repository và transaction boundary

`BookingRepository`/`JdbcBookingRepository` cung cấp insert booking, items, history, tenant-scoped load và conditional status update. Load aggregate dùng ba bounded queries: booking, toàn bộ items và toàn bộ history; không query theo từng item/history.

`BookingPersistenceService.create()` ghi booking, toàn bộ items và initial history trong một transaction. `transition()` load tenant-scoped aggregate, kiểm tra state machine, update với expected status rồi ghi history trong cùng transaction. Test cố ý làm history FK thất bại sau status update và xác nhận cả status update được rollback.

## Indexes

- tenant + branch + start;
- tenant + employee + start/end + status;
- tenant + status + start;
- tenant + customer + created;
- tenant + booking + item position;
- tenant + service + booking;
- tenant + booking + history changed time.

Cấu trúc employee/start/end/status của V11 được V12 dùng trực tiếp cho overlap guard.

## Create Booking API

Endpoint công khai:

```http
POST /api/v1/public/businesses/{slug}/bookings
Idempotency-Key: booking-client-generated-key
X-XSRF-TOKEN: <token từ GET /api/v1/auth/csrf>
```

Request chỉ nhận `branchId`, `serviceId`, `employeeId`, `start` và snapshot liên hệ khách (`name`, `email`, `phone`). Client không thể đặt `endAt`, giá, currency, duration, buffer, status hay expiry; field không được phép bị trả `400`.

Luồng application:

1. Normalize/validate slug, idempotency key và customer.
2. Một query tenant-scoped tải business/branch/service/employee `ACTIVE` cùng toàn bộ assignment cần thiết.
3. Claim idempotency key trong transaction.
4. Gọi lại `AvailabilityQueryService` cho đúng employee/start để áp dụng working rule, break, exception, lead time, horizon, timezone và active booking.
5. Tải giá, duration, buffer từ Service source of truth rồi tạo immutable item snapshot.
6. Ghi booking, item, initial history và hoàn tất idempotency record trong cùng transaction.
7. PostgreSQL exclusion constraint quyết định cuối cùng khi hai transaction cùng vượt qua bước re-check.

Lần tạo đầu trả `201`; retry cùng key/payload trả `200` với cùng booking. Resource sai tenant/inactive/assignment sai được ẩn bằng `404`. Slot không còn hợp lệ trả `409 SLOT_UNAVAILABLE`.

## Occupied range và buffer

Từ BF-045, `bookings.start_at/end_at` có nghĩa là khoảng employee bị chiếm, không phải khoảng hiển thị cho khách:

```text
occupiedStart = requestedStart - bufferBefore
visibleEnd    = requestedStart + duration
occupiedEnd   = visibleEnd + bufferAfter
```

PostgreSQL và `BookingBusyIntervalProvider` cùng dùng occupied range này. Response public dựng `start/end` khách nhìn thấy từ occupied start và item snapshot. Vì vậy service `09:00–10:00` có buffer-after 15 phút giữ employee đến `10:15`; booking khác lúc `10:00` bị từ chối cả ở availability lẫn database.

## PostgreSQL exclusion constraint

V12 bật `btree_gist` và tạo `no_overlapping_active_employee_bookings` theo tenant + employee + `tstzrange(start_at, end_at, '[)')`. Partial predicate chỉ bao phủ bốn trạng thái chiếm slot.

Half-open interval cho phép `09:00–10:00` chạm `10:00–11:00`, nhưng từ chối mọi overlap thật. Availability re-check chỉ là validation nghiệp vụ; constraint GiST là lớp bảo vệ TOCTOU cuối cùng. Chỉ SQLSTATE `23P01` từ đúng constraint này được chuyển thành `SLOT_UNAVAILABLE`; lỗi integrity khác vẫn đi theo error path gốc và không lộ SQL/constraint ra response.

## Durable idempotency

`booking_idempotency_keys` có unique `(tenant_id, idempotency_key)`, fingerprint 64 ký tự, booking reference và completion timestamp. Fingerprint SHA-256 được tạo từ các field semantic sau normalize, dùng length-prefix nên không phụ thuộc thứ tự JSON hay field ngẫu nhiên.

`INSERT ... ON CONFLICT DO NOTHING` vừa claim key vừa làm request đồng thời chờ transaction sở hữu key. Sau commit, request cùng fingerprint lock/read record và trả booking cũ; fingerprint khác trả `409 IDEMPOTENCY_KEY_REUSED`. Idempotency row, booking, item và history nằm trong một transaction: lỗi resource/slot/constraint/item/history làm rollback sạch, không có key hoặc booking mồ côi.

## Booking-backed availability

`BookingBusyIntervalProvider` thay adapter rỗng ở runtime. Adapter thực hiện một bounded query cho toàn bộ employee được hỏi trong branch/day, lấy status từ `BookingStatus.occupiesSlot()`, rồi group interval trong memory. Không query per slot hoặc per employee; Pure `AvailabilityEngine` vẫn không biết JDBC/Booking.

Sau khi booking active được tạo, Public Availability loại employee đó khỏi `employeeIds` tại slot tương ứng. Employee khác vẫn còn nếu rảnh; khi mọi employee đều bận, slot biến mất. Terminal booking không còn chiếm slot.

## Security và expiry

Create Booking là public guest flow nên không cần Bearer JWT, nhưng vẫn là browser mutation và bắt buộc CSRF cookie + `X-XSRF-TOKEN`; không có CSRF ignore mới. Hold mặc định 10 phút qua `BOOKFLOW_BOOKING_HOLD_MINUTES`, dùng injectable `Clock`, initial state vẫn là `PENDING_CONFIRMATION`.

BF-045 chưa có expiry worker. Booking pending chỉ nhả slot sau khi ticket BF-046 chuyển trạng thái sang `EXPIRED` (hoặc terminal state khác); thời điểm `expires_at` tự nó chưa thay đổi status.

## Query và concurrency verification BF-045

- Create resource validation: một join query tenant-scoped.
- Availability: giữ bounded query strategy BF-041 cộng một booking busy query cho toàn bộ employee/ngày.
- Idempotency: unique index lookup và row lock theo tenant/key.
- Overlap: GiST exclusion index do PostgreSQL quản lý.
- PostgreSQL 17 Testcontainers: 20 transaction cùng employee/slot với key khác cho đúng 1 success và 19 `SLOT_UNAVAILABLE`; database còn đúng một booking/item/history/idempotency row.
- 20 retry đồng thời cùng key/payload đều nhận cùng booking, chỉ một booking row và 19 replay.
- Boundary `[start,end)` và employee khác đều tạo được; buffer kéo dài occupied range bị bảo vệ.
- Migration database sạch, direct constraint, snapshot, rollback và Public Availability regression đều được kiểm tra trong full `clean verify`.

## Kiểm thử BF-044

- Unit: aggregate hợp lệ, invalid time, snapshot invariant, initial status/history, transition hợp lệ/không hợp lệ, terminal state và occupies-slot policy.
- PostgreSQL Testcontainers: persistence/load, multiple item snapshots, mutation độc lập với Service, status/history, transaction rollback, composite tenant FK, Instant round-trip, indexes và migration V11.
- Flyway: database sạch migrate đến V11, validate thành công và lần migrate tiếp theo không có migration pending.
- Full `mvn test` và `mvn clean verify` là cổng hoàn thành BF-044.

## Giới hạn có chủ đích và ticket tiếp theo

BF-046 được hoãn rõ ràng: expiry worker, cancel và atomic reschedule. BF-047 được hoãn: auto employee selection và frontend Booking UI. Payment, Notification và availability cache thuộc các ticket sau; BF-045 không thêm Redis lock/cache và không triển khai các phần này.
