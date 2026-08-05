# Architecture Decision Records (ADR)

ADR là tài liệu ngắn ghi lại một quyết định kiến trúc quan trọng, bối cảnh đưa ra quyết định và hệ quả của nó. ADR giúp đội ngũ hiểu vì sao lựa chọn được thực hiện khi dự án thay đổi theo thời gian.

## Quy ước

- Tên file: `NNNN-short-title.md`, ví dụ `0001-use-modular-monolith.md`.
- Trạng thái hợp lệ: `Proposed`, `Accepted`, `Superseded`, `Rejected`.
- Mỗi ADR gồm các phần: **Context**, **Decision**, **Alternatives**, **Consequences**.

## Danh sách ADR

| ADR | Trạng thái | Ngày | Quyết định |
|---|---|---|---|
| [0001 — Authentication và refresh token](0001-authentication-and-refresh-token.md) | Accepted | 2026-08-03 | Access JWT, opaque refresh token, session lifecycle và browser security |
