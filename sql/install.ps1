# 一键执行：建库 + V1 + V2 + V3 + 种子
# 用法：
#   cd aiguanli\sql
#   .\install.ps1
# 若密码不是 123456，先：
#   $env:MYSQL_PWD = "你的密码"

$ErrorActionPreference = "Stop"
$mysql = "D:\DataBase\mysql-8.0.42-winx64\bin\mysql.exe"
if (-not (Test-Path $mysql)) {
    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysqlCmd) { throw "找不到 mysql.exe，请把 MySQL bin 加入 PATH" }
    $mysql = $mysqlCmd.Source
}

$user = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }
$pwd  = $env:MYSQL_PWD
$sqlDir = $PSScriptRoot

function Invoke-MysqlFile([string]$file, [string]$password) {
    Write-Host ">> $(Split-Path $file -Leaf)"
    $unix = ($file -replace '\\', '/')
    $args = @("-u$user", "--default-character-set=utf8mb4")
    if ($null -ne $password -and $password -ne "") {
        $args += "-p$password"
    }
    $args += @("-e", "source $unix")
    & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw "执行失败: $file" }
}

$candidates = @()
if ($null -ne $pwd) { $candidates += $pwd }
$candidates += @("123456", "root", "")

$ok = $false
$used = $null
foreach ($tryPwd in $candidates) {
    try {
        $args = @("-u$user", "--default-character-set=utf8mb4", "-e", "SELECT 1;")
        if ($tryPwd -ne "") { $args += "-p$tryPwd" }
        & $mysql @args | Out-Null
        if ($LASTEXITCODE -eq 0) { $ok = $true; $used = $tryPwd; break }
    } catch { }
}
if (-not $ok) { throw "MySQL 登录失败。请设置 `$env:MYSQL_PWD 后重试。" }

$files = @(
    "00_create_database.sql",
    "V1__sprint1_core.sql",
    "V2__sprint2_integration.sql",
    "V3__sprint3_ai.sql",
    "seed_sprint1.sql"
)
foreach ($f in $files) {
    Invoke-MysqlFile (Join-Path $sqlDir $f) $used
}
Write-Host "完成。库名 aiguanli。演示账号 admin/admin123  pm/pm123  member/member123"
Write-Host "22组账号 likx / zhangyl / lisb / qubq  密码 22team"
