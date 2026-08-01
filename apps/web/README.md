# BookFlow Web

Frontend nền tảng của BookFlow dùng Next.js 16.2.12, React, App Router, TypeScript strict, ESLint, CSS Modules và Vitest. Yêu cầu Node.js 24 và npm.

Từ repository root:

```powershell
npm --prefix .\apps\web ci
npm --prefix .\apps\web run dev
npm --prefix .\apps\web run verify
npm --prefix .\apps\web run build
```

Ứng dụng local mặc định tại `http://127.0.0.1:3000`.

Xem hướng dẫn đầy đủ tại [Chạy Next.js local](../../docs/setup/nextjs-local.md).

BF-005 chưa có API integration, authentication hoặc booking UI.
