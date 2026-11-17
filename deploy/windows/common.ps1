# 宏禹水利管理平台 —— Windows 部署脚本公共函数（由 install.ps1 / manage.ps1 引用）
# 兼容 Windows PowerShell 5.1

$ServiceId = 'HongyuWater'
$ServiceName = '宏禹水利管理平台'

function Write-Info($msg) { Write-Host "[信息] $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "[完成] $msg" -ForegroundColor Green }
function Write-Warn2($msg) { Write-Host "[注意] $msg" -ForegroundColor Yellow }
function Stop-WithError($msg) {
    Write-Host "[错误] $msg" -ForegroundColor Red
    exit 1
}

function Test-Admin {
    $p = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# 写 UTF-8（无 BOM）文本：Spring Boot / WinSW 读取配置时不受 BOM 干扰
function Write-Utf8File([string]$Path, [string]$Content) {
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [IO.File]::WriteAllText($Path, $Content, (New-Object Text.UTF8Encoding($false)))
}

# 运行外部程序并收集输出（PowerShell 5.1 下 stderr 不当作异常）
function Invoke-Native([string]$Exe, [string[]]$Arguments) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($out -join "`n") }
    } finally {
        $ErrorActionPreference = $old
    }
}

# 返回 java.exe 的主版本号，无法识别返回 0
function Get-JavaMajor([string]$JavaExe) {
    if (-not $JavaExe -or -not (Test-Path $JavaExe)) { return 0 }
    $r = Invoke-Native $JavaExe @('-version')
    if ($r.Output -match 'version "(\d+)(\.\d+)?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2].TrimStart('.') }
        return $major
    }
    return 0
}

# 依次查找可用的 Java 21+：指定目录 > 安装目录/发布包自带 jre > JAVA_HOME > PATH
function Find-Java([string[]]$Candidates) {
    $list = @()
    foreach ($c in $Candidates) { if ($c) { $list += (Join-Path $c 'bin\java.exe') } }
    if ($env:JAVA_HOME) { $list += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $list += $cmd.Source }
    foreach ($exe in $list) {
        if ((Get-JavaMajor $exe) -ge 21) { return (Resolve-Path $exe).Path }
    }
    return $null
}

# 查找 PostgreSQL 客户端目录（psql / pg_dump）：指定目录 > 本机 PostgreSQL 服务 > 默认安装目录 > PATH
function Find-PgBin([string]$Preferred) {
    if ($Preferred -and (Test-Path (Join-Path $Preferred 'psql.exe'))) { return $Preferred }
    $svc = Get-CimInstance Win32_Service -Filter "Name like 'postgresql%'" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($svc -and $svc.PathName -match '^"?(.+?\\bin)\\pg_ctl\.exe') {
        if (Test-Path (Join-Path $Matches[1] 'psql.exe')) { return $Matches[1] }
    }
    $dirs = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending
    if ($dirs) { return (Split-Path -Parent $dirs[0].FullName) }
    $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path -Parent $cmd.Source) }
    return $null
}

# YAML 单引号字符串
function ConvertTo-YamlString([string]$s) { return "'" + ($s -replace "'", "''") + "'" }

# 从 config\application.yml 读取数据库连接（安装脚本生成的格式）
function Read-DbConfig([string]$InstallDir) {
    $file = Join-Path $InstallDir 'config\application.yml'
    if (-not (Test-Path $file)) { Stop-WithError "未找到配置文件：$file" }
    $text = [IO.File]::ReadAllText($file)
    $get = {
        param($key)
        if ($text -match "(?m)^\s+${key}:\s*(.+?)\s*$") {
            $v = $Matches[1]
            if ($v -match "^'(.*)'$") { return ($Matches[1] -replace "''", "'") }
            if ($v -match '^"(.*)"$') { return $Matches[1] }
            return $v
        }
        return $null
    }
    $url = & $get 'url'
    if ($url -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') { Stop-WithError "无法解析数据库地址：$url" }
    $port = 5432
    if ($Matches[2]) { $port = [int]$Matches[2] }
    return [pscustomobject]@{
        Host     = $Matches[1]
        Port     = $port
        Name     = $Matches[3]
        User     = (& $get 'username')
        Password = (& $get 'password')
    }
}

function Read-DeployInfo([string]$InstallDir) {
    $file = Join-Path $InstallDir 'config\deploy.json'
    if (Test-Path $file) { return (Get-Content $file -Raw -Encoding UTF8 | ConvertFrom-Json) }
    return [pscustomobject]@{ Port = 8080; PgBin = $null; BackupKeepDays = 30 }
}

function Wait-Health([int]$Port, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health" -UseBasicParsing -TimeoutSec 5
            if ($r.Content -match 'UP') { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Get-LanAddress {
    $ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1
    if ($ip) { return $ip.IPAddress }
    return 'localhost'
}
