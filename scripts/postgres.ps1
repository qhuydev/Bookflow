#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'status', 'ready', 'logs', 'psql', 'help')]
    [string]$Command = 'help'
)

$ErrorActionPreference = 'Stop'
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:ComposeFile = Join-Path $script:RepoRoot 'compose.yaml'

function Test-DockerPrerequisites {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error 'Khong tim thay Docker CLI.'
        exit 1
    }
    & docker version --format '{{.Server.Version}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Docker daemon khong san sang hoac khong the truy cap.'
        exit 1
    }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Khong tim thay Docker Compose.'
        exit 1
    }
}

function Show-Help {
    @'
Su dung: .\scripts\postgres.ps1 <command>

  up      Khoi dong PostgreSQL o detached mode.
  down    Dung va xoa container/network; giu named volume.
  status  Hien thi trang thai service postgres.
  ready   Kiem tra pg_isready trong container.
  logs    Hien thi log cua postgres (khong follow vo han).
  psql    Mo psql ben trong container.
  help    Hien thi huong dan nay.
'@ | Write-Host
}

if ($Command -eq 'help') { Show-Help; exit 0 }
if (-not (Test-Path $script:ComposeFile)) { Write-Error "Khong tim thay $script:ComposeFile"; exit 1 }
Test-DockerPrerequisites

switch ($Command) {
    'up'     { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile up -d }
    'down'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile down }
    'status' { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile ps postgres }
    'ready'  { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' }
    'logs'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile logs postgres }
    'psql'   { & docker compose --project-directory $script:RepoRoot -f $script:ComposeFile exec postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' }
}

exit $LASTEXITCODE
