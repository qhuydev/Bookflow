#requires -Version 5.1
$ErrorActionPreference = 'Continue'
$requiredFailure = $false

function Write-Result {
    param([string]$Status, [string]$Name, [string]$Detail)
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Name, $Detail)
}

function Test-Tool {
    param([string]$Name, [string]$Command, [string[]]$ToolArguments)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        Write-Result 'FAIL' $Name 'Khong tim thay cong cu.'
        $script:requiredFailure = $true
        return ''
    }
    $output = (& $Command @ToolArguments 2>&1 | Out-String).Trim()
    $output = $output.Replace("`r", ' ').Replace("`n", ' ')
    Write-Result 'PASS' $Name $output
    return $output
}

$null = Test-Tool 'Git' 'git' @('--version')
$javaVersion = Test-Tool 'Java runtime' 'java' @('-version')
$javacVersion = Test-Tool 'Java compiler (javac)' 'javac' @('-version')

if (($javaVersion.Length -gt 0) -and ($javacVersion.Length -gt 0)) {
    $runtimeIs21 = $javaVersion -match '(^|[^0-9])21([^0-9]|$)'
    $compilerIs21 = $javacVersion -match '(^|[^0-9])21([^0-9]|$)'
    if ($runtimeIs21 -and $compilerIs21) {
        Write-Result 'PASS' 'Java 21' 'Java runtime va javac deu la phien ban 21.'
    } else {
        Write-Result 'FAIL' 'Java 21' 'Can ca Java runtime va javac phien ban 21.'
        $requiredFailure = $true
    }
}

$null = Test-Tool 'Node.js' 'node' @('--version')
$null = Test-Tool 'npm' 'npm' @('--version')
$null = Test-Tool 'Docker' 'docker' @('--version')
$null = Test-Tool 'Docker Compose' 'docker' @('compose', 'version')

if (Get-Command 'code' -ErrorAction SilentlyContinue) {
    $output = (& code --version 2>&1 | Out-String).Trim()
    $output = $output.Replace("`r", ' ').Replace("`n", ' ')
    Write-Result 'PASS' 'VS Code CLI' $output
} else {
    Write-Result 'WARN' 'VS Code CLI' 'Khong tim thay; day la cong cu tuy chon.'
}

if ($requiredFailure) { exit 1 }
exit 0
