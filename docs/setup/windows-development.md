# Phát triển BookFlow trên Windows

## Kiểm tra công cụ

Mở PowerShell trong `D:\\BookFlow` và chạy:

```powershell
git --version
java -version
javac -version
node --version
npm --version
docker --version
docker compose version
```

BookFlow dự kiến dùng Java/JDK 21, vì vậy cả `java` và `javac` phải báo phiên bản 21. Docker Desktop cần đang chạy để các lệnh Docker/Compose hoạt động.

## Chạy script kiểm tra

Trong PowerShell hoặc Windows PowerShell:

```powershell
./scripts/check-prerequisites.ps1
```

Nếu execution policy chặn script cục bộ, chỉ dùng cách chạy theo phiên hiện tại khi bạn hiểu chính sách máy của mình:

```powershell
powershell -ExecutionPolicy Bypass -File .\\scripts\\check-prerequisites.ps1
```

Với Git Bash hoặc WSL, Bash không bắt buộc nhưng có thể chạy script tương đương:

```bash
bash scripts/check-prerequisites.sh
```

## Mở trong VS Code

```powershell
code D:\\BookFlow
```

Nếu lệnh `code` chưa có, mở VS Code rồi chọn **File > Open Folder** và chọn `D:\\BookFlow`; VS Code CLI chỉ là tùy chọn.

## Line ending

Repository quy định line ending qua `.gitattributes`. Trên Windows, Git có thể checkout CRLF cho file phù hợp; shell script được giữ LF để tương thích Bash. Không chỉnh sửa hàng loạt line ending nếu ticket không yêu cầu.
