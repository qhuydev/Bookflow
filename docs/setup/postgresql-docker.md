# PostgreSQL local bang Docker Compose

## Muc dich

BF-002 cung cap mot PostgreSQL 17 rieng cho local development. Chua co Spring Boot, schema nghiep vu hay Flyway trong ticket nay. Docker Compose giup moi thanh vien dung cung image va cau hinh lap lai duoc ma khong cai PostgreSQL truc tiep len Windows.

## Thanh phan cau hinh

- Service `postgres` dung image `postgres:17-alpine` (co the override bang `POSTGRES_IMAGE`).
- Port chi bind `127.0.0.1`, nen khong cong khai tren cac interface mang khac.
- Health check goi `pg_isready` voi database va user ben trong container.
- Named volume `postgres-data` mount tai `/var/lib/postgresql/data` de giu du lieu khi container bi tao lai.
- Bridge network `bookflow-network` la network rieng cua stack local.

## `.env.example` va `.env`

`.env.example` la template an toan duoc commit. Tao file `.env` local tu template va thay password bang gia tri manh; `.env` bi Git ignore va khong duoc chia se. Password mau tuyet doi khong dung cho staging hay production.

PowerShell:

```powershell
Copy-Item .env.example .env
# Sua POSTGRES_PASSWORD trong .env bang mot gia tri local manh.
```

Git Bash/WSL:

```bash
cp .env.example .env
# Sua POSTGRES_PASSWORD trong .env bang mot gia tri local manh.
```

## Su dung

Tu root repository:

```powershell
.\scripts\postgres.ps1 up
.\scripts\postgres.ps1 status
.\scripts\postgres.ps1 ready
.\scripts\postgres.ps1 psql
.\scripts\postgres.ps1 logs
.\scripts\postgres.ps1 down
```

Hoac voi Bash:

```bash
bash scripts/postgres.sh up
bash scripts/postgres.sh status
bash scripts/postgres.sh ready
bash scripts/postgres.sh psql
bash scripts/postgres.sh logs
bash scripts/postgres.sh down
```

`down` chi dung va xoa container/network; named volume duoc giu lai. `docker compose down -v` xoa volume va du lieu, vi vay chi duoc nhac den nhu canh bao va khong dung cho van hanh thuong ngay.

## Ket noi bang DBeaver hoac pgAdmin

```text
Host: localhost
Port: gia tri POSTGRES_PORT
Database: gia tri POSTGRES_DB
Username: gia tri POSTGRES_USER
Password: lay tu file .env local
```

## Troubleshooting

- Docker Desktop chua chay: mo Docker Desktop, doi daemon san sang, sau do chay `docker version` va `docker info`.
- Khong truy cap duoc Docker daemon: kiem tra Docker Desktop dang dung **Linux containers** va quyen truy cap cua tai khoan hien tai.
- Port `5432` dang duoc dung: khong dung/kill tien trinh khac; doi `POSTGRES_PORT=5433` trong `.env`, roi chay lai `up`.
- Container `unhealthy`: chay `docker compose logs postgres` hoac script `logs` de xem log; kiem tra bien database/user trong `.env`.
- Loi authentication: kiem tra cac ten bien trong `.env` ma khong dua password vao log. Doi username/password trong `.env` khong tu cap nhat database da ton tai trong volume.
- Canh bao `C:\Users\DMX\.docker\config.json` access denied: khong sua file hay quyen Windows trong ticket nay. Neu canh bao lam `pull`/`up` that bai, day la blocker can xu ly ben ngoai repository.
- Bash/WSL `E_ACCESSDENIED`: day la gioi han moi truong; dung PowerShell de kiem tra, khong sua script de che loi.
- Khong xoa volume nhu cach xu ly dau tien. Neu volume cu co credential cu, thu thap trang thai/log an toan va xin quyet dinh truoc bat ky thao tac xoa du lieu nao.
