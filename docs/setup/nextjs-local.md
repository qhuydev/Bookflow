# Chạy Next.js local

## BF-005 làm gì?

BF-005 tạo bộ khung frontend tối thiểu tại `apps/web`. Next.js dùng React để xây dựng giao diện; App Router ánh xạ route từ `src/app`; TypeScript strict giúp phát hiện lỗi kiểu dữ liệu; ESLint kiểm tra chất lượng code; Vitest và React Testing Library kiểm tra component. Production build xác nhận project có thể được đóng gói.

Ticket này chỉ tạo nền kỹ thuật và trang chủ tĩnh. Chưa có authentication, booking, dashboard, API integration hoặc nghiệp vụ BookFlow.

## Cấu trúc project

- `package.json`: dependency, yêu cầu Node và các lệnh npm.
- `package-lock.json`: khóa chính xác cây dependency và phải được commit.
- `node_modules`: dependency local do npm cài, không commit.
- `src/app`: route và layout của App Router.
- `src/app/page.tsx`: trang của route `/`.
- `src/app/layout.tsx`: layout gốc và metadata dùng chung.
- `src/app/globals.css`: reset và biến màu toàn cục.
- `src/app/page.module.css`: style chỉ áp dụng cho trang chủ.
- `public`: tài nguyên tĩnh local.
- `.next`: build output, không commit.
- `next.config.ts`: cấu hình Next.js ổn định.
- `tsconfig.json`: cấu hình TypeScript strict và alias `@/*`.
- `eslint.config.mjs`: cấu hình ESLint cho Next.js và TypeScript.
- `vitest.config.ts`: cấu hình test trong môi trường `jsdom`.

Component trong App Router mặc định là Server Component. Chỉ dùng Client Component khi thật sự cần state, event handler hoặc browser API.

## Kiểm tra môi trường

```powershell
node --version
npm --version
```

Node phải là phiên bản `24.x` và npm phải hoạt động.

## Cài dependency

Từ repository root:

```powershell
npm --prefix .\apps\web ci
```

Hoặc từ `apps/web`:

```powershell
npm ci
```

`npm install` được dùng khi chủ động thêm hoặc cập nhật dependency. `npm ci` cài đúng theo `package-lock.json`, phù hợp cho CI hoặc máy phát triển mới. Không xóa lockfile như giải pháp đầu tiên khi gặp lỗi.

### Overrides tạm thời

`package.json` hiện ghim tạm thời `postcss` và `sharp` bằng `overrides` vì đây là các dependency bắc cầu của Next.js 16.2.12 từng có cảnh báo bảo mật production. Cần xem xét loại bỏ overrides khi một bản Next.js chính thức sử dụng các dependency đã vá, sau đó chạy lại `npm install`, `npm ls`, production audit và toàn bộ kiểm tra frontend.

## Chạy development

Từ repository root:

```powershell
npm --prefix .\apps\web run dev
```

Hoặc:

```powershell
Set-Location .\apps\web
npm run dev
```

Mở `http://127.0.0.1:3000` và dùng `Ctrl+C` để dừng.

## Kiểm tra code

```powershell
npm --prefix .\apps\web run lint
npm --prefix .\apps\web run typecheck
npm --prefix .\apps\web run test
npm --prefix .\apps\web run build
npm --prefix .\apps\web run verify
```

`verify` chạy tuần tự lint, typecheck, unit test và production build.

## Chạy production local

```powershell
npm --prefix .\apps\web run build
npm --prefix .\apps\web run start -- -H 127.0.0.1 -p 3000
```

Dùng `Ctrl+C` để dừng server.

## Biến môi trường

Nếu ticket tích hợp sau cần cấu hình backend local, tạo file riêng:

```powershell
Copy-Item .\apps\web\.env.example .\apps\web\.env.local
```

`NEXT_PUBLIC_BOOKFLOW_API_BASE_URL` trỏ tới Spring Boot local tại `http://127.0.0.1:8080`. Biến có prefix `NEXT_PUBLIC` được đưa vào client bundle, vì vậy tuyệt đối không lưu password, token, JWT secret hoặc API secret trong biến này. `.env.local` không được commit. BF-005 mới khai báo template, chưa dùng biến để gọi backend; API integration và CORS thuộc ticket sau.

## Quan hệ với backend và infrastructure

- Frontend mặc định chạy port `3000`.
- Backend Spring Boot chạy port `8080`.
- PostgreSQL và Redis tiếp tục được quản lý bằng Docker Compose.
- Frontend không kết nối trực tiếp PostgreSQL hoặc Redis.
- Frontend chỉ giao tiếp với backend qua HTTP API trong ticket tích hợp sau.
- BF-005 không yêu cầu bật Spring Boot và chưa cấu hình CORS.

## Cấu trúc feature tương lai

Khi có nghiệp vụ thật, mỗi feature có thể được tổ chức như sau; BF-005 chưa tạo các thư mục này:

```text
src/features/<feature-name>/
├── components/
├── api/
├── hooks/
├── schemas/
└── types/
```

Chỉ đưa component vào `src/components` khi nó thực sự được nhiều feature sử dụng.

## Troubleshooting

- Không tìm thấy `node`: cài hoặc chọn Node theo chính sách máy, rồi mở terminal mới; không sửa cấu hình hệ thống ngoài phạm vi repository.
- Node không phải 24: chọn Node 24 trước khi cài dependency hoặc chạy build.
- Không tìm thấy `npm`: xác nhận bản Node đã cài kèm npm và kiểm tra lại PATH trong terminal mới.
- PowerShell chặn `npm.ps1`: có thể gọi `npm.cmd` mà không thay đổi execution policy của hệ thống.
- Không truy cập được npm registry: kiểm tra mạng, proxy và registry tin cậy; không tắt SSL.
- `npm ci` báo lockfile không đồng bộ: xác định thay đổi trong `package.json`, rồi chủ động chạy `npm install` để cập nhật lockfile; không xóa lockfile trước tiên.
- Port 3000 đã bị chiếm: dùng port khác cho lần chạy, ví dụ `npm run dev -- -p 3001`; không kill toàn bộ tiến trình Node.
- ESLint thất bại: sửa lỗi được báo, không tắt hàng loạt rule.
- TypeScript thất bại: sửa kiểu dữ liệu hoặc import; không dùng `any`, `@ts-ignore` hay tắt strict để che lỗi.
- Unit test thất bại: đọc assertion và lỗi render đầu tiên; không skip hoặc xóa test.
- Build thất bại: kiểm tra Node, dependency, type error, import, asset ngoài mạng và stack trace đầu tiên.
- `Module not found`: xác nhận dependency đã được cài và đường dẫn/alias đúng chữ hoa chữ thường.
- Biến môi trường không được nhận: xác nhận tên file, prefix và khởi động lại dev server sau khi đổi `.env.local`.
- Không dùng `npm audit fix --force`; hãy đọc advisory và đánh giá tác động trước khi đổi dependency.
