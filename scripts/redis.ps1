#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'status', 'ready', 'logs', 'cli', 'info', 'help')]
    [string]$Command = 'help'
)

$ErrorActionPreference = 'Stop'
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:ComposeFile = Join-Path $script:RepoRoot 'compose.yaml'

function Test-DockerPrerequisites {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Error 'Khong tim thay Docker CLI.'; exit 1 }
    & docker version --format '{{.Server.Version}}' *> $null
    if ($LASTEXITCODE -ne 0) { Write-Error 'Docker daemon khong san sang hoac khong the truy cap.'; exit 1 }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { Write-Error 'Khong tim thay Docker Compose.'; exit 1 }
}

function Show-Help {
    @'
Su dung: .\scripts\redis.ps1 <command>

  up      Khoi dong rieng Redis o detached mode.
  down    Dung va xoa rieng container Redis; giu named volume va PostgreSQL.
  status  Hien thi trang thai Redis.
  ready   Kiem tra PING da xac thuc.
  logs    Hien thi log Redis (khong follow vo han).
  cli     Mo Redis CLI da xac thuc trong container.
  info    Hien thi version, uptime, persistence va memory an toan.
  help    Hien thi huong dan nay.
'@ | Write-Host
}

if ($Command -eq 'help') { Show-Help; exit 0 }
if (-not (Test-Path $script:ComposeFile)) { Write-Error "Khong tim thay $script:ComposeFile"; exit 1 }
Test-DockerPrerequisites

switch ($Command) {
    'up'     { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile up -d redis }
    'down'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile stop redis; if ($LASTEXITCODE -eq 0) { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile rm -f redis } }
    'status' { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile ps redis }
    'ready'  { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec -T redis redis-cli ping }
    'logs'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile logs redis }
    'cli'    { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec redis redis-cli }
    'info'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec -T redis redis-cli info server; if ($LASTEXITCODE -eq 0) { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec -T redis redis-cli info persistence; & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec -T redis redis-cli info memory } }
}

exit $LASTEXITCODE
