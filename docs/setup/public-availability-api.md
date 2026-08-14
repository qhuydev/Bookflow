# Public Availability API

BF-041 cung cấp API chỉ đọc để khách chưa đăng nhập tìm các slot có thể đặt. API nối dữ liệu lịch BF-039 với Pure Availability Engine BF-040; chưa tạo booking và chưa trừ các booking đang tồn tại.

## Endpoint

```http
GET /api/v1/public/businesses/{slug}/availability?branchId={uuid}&serviceId={uuid}&date={yyyy-MM-dd}&employeeId={uuid}
```

- `slug`, `branchId`, `serviceId`, `date`: bắt buộc.
- `employeeId`: tùy chọn. Nếu có, kết quả chỉ thuộc nhân viên đó. Nếu bỏ qua, hệ thống tính cho mọi nhân viên active đủ assignment và gộp các slot giống nhau.
- Endpoint là public GET, không yêu cầu JWT hoặc CSRF.

Ví dụ response:

```json
{
  "date": "2026-08-20",
  "timeZone": "Asia/Ho_Chi_Minh",
  "branchId": "11111111-1111-1111-1111-111111111111",
  "serviceId": "22222222-2222-2222-2222-222222222222",
  "slots": [
    {
      "start": "2026-08-20T09:00:00+07:00",
      "end": "2026-08-20T10:00:00+07:00",
      "employeeIds": ["33333333-3333-3333-3333-333333333333"]
    }
  ]
}
```

Slot được sắp xếp theo `start`; `employeeIds` được sắp xếp ổn định. Response không công khai `tenantId`, membership, user, schedule rule, break, exception hoặc thông tin riêng của nhân viên.

## Quy tắc resource và lỗi

- Business, Branch, Service và Employee phải `ACTIVE`.
- Service phải được gán vào Branch.
- Employee được chọn phải được gán cả Branch và Service trong cùng business.
- UUID thuộc tenant khác, resource inactive hoặc relationship sai đều trả `404` để không lộ dữ liệu.
- Query thiếu/sai định dạng trả Problem Detail `400`.
- Resource hợp lệ nhưng không có lịch/slot trả `200` với `slots: []`, không phải `404` hay `409`.

## Chính sách thời gian

- Timezone lấy từ Branch; response dùng ISO-8601 có offset và luôn kèm tên timezone.
- Duration và buffer lấy từ Service rồi được BF-040 áp dụng vào occupied interval.
- Slot step lấy từ `BOOKFLOW_AVAILABILITY_SLOT_STEP_MINUTES`, mặc định 15 phút.
- Lead time lấy từ `BOOKFLOW_AVAILABILITY_DEFAULT_LEAD_TIME_MINUTES`, mặc định 0. Đây là policy cấp ứng dụng tạm thời vì business schema chưa có minimum lead time.
- Booking horizon lấy từ `Business.maxBookingAdvanceDays` và có tính inclusive.

## Query và giới hạn hiện tại

Repository batch-load danh sách nhân viên, working rules, breaks và exceptions. Không có query theo slot hoặc theo từng employee. `BusyIntervalProvider` là điểm nối dành cho Booking phase; adapter hiện tại trả danh sách rỗng vì booking domain chưa tồn tại. Do đó API chưa trừ active booking, chưa cache và chưa cung cấp giao diện frontend.
