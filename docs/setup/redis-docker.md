# Redis local voi Docker Compose

## Redis dung de lam gi trong BookFlow?

Redis du kien ho tro cache du lieu doc nhieu, rate limiting, idempotency key, distributed lock co TTL, du lieu tam thoi va xu ly xung dot booking o cac ticket sau. BF-003 chua tich hop bat ky chuc nang nao vao ung dung: PostgreSQL van la nguon du lieu chinh, Redis khong thay the PostgreSQL va cache khong duoc la nguon du lieu nghiep vu duy nhat.

## Cau hinh local

Service `redis` dung `redis:8.6.5-alpine`, bat authentication, AOF va `appendfsync everysec`. Host ket noi qua `localhost` va `REDIS_PORT`; trong Docker network, hostname noi bo la `redis:6379`. Redis mount named volume `redis-data` tai `/data` va dung chung `bookflow-network` voi PostgreSQL.

```text
Host: localhost
Port: gia tri REDIS_PORT trong .env
Password: gia tri REDIS_PASSWORD trong .env
Database index: 0
```

`.env.example` chi la template; tao/sua `.env` local va khong commit password. Khong dung password mau cho staging hoac production.

## Su dung

```powershell
.\scripts\redis.ps1 up
.\scripts\redis.ps1 status
.\scripts\redis.ps1 ready
.\scripts\redis.ps1 cli
.\scripts\redis.ps1 info
.\scripts\redis.ps1 logs
.\scripts\redis.ps1 down
```

Trong CLI, cac lenh co ban la `PING`, `SET demo:key "hello" EX 60`, `GET demo:key`, `TTL demo:key`, `DEL demo:key`. `SET` luu key, `GET` doc key, `EX 60` dat han 60 giay, `TTL` xem thoi gian con lai va `DEL` xoa key. Khong dung `FLUSHALL` hoac `FLUSHDB`.

## Persistence

Redis chu yeu luu trong memory. AOF ghi cac thay doi vao volume `/data`; voi `appendfsync everysec`, su co nghiem trong co the mat toi da khoang mot giay du lieu. Volume giu du lieu khi container Redis duoc tao lai, nhung khong bien Redis thanh nguon nghiep vu thay PostgreSQL. Xoa volume se xoa du lieu Redis.

## Troubleshooting

- Docker Desktop/daemon: kiem tra `docker version`, `docker info` va Linux containers.
- Port `6379` bi chiem: khong kill tien trinh khac; doi `REDIS_PORT=6380` trong `.env`, roi chay lai `up`.
- `unhealthy`, `NOAUTH` hoac sai password: xem `scripts/redis.ps1 logs`; neu doi password `.env`, recreate rieng Redis, khong xoa volume.
- Volume Redis cu: khong xoa volume nhu cach xu ly dau tien; thu thap trang thai/log an toan va xin quyet dinh.
- Canh bao `.docker\config.json` hoac Bash/WSL `E_ACCESSDENIED`: khong sua quyen/file ngoai repository; dung PowerShell neu Bash bi chan.
- Redis chay nhung PostgreSQL dung: dung script service-scoped de khoi dong PostgreSQL rieng. `localhost:6379` la tu may host, con `redis:6379` la tu container cung network.
